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
package co.elastic.apm.agent.reactor;

import co.elastic.apm.agent.collections.WeakConcurrentProviderImpl;
import co.elastic.apm.agent.impl.Tracer;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.sdk.state.GlobalVariables;
import co.elastic.apm.agent.sdk.weakconcurrent.WeakMap;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;
import reactor.core.CoreSubscriber;
import reactor.core.Fuseable;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Operators;
import reactor.util.context.Context;

import javax.annotation.Nullable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;
import java.util.function.Function;

public class TracedSubscriber<T> implements CoreSubscriber<T> {

    private static final Logger log = LoggerFactory.getLogger(TracedSubscriber.class);

    private static final AtomicBoolean isRegistered = GlobalVariables.get(ReactorInstrumentation.class, "reactor-hook-enabled", new AtomicBoolean(false));

    private static final WeakMap<TracedSubscriber<?>, AbstractSpan<?>> contextMap = WeakConcurrentProviderImpl.createWeakSpanMap();

    private static final String HOOK_KEY = "elastic-apm";

    private final CoreSubscriber<? super T> subscriber;

    private final Tracer tracer;

    private final Context context;

    TracedSubscriber(CoreSubscriber<? super T> subscriber, Tracer tracer, AbstractSpan<?> context) {
        this.subscriber = subscriber;
        this.tracer = tracer;
        contextMap.put(this, context);

        // store our span/transaction into reactor context for later lookup without relying on active tracer state
        this.context = subscriber.currentContext().put(AbstractSpan.class, context);
    }

    @Override
    public Context currentContext() {
        return context;
    }

    /**
     * Wraps {@link Subscriber#onSubscribe(Subscription)} for context propagation, executed in "subscriber scheduler".
     *
     * @param s subscription
     */
    @Override
    public void onSubscribe(Subscription s) {
        AbstractSpan<?> context = getContext();
        boolean hasActivated = doEnter("onSubscribe", context);
        Throwable thrown = null;
        try {
            subscriber.onSubscribe(s);
        } catch (Throwable e) {
            thrown = e;
            throw e;
        } finally {
            doExit(hasActivated, "onSubscribe", context);
            discardIf(thrown != null);
        }
    }

    /**
     * Wraps {@link Subscriber#onNext(Object)} for context propagation, executed in "publisher scheduler"
     *
     * @param next next item
     */
    @Override
    public void onNext(T next) {
        AbstractSpan<?> context = getContext();
        boolean hasActivated = doEnter("onNext", context);
        Throwable thrown = null;
        try {
            subscriber.onNext(next);
        } catch (Throwable e) {
            thrown = e;
            throw e;
        } finally {
            doExit(hasActivated, "onNext", context);
            discardIf(thrown != null);
        }
    }

    /**
     * Wraps {@link Subscriber#onError(Throwable)} for context propagation, executed in "publisher scheduler"
     *
     * @param t error
     */
    @Override
    public void onError(Throwable t) {
        AbstractSpan<?> context = getContext();
        boolean hasActivated = doEnter("onError", context);
        try {
            subscriber.onError(t);
        } finally {
            doExit(hasActivated, "onError", context);
            discardIf(true);
        }
    }

    /**
     * Wraps {@link Subscriber#onComplete()} for context propagation, executed in "publisher scheduler"
     */
    @Override
    public void onComplete() {
        AbstractSpan<?> context = getContext();
        boolean hasActivated = doEnter("onComplete", context);
        try {
            subscriber.onComplete();
        } finally {
            doExit(hasActivated, "onComplete", context);
            discardIf(true);
        }
    }

    /**
     * Wrapped method entry
     *
     * @param method  method name (only for debugging)
     * @param context context
     * @return {@literal true} if context has been activated
     */
    private boolean doEnter(String method, @Nullable AbstractSpan<?> context) {
        debugTrace(true, method, context);

        if (context == null || tracer.getActive() == context) {
            // already activated or discarded
            return false;
        }

        context.activate();
        return true;
    }

    /**
     * Wrapped method exit
     *
     * @param deactivate {@literal true} to de-activate due to a previous activation, no-op otherwise
     * @param method     method name (only for debugging)
     * @param context    context
     */
    private void doExit(boolean deactivate, String method, @Nullable AbstractSpan<?> context) {
        debugTrace(false, method, context);

        if (context == null || !deactivate) {
            return;
        }

        if (context != tracer.getActive()) {
            // don't attempt to deactivate if not the active one
            return;
        }

        // the current context has been activated on enter thus must be the active one
        context.deactivate();
    }

    private void discardIf(boolean condition) {
        if (!condition) {
            return;
        }
        contextMap.remove(this);
    }

    private void debugTrace(boolean isEnter, String method, @Nullable AbstractSpan<?> context) {
        if (!log.isTraceEnabled()) {
            return;
        }
        log.trace("{} reactor {} {}", isEnter ? ">>" : "<<", method, context);
    }

    /**
     * @return context associated with {@literal this}.
     */
    @Nullable
    private AbstractSpan<?> getContext() {
        return contextMap.get(this);
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

                // use active span/transaction if directly active
                AbstractSpan<?> active = tracer.getActive();

                // fallback to using context-stored span/transaction if not already active
                if (active == null) {
                    active = subscriber.currentContext().getOrDefault(AbstractSpan.class, null);
                }

                if (active == null) {
                    // no active context, we have nothing to wrap
                    return subscriber;
                }

                log.trace("wrapping subscriber {} publisher {} with active span/transaction {}", subscriber.toString(), publisher, active);

                return new TracedSubscriber<>(subscriber, tracer, active);
            }
        });
    }
}
