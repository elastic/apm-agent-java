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

import co.elastic.apm.agent.context.InFlightRegistry;
import co.elastic.apm.agent.impl.Tracer;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.sdk.state.CallDepth;
import co.elastic.apm.agent.sdk.state.GlobalVariables;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.CoreSubscriber;
import reactor.core.Fuseable;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Operators;

import javax.annotation.Nullable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;
import java.util.function.Function;

public class TracedSubscriber<T> implements CoreSubscriber<T> {

    private static final Logger log = LoggerFactory.getLogger(TracedSubscriber.class);

    private static final AtomicBoolean isRegistered = GlobalVariables.get(ReactorInstrumentation.class, "reactor-hook-enabled", new AtomicBoolean(false));
    private static final CallDepth callDepth = CallDepth.get(TracedSubscriber.class);

    private static final String HOOK_KEY = "elastic-apm";

    protected final CoreSubscriber<? super T> subscriber;

    private final AbstractSpan<?> context;
    private final Tracer tracer;

    public TracedSubscriber(CoreSubscriber<? super T> subscriber, Tracer tracer, AbstractSpan<?> context) {
        this.subscriber = subscriber;
        this.context = context;
        this.tracer = tracer;

        InFlightRegistry.inFlightStart(context);
    }

    @Override
    public void onSubscribe(Subscription s) {
        boolean hasActivated = doEnter("onSubscribe");
        try {
            subscriber.onSubscribe(s);
        } finally {
            doExit(hasActivated, "onSubscribe");
        }
    }

    @Override
    public void onNext(T next) {
        boolean hasActivated = doEnter("onNext");
        try {
            subscriber.onNext(next);
        } finally {
            doExit(hasActivated, "onNext");
        }
    }

    @Override
    public void onError(Throwable t) {
        boolean hasActivated = doEnter("onError");
        try {
            subscriber.onError(t);
        } finally {
            doExit(hasActivated, "onError");
        }
    }

    @Override
    public void onComplete() {
        boolean hasActivated = doEnter("onComplete");
        try {
            subscriber.onComplete();
        } finally {
            doExit(hasActivated, "onComplete");
        }
    }

    @Nullable
    private boolean doEnter(String method) {
        // only do something on outer method call, not the nested calls within same thread
        if (callDepth.isNestedCallAndIncrement()) {
            return false;
        }

        debugTrace(true, method);

        if (tracer.getActive() == context) {
            // already activated
            return false;
        }

        return InFlightRegistry.activateInFlight(context);
    }

    private void doExit(boolean deactivate, String method) {
        // only do something on outer method call, not the nested calls within same thread
        if (callDepth.isNestedCallAndDecrement()) {
            return;
        }

        debugTrace(false, method);

        if (!deactivate) {
            return;
        }

        if(tracer.getActive() != context){
            return;
        }

        InFlightRegistry.deactivateInFlight(context);
    }

    private void debugTrace(boolean isEnter, String method) {
        if (!log.isDebugEnabled()) {
            return;
        }
        log.debug("{} reactor {}", isEnter ? ">>" : "<<", method);
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
    static boolean isHookRegistered() {
        return isRegistered.get();
    }

    private static <X> Function<? super Publisher<X>, ? extends Publisher<X>> wrapOperators(final Tracer tracer) {
        //noinspection Convert2Lambda,rawtypes,Convert2Diamond
        return Operators.liftPublisher(new BiFunction<Publisher, CoreSubscriber<? super X>, CoreSubscriber<? super X>>() {
            @Override
            public CoreSubscriber<? super X> apply(Publisher publisher, CoreSubscriber<? super X> subscriber) {
                // don't wrap known #error #just #empty as they have instantaneous execution
                if (publisher instanceof Fuseable.ScalarCallable) {
                    log.trace("skip wrapping {}", subscriber.toString());
                    return subscriber;
                }

                AbstractSpan<?> active = tracer.getActive();

                if (active == null) {
                    // no active context, we have nothing to wrap
                    return subscriber;
                }

                log.trace("wrapping subscriber {} with active span/transaction {}", subscriber.toString(), active);

                return new TracedSubscriber<>(subscriber, tracer, active);
            }
        });
    }


}
