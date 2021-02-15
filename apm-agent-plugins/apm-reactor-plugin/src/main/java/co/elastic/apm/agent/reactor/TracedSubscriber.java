/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2020 Elastic and contributors
 * %%
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
 * #L%
 */
package co.elastic.apm.agent.reactor;

import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscription;
import reactor.core.CoreSubscriber;
import reactor.core.Fuseable;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Operators;

import javax.annotation.Nullable;
import java.util.function.Function;

public class TracedSubscriber<T,C extends AbstractSpan<?>> implements CoreSubscriber<T> {
    private static final String HOOK_KEY = "elastic-apm";

    private final CoreSubscriber<? super T> subscriber;
    private final C context;
    private final boolean terminateOnComplete;

    public TracedSubscriber(CoreSubscriber<? super T> subscriber,
                            C context,
                            boolean terminateOnComplete) {
        this.context = context;
        this.terminateOnComplete = terminateOnComplete;
        this.subscriber = subscriber;
    }

    @Override
    public void onSubscribe(Subscription s) {
        context.activate();
        try {
            subscriber.onSubscribe(s);
        } finally {
            context.deactivate();
        }
    }

    @Override
    public void onNext(T next) {
        context.activate();
        try {
            subscriber.onNext(next);
        } finally {
            context.deactivate();
        }
    }

    @Override
    public void onError(Throwable t) {
        context.activate();
        try {
            subscriber.onError(t);
        } finally {
            context.deactivate();
            beforeContextEnd(context, t);
            context.captureException(t).end();
        }
    }

    protected void beforeContextEnd(C context, @Nullable Throwable thrown) {
    }

    @Override
    public void onComplete() {
        context.activate();
        try {
            subscriber.onComplete();
        } finally {
            context.deactivate();
            if (terminateOnComplete) {
                beforeContextEnd(context, null);
                context.end();
            }
        }
    }


    public static <X> Function<? super Publisher<X>, ? extends Publisher<X>> wrapOperators(ElasticApmTracer tracer) {
        return Operators.liftPublisher((p, sub) -> {
            // don't wrap known #error #just #empty as they have instantaneous execution
            if (p instanceof Fuseable.ScalarCallable) {
                return sub;
            }

            AbstractSpan<?> active = tracer.getActive();

            if (active == null) {
                // no active context, we have nothing to wrap
                return sub;
            }

            return new TracedSubscriber<>(sub, active, false);
        });
    }

    public static void registerHooks(ElasticApmTracer tracer) {
        Hooks.onEachOperator(HOOK_KEY, wrapOperators(tracer));
    }

    public static void unregisterHooks() {
        Hooks.resetOnEachOperator(HOOK_KEY);
    }


}
