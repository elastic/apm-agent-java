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

import co.elastic.apm.agent.impl.Tracer;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.sdk.state.CallDepth;
import co.elastic.apm.agent.sdk.state.GlobalVariables;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscription;
import reactor.core.CoreSubscriber;
import reactor.core.Fuseable;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Operators;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;
import java.util.function.Function;

public class TracedSubscriber<T,C extends AbstractSpan<?>> implements CoreSubscriber<T> {

    private static final AtomicBoolean isRegistered = GlobalVariables.get(ReactorInstrumentation.class, "reactor-hook-enabled", new AtomicBoolean(false));
    private static final CallDepth callDepth = CallDepth.get(TracedSubscriber.class);

    private static final String HOOK_KEY = "elastic-apm";

    private final CoreSubscriber<? super T> subscriber;
    private final C context;

    public TracedSubscriber(CoreSubscriber<? super T> subscriber,
                            C context) {
        this.context = context;
        this.subscriber = subscriber;
    }

    @Override
    public void onSubscribe(Subscription s) {
        activate();
        try {
            subscriber.onSubscribe(s);
        } finally {
            deactivate();
        }
    }


    @Override
    public void onNext(T next) {
        activate();
        try {
            subscriber.onNext(next);
        } finally {
            deactivate();
        }
    }

    @Override
    public void onError(Throwable t) {
        activate();
        try {
            subscriber.onError(t);
        } finally {
            deactivate();
        }
    }

    private void activate() {
        // only activate on the outer method call, not the nested calls within same thread
        if (callDepth.isNestedCallAndIncrement()) {
            return;
        }
        context.activate();
    }


    private void deactivate() {
        // only deactivate on the outer method call, not the nested calls within same thread
        if (callDepth.isNestedCallAndDecrement()) {
            return;
        }
        context.deactivate();
    }

    @Override
    public void onComplete() {
        activate();
        try {
            subscriber.onComplete();
        } finally {
            deactivate();
        }
    }

    /**
     * Register active context propagation
     *
     * @param tracer tracer
     */
    static void registerHooks(Tracer tracer) {
        if (isRegistered.getAndSet(true)) {
            return;
        }
        Hooks.onEachOperator(HOOK_KEY, wrapOperators(tracer));
    }

    /**
     * Unregister active context propagation. Should only be used for testing
     */
    static void unregisterHooks() {
        if (!isRegistered.getAndSet(false)) {
            return;
        }
        Hooks.resetOnEachOperator(HOOK_KEY);
    }

    /**
     * @return true if hook is registered. Should only be used for testing
     */
    static boolean isHookRegistered(){
        return isRegistered.get();
    }

    private static <X> Function<? super Publisher<X>, ? extends Publisher<X>> wrapOperators(final Tracer tracer) {
        //noinspection Convert2Lambda,rawtypes,Convert2Diamond
        return Operators.liftPublisher(new BiFunction<Publisher, CoreSubscriber<? super X>, CoreSubscriber<? super X>>() {
            @Override
            public CoreSubscriber<? super X> apply(Publisher p, CoreSubscriber<? super X> sub) {
                // don't wrap known #error #just #empty as they have instantaneous execution
                if (p instanceof Fuseable.ScalarCallable) {
                    return sub;
                }

                AbstractSpan<?> active = tracer.getActive();

                if (active == null) {
                    // no active context, we have nothing to wrap
                    return sub;
                }

                return new TracedSubscriber<>(sub, active);
            }
        });
    }


}
