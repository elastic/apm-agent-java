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
package co.elastic.apm.agent.concurrent;

import co.elastic.apm.agent.impl.Tracer;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.sdk.DynamicTransformer;
import co.elastic.apm.agent.sdk.ElasticApmInstrumentation;
import co.elastic.apm.agent.sdk.weakmap.WeakMapSupplier;
import com.blogspot.mydailyjava.weaklockfree.WeakConcurrentMap;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ForkJoinTask;

public class JavaConcurrent {

    private static final WeakConcurrentMap<Object, AbstractSpan<?>> contextMap = WeakMapSupplier.createMap();
    private static final List<Class<? extends ElasticApmInstrumentation>> RUNNABLE_CALLABLE_FJTASK_INSTRUMENTATION = Collections.
        <Class<? extends ElasticApmInstrumentation>>singletonList(RunnableCallableForkJoinTaskInstrumentation.class);
    static final ThreadLocal<Boolean> needsContext = new ThreadLocal<>();

    private static void removeContext(Object o) {
        AbstractSpan<?> context = contextMap.remove(o);
        if (context != null) {
            context.decrementReferences();
        }
    }

    @Nullable
    public static AbstractSpan<?> restoreContext(Object o, Tracer tracer) {
        // When an Executor executes directly on the current thread we need to enable this thread for context propagation again
        needsContext.set(Boolean.TRUE);
        AbstractSpan<?> context = contextMap.remove(o);
        if (context == null) {
            return null;
        }
        if (tracer.getActive() != context) {
            context.activate();
            context.decrementReferences();
            return context;
        } else {
            context.decrementReferences();
            return null;
        }
    }

    /**
     * Instruments or wraps the provided runnable and makes this {@link AbstractSpan} active in the {@link Runnable#run()} method.
     */
    @Nullable
    public static Runnable withContext(@Nullable Runnable runnable, Tracer tracer) {
        if (runnable instanceof RunnableLambdaWrapper || runnable == null || needsContext.get() == Boolean.FALSE) {
            return runnable;
        }
        needsContext.set(Boolean.FALSE);
        AbstractSpan<?> active = tracer.getActive();
        if (active == null) {
            return runnable;
        }
        if (isLambda(runnable)) {
            runnable = new RunnableLambdaWrapper(runnable);
        }
        captureContext(runnable, active);
        return runnable;
    }

    private static void captureContext(Object task, AbstractSpan<?> active) {
        DynamicTransformer.Accessor.get().ensureInstrumented(task.getClass(), RUNNABLE_CALLABLE_FJTASK_INSTRUMENTATION);
        contextMap.put(task, active);
        active.incrementReferences();
        // Do no discard branches leading to async operations so not to break span references
        active.setNonDiscardable();
    }

    /**
     * Instruments or wraps the provided runnable and makes this {@link AbstractSpan} active in the {@link Runnable#run()} method.
     */
    @Nullable
    public static <T> Callable<T> withContext(@Nullable Callable<T> callable, Tracer tracer) {
        if (callable instanceof CallableLambdaWrapper || callable == null || needsContext.get() == Boolean.FALSE) {
            return callable;
        }
        needsContext.set(Boolean.FALSE);
        AbstractSpan<?> active = tracer.getActive();
        if (active == null) {
            return callable;
        }
        if (isLambda(callable)) {
            callable = new CallableLambdaWrapper<>(callable);
        }
        captureContext(callable, active);
        return callable;
    }

    @Nullable
    public static <T> ForkJoinTask<T> withContext(@Nullable ForkJoinTask<T> task, Tracer tracer) {
        if (task == null || needsContext.get() == Boolean.FALSE) {
            return task;
        }
        needsContext.set(Boolean.FALSE);
        AbstractSpan<?> active = tracer.getActive();
        if (active == null) {
            return task;
        }
        captureContext(task, active);
        return task;
    }

    public static void doFinally(@Nullable Throwable thrown, @Nullable Object contextObject) {
        needsContext.set(Boolean.TRUE);
        if (thrown != null && contextObject != null) {
            removeContext(contextObject);
        }
    }

    public static void doFinally(@Nullable Throwable thrown, @Nullable Collection<? extends Callable<?>> callables) {
        needsContext.set(Boolean.TRUE);
        if (thrown != null && callables != null) {
            for (Callable<?> callable : callables) {
                removeContext(callable);
            }
        }
    }

    private static boolean isLambda(Object o) {
        return o.getClass().getName().indexOf('/') != -1;
    }

    @Nullable
    public static <T> Collection<? extends Callable<T>> withContext(@Nullable Collection<? extends Callable<T>> callables, Tracer tracer) {
        if (callables == null) {
            return null;
        }
        if (callables.isEmpty()) {
            return callables;
        }
        final Collection<Callable<T>> wrapped;
        if (needsWrapping(callables)) {
            wrapped = new ArrayList<>(callables.size());
        } else {
            wrapped = null;
        }
        Boolean context = needsContext.get();
        for (Callable<T> callable : callables) {
            // restore previous state as withContext always sets to false
            needsContext.set(context);
            final Callable<T> potentiallyWrappedCallable = withContext(callable, tracer);
            if (wrapped != null) {
                wrapped.add(potentiallyWrappedCallable);
            }
        }
        needsContext.set(Boolean.FALSE);
        return wrapped != null ? wrapped : callables;
    }

    private static boolean needsWrapping(Collection<? extends Callable<?>> callables) {
        for (Callable<?> callable : callables) {
            if (isLambda(callable)) {
                return true;
            }
        }
        return false;
    }

    public static class RunnableLambdaWrapper implements Runnable {

        private final Runnable delegate;

        public RunnableLambdaWrapper(Runnable delegate) {
            this.delegate = delegate;
        }

        @Override
        public void run() {
            delegate.run();
        }
    }

    public static class CallableLambdaWrapper<V> implements Callable<V> {
        private final Callable<V> delegate;

        public CallableLambdaWrapper(Callable<V> delegate) {
            this.delegate = delegate;
        }

        @Override
        public V call() throws Exception {
            return delegate.call();
        }
    }
}
