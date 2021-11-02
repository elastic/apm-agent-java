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
package co.elastic.apm.agent.webfluxclient;

import co.elastic.apm.agent.collections.WeakConcurrentProviderImpl;
import co.elastic.apm.agent.impl.Tracer;
import co.elastic.apm.agent.impl.transaction.Outcome;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.sdk.weakconcurrent.WeakMap;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.CoreSubscriber;

import javax.annotation.Nullable;

public class WebfluxClientSubscriber<T> implements CoreSubscriber<T>, Subscription {
    public static final Logger logger = LoggerFactory.getLogger(WebfluxClientSubscriber.class);

    private final Tracer tracer;
    private CoreSubscriber<? super T> subscriber;
    private static final WeakMap<WebfluxClientSubscriber<?>, Span> spanMap = WeakConcurrentProviderImpl.createWeakSpanMap();
    private Subscription subscription;

    public WebfluxClientSubscriber(CoreSubscriber<? super T> subscriber, Span span, Tracer tracer) {
        this.subscriber = subscriber;
        this.tracer = tracer;

        spanMap.put(this, span);
    }

    @Override
    public void onSubscribe(Subscription s) {
        this.subscription = s;
        Span span = getSpan();

        boolean hasActivated = doEnter("onSubscribe", span);
        Throwable thrown = null;
        try {
            subscriber.onSubscribe(this);
        } catch (Throwable e) {
            thrown = e;
            throw e;
        } finally {
            doExit(hasActivated, "onSubscribe", span);
            discardIf(thrown != null);
        }
    }

    @Override
    public void onNext(T t) {
        final Span span = getSpan();
        boolean hasActivated = doEnter("onNext", span);
        Throwable thrown = null;
        try {
            subscriber.onNext(t);
        } catch (Throwable e) {
            thrown = e;
            throw e;
        } finally {
            doExit(hasActivated, "onNext", span);
            discardIf(thrown != null);
        }
    }

    @Override
    public void onError(Throwable throwable) {
        Span span = getSpan();
        boolean hasActivated = doEnter("onError", span);
        try {
            subscriber.onError(throwable);
            span = span.withOutcome(Outcome.FAILURE);
        } finally {
            doExit(hasActivated, "onError", span);
            discardIf(true);
            endSpan(throwable, span);
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

    @Override
    public void request(long n) {
        subscription.request(n);
    }

    @Override
    public void cancel() {
        subscription.cancel();
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
        if (!logger.isTraceEnabled()) {
            return;
        }
        logger.trace("{} r2dbc {} {}", isEnter ? ">>" : "<<", method, span);
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
