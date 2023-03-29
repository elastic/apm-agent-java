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
package co.elastic.apm.agent.springwebclient;

import co.elastic.apm.agent.collections.WeakConcurrentProviderImpl;
import co.elastic.apm.agent.impl.context.web.ResultUtil;
import co.elastic.apm.agent.tracer.Span;
import co.elastic.apm.agent.sdk.weakconcurrent.WeakMap;
import co.elastic.apm.agent.tracer.Tracer;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.client.ClientResponse;
import reactor.core.CoreSubscriber;

import javax.annotation.Nullable;

public class WebClientSubscriber<T> implements CoreSubscriber<T>, Subscription {

    private static final Logger logger = LoggerFactory.getLogger(WebClientSubscriber.class);

    private static final WeakMap<WebClientSubscriber<?>, Span<?>> spanMap = WeakConcurrentProviderImpl.createWeakSpanMap();

    private final Tracer tracer;
    private final CoreSubscriber<? super T> subscriber;
    private Subscription subscription;

    public WebClientSubscriber(CoreSubscriber<? super T> subscriber, Span<?> span, Tracer tracer) {
        this.subscriber = subscriber;
        this.tracer = tracer;

        spanMap.put(this, span);
    }

    @Override
    public void onSubscribe(Subscription s) {
        this.subscription = s;
        Span<?> span = getSpan();

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
        final Span<?> span = getSpan();
        boolean hasActivated = doEnter("onNext", span);
        Throwable thrown = null;
        try {
            if (span != null && t instanceof ClientResponse) {
                ClientResponse clientResponse = (ClientResponse) t;
                int statusCode = clientResponse.rawStatusCode();
                span.withOutcome(ResultUtil.getOutcomeByHttpClientStatus(statusCode));
                span.getContext().getHttp().withStatusCode(statusCode);
            }
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
        Span<?> span = getSpan();
        boolean hasActivated = doEnter("onError", span);
        try {
            subscriber.onError(throwable);
        } finally {
            doExit(hasActivated, "onError", span);
            discardIf(true);
            endSpan(throwable, span);
        }
    }

    @Override
    public void onComplete() {
        final Span<?> span = getSpan();
        boolean hasActivated = doEnter("onComplete", span);
        try {
            subscriber.onComplete();
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
        cancelSpan();
    }

    @Nullable
    private Span<?> getSpan() {
        return spanMap.get(this);
    }


    private void discardIf(boolean condition) {
        if (!condition) {
            return;
        }
        spanMap.remove(this);
    }

    private boolean doEnter(String method, @Nullable Span<?> span) {
        debugTrace(true, method, span);

        if (span == null || tracer.getActive() == span) {
            // already activated or discarded
            return false;
        }

        span.activate();
        return true;
    }

    private void doExit(boolean deactivate, String method, @Nullable Span<?> span) {
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

    private void debugTrace(boolean isEnter, String method, @Nullable Span<?> span) {
        if (!logger.isTraceEnabled()) {
            return;
        }
        logger.trace("{} webclient {} {}", isEnter ? ">>" : "<<", method, span);
    }

    private void endSpan(@Nullable Throwable thrown, @Nullable Span<?> span) {
        if (span == null) {
            // already discarded
            return;
        }
        span.captureException(thrown).end();
    }

    private void cancelSpan() {
        Span<?> span = getSpan();
        debugTrace(true, "cancelSpan", span);
        try {
            if (span != null) {
                endSpan(null, span);
                spanMap.remove(this);
            }
        } finally {
            debugTrace(false, "cancelSpan", span);
        }
    }

}
