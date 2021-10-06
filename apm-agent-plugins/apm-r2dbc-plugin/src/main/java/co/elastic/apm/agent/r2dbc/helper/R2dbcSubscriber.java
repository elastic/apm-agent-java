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
package co.elastic.apm.agent.r2dbc.helper;

import co.elastic.apm.agent.collections.WeakConcurrentSupplierImpl;
import co.elastic.apm.agent.impl.Tracer;
import co.elastic.apm.agent.impl.context.Destination;
import co.elastic.apm.agent.impl.transaction.Outcome;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.sdk.weakconcurrent.WeakMap;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.Result;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.CoreSubscriber;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.annotation.Nullable;

import static co.elastic.apm.agent.r2dbc.helper.R2dbcHelper.DB_SPAN_ACTION;
import static co.elastic.apm.agent.r2dbc.helper.R2dbcHelper.DB_SPAN_TYPE;

public class R2dbcSubscriber<T> implements CoreSubscriber<T>, Subscription {
    private static final Logger log = LoggerFactory.getLogger(R2dbcSubscriber.class);

    private final Tracer tracer;
    private final CoreSubscriber<? super T> subscriber;
    private static final WeakMap<R2dbcSubscriber<?>, Span> spanMap = WeakConcurrentSupplierImpl.createWeakSpanMap();
    private final Connection connection;
    private Subscription subscription;

    public R2dbcSubscriber(CoreSubscriber<? super T> subscriber,
                           Tracer tracer,
                           Span span,
                           final Connection connection) {
        this.subscriber = subscriber;
        this.tracer = tracer;
        this.connection = connection;

        spanMap.put(this, span);
    }

    @Override
    public void request(long n) {
        subscription.request(n);
    }

    @Override
    public void cancel() {
        subscription.cancel();
        cancelSpan();
    }

    @Override
    public void onSubscribe(Subscription subscription) {
        this.subscription = subscription;
        Span span = getSpan();
        boolean hasActivated = doEnter("onSubscribe", span);
        Throwable thrown = null;
        try {
            subscriber.onSubscribe(subscription);
        } catch (Throwable e) {
            thrown = e;
            throw e;
        } finally {
            doExit(hasActivated, "onSubscribe", span);
            discardIf(thrown != null);
        }
    }

    @Override
    public void onNext(T next) {
        final Span span = getSpan();
        boolean hasActivated = doEnter("onNext", span);
        Throwable thrown = null;
        try {
            subscriber.onNext(next);
            if (span.getSubtype() == null) {
                R2dbcHelper helper = R2dbcHelper.get();
                ConnectionMetaData connectionMetaData = helper.getConnectionMetaData(connection);
                log.debug("Parsed connection metadata = {}", connectionMetaData);
                String vendor = "unknown";
                if (connectionMetaData != null) {
                    vendor = connectionMetaData.getDbVendor();
                    span.getContext().getDb()
                        .withInstance(connectionMetaData.getInstance())
                        .withUser(connectionMetaData.getUser());
                    Destination destination = span.getContext().getDestination()
                        .withAddress(connectionMetaData.getHost())
                        .withPort(connectionMetaData.getPort());
                    destination.getService()
                        .withName(vendor)
                        .withResource(vendor)
                        .withType(DB_SPAN_TYPE);
                }
                span.withSubtype(vendor).withAction(DB_SPAN_ACTION);
            }
            if (next instanceof Result) {
                Result result = (Result) next;
                if (result.getRowsUpdated() instanceof Mono) {
                    ((Mono<Integer>) result.getRowsUpdated()).cache().subscribe();
                } else if (result.getRowsUpdated() instanceof Flux) {
                    ((Flux<Integer>) result.getRowsUpdated()).cache().subscribe();
                }
            }
        } catch (Throwable e) {
            thrown = e;
            throw e;
        } finally {
            doExit(hasActivated, "onNext", span);
            discardIf(thrown != null);
        }
    }

    @Override
    public void onError(Throwable t) {
        Span span = getSpan();
        boolean hasActivated = doEnter("onError", span);
        try {
            subscriber.onError(t);
            span = span.withOutcome(Outcome.FAILURE);
        } finally {
            doExit(hasActivated, "onError", span);
            discardIf(true);
            endSpan(t, span);
        }
    }

    @Override
    public void onComplete() {
        Span span = getSpan();
        boolean hasActivated = doEnter("onComplete", span);
        try {
            subscriber.onComplete();
            span = span.withOutcome(Outcome.SUCCESS);
        } finally {
            doExit(hasActivated, "onComplete", span);
            discardIf(true);
            endSpan(null, span);
        }
    }

    @Nullable
    private Span getSpan() {
        return spanMap.get(this);
    }

    private void discardIf(boolean condition) {
        if (!condition) {
            return;
        }
        spanMap.remove(this);
    }

    private boolean doEnter(String method, @Nullable Span span) {
        debugTrace(true, method, span);

        if (span == null || tracer.getActive() == span) {
            // already activated or discarded
            return false;
        }

        span.activate();
        return true;
    }

    private void doExit(boolean deactivate, String method, @Nullable Span span) {
        debugTrace(false, method, span);

        if (span == null || !deactivate) {
            return;
        }

        if (span != tracer.getActive()) {
            // don't attempt to deactivate if not the active one
            return;
        }
        // the current context has been activated on enter thus must be the active one
        span.deactivate();
    }

    private void debugTrace(boolean isEnter, String method, @Nullable Span span) {
        if (!log.isTraceEnabled()) {
            return;
        }
        log.trace("{} r2dbc {} {}", isEnter ? ">>" : "<<", method, span);
    }

    private void endSpan(@Nullable Throwable thrown, @Nullable Span span) {
        if (span == null) {
            // already discarded
            return;
        }
        span.captureException(thrown).end();
    }

    private void cancelSpan() {
        Span span = getSpan();
        debugTrace(true, "cancelSpan", span);
        try {
            if (span == null) {
                return;
            }
            endSpan(null, span);

            spanMap.remove(this);
        } finally {
            debugTrace(false, "cancelSpan", span);
        }
    }
}
