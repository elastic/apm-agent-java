/*
 * Licensed to Elasticsearch B.V. under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch B.V. licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package co.elastic.apm.agent.springwebflux;

import co.elastic.apm.agent.collections.WeakConcurrentProviderImpl;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.sdk.weakconcurrent.WeakMap;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.CoreSubscriber;
import reactor.util.context.Context;

import javax.annotation.Nullable;

/**
 * Transaction-aware subscriber that will (optionally) activate transaction and terminate it on error or completion.
 *
 * @param <T>
 */
class TransactionAwareSubscriber<T> implements CoreSubscriber<T>, Subscription {

    private static final Logger log = LoggerFactory.getLogger(TransactionAwareSubscriber.class);

    private static final WeakMap<TransactionAwareSubscriber<?>, Transaction> transactionMap = WeakConcurrentProviderImpl.createWeakSpanMap();

    private final CoreSubscriber<? super T> subscriber;

    private final ServerWebExchange exchange;

    private final String description;

    private final Context context;

    private Subscription subscription;

    /**
     * @param subscriber  subscriber to wrap
     * @param transaction transaction
     * @param exchange    server web exchange
     * @param description human-readable description to make debugging easier
     */
    TransactionAwareSubscriber(CoreSubscriber<? super T> subscriber,
                               Transaction transaction,
                               ServerWebExchange exchange,
                               String description) {

        this.subscriber = subscriber;
        this.exchange = exchange;
        this.description = description;

        transactionMap.put(this, transaction);

        // store transaction into subscriber context it can be looked-up by reactor when the transaction
        // is not already active in current thread.
        this.context = subscriber.currentContext().put(AbstractSpan.class, transaction);
    }

    @Override
    public Context currentContext() {
        return context;
    }

    @Override
    public void request(long n) {
        subscription.request(n);
    }

    @Override
    public void cancel() {
        subscription.cancel();
        cancelTransaction();
    }

    /**
     * Wraps {@link Subscriber#onSubscribe(Subscription)} for context propagation, executed in "subscribe scheduler".
     * Might activate transaction if not already active. When activating the transaction is kept active after method execution.
     * Refer to {@link #doEnter} for details on activation.
     */
    @Override
    public void onSubscribe(Subscription s) {
        this.subscription = s;
        Transaction transaction = getTransaction();
        doEnter("onSubscribe", transaction);
        Throwable thrown = null;
        try {
            subscriber.onSubscribe(this);
        } catch (Throwable e) {
            thrown = e;
            throw e;
        } finally {
            doExit(thrown != null, "onSubscribe", transaction);
        }
    }

    /**
     * Wraps {@link Subscriber#onNext(Object)} for context propagation, executed in "publisher scheduler".
     * Assumes the transaction is already active, will discard transaction reference if any exception is thrown.
     *
     * @param next next item
     */
    @Override
    public void onNext(T next) {
        Transaction transaction = getTransaction();
        doEnter("onNext", transaction);
        Throwable thrown = null;
        try {
            subscriber.onNext(next);
        } catch (Throwable e) {
            thrown = e;
            throw e;
        } finally {
            doExit(thrown != null, "onNext", transaction);
        }
    }

    /**
     * Wraps {@link Subscriber#onError(Throwable)} for context propagation, executed in "publisher scheduler".
     * Assumes the transaction is already active, will terminate transaction and optionally deactivate if it was
     * previously activated by {@link #onSubscribe(Subscription)}.
     *
     * @param t error
     */
    @Override
    public void onError(Throwable t) {
        Transaction transaction = getTransaction();
        doEnter("onError", transaction);
        try {
            subscriber.onError(t);
        } finally {
            WebfluxHelper.endTransaction(t, transaction, exchange);
            doExit(true, "onError", transaction);
        }
    }

    /**
     * Wraps {@link Subscriber#onComplete()} for context propagation, executed in "publisher scheduler".
     * Assumes the transaction is already active, will terminate transaction and optionally deactivate if it was
     * previously activated by {@link #onSubscribe(Subscription)}.
     */
    @Override
    public void onComplete() {
        Transaction transaction = getTransaction();
        doEnter("onComplete", transaction);
        try {
            subscriber.onComplete();
        } finally {
            WebfluxHelper.endTransaction(null, transaction, exchange);
            doExit(true, "onComplete", transaction);
        }
    }

    private void doEnter(String method, @Nullable Transaction transaction) {
        debugTrace(true, method, transaction);

        if (transaction == null) {
            return;
        }

        transaction.activate();
    }

    private void doExit(boolean discard, String method, @Nullable Transaction transaction) {
        debugTrace(false, method, transaction);

        if (transaction == null) {
            return;
        }

        transaction.deactivate();
        if (discard) {
            transactionMap.remove(this);
        }
    }

    private void cancelTransaction() {
        Transaction transaction = getTransaction();
        debugTrace(true, "cancelTransaction", transaction);
        try {
            if (transaction == null) {
                return;
            }

            WebfluxHelper.endTransaction(null, transaction, exchange);

            transactionMap.remove(this);
        } finally {
            debugTrace(false, "cancelTransaction", transaction);
        }
    }

    @Nullable
    private Transaction getTransaction() {
        return transactionMap.get(this);
    }

    private void debugTrace(boolean isEnter, String method, @Nullable Transaction transaction) {
        if (!log.isTraceEnabled()) {
            return;
        }
        log.trace("{} {} {} {}", isEnter ? ">>>>" : "<<<<", description, method, transaction);
    }
}
