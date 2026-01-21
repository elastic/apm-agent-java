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
package co.elastic.apm.agent.concurrent;

import co.elastic.apm.agent.common.ThreadUtils;
import co.elastic.apm.agent.sdk.DynamicTransformer;
import co.elastic.apm.agent.sdk.ElasticApmInstrumentation;
import co.elastic.apm.agent.sdk.state.GlobalState;
import co.elastic.apm.agent.tracer.AbstractSpan;
import co.elastic.apm.agent.tracer.TraceState;
import co.elastic.apm.agent.tracer.GlobalTracer;
import co.elastic.apm.agent.tracer.Tracer;
import co.elastic.apm.agent.tracer.reference.ReferenceCountedMap;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ForkJoinTask;

// Not strictly necessary as AbstractJavaConcurrentInstrumentation returns an empty collection for pluginClassLoaderRootPackages
// but this signals the intent that this class must not be loaded from the IndyBootstrapClassLoader so that the state in this class applies globally
@GlobalState
public class JavaConcurrent {

    private static final ReferenceCountedMap<Object, TraceState<?>> contextMap = GlobalTracer.get().newReferenceCountedMap();

    private static final List<Class<? extends ElasticApmInstrumentation>> RUNNABLE_CALLABLE_FJTASK_INSTRUMENTATION = Collections.
        <Class<? extends ElasticApmInstrumentation>>singletonList(RunnableCallableForkJoinTaskInstrumentation.class);
    static final ThreadLocal<Boolean> needsContext = new ThreadLocal<>();

    private static final Set<String> EXCLUDED_EXECUTABLE_TYPES;

    static {
        EXCLUDED_EXECUTABLE_TYPES = new HashSet<String>();
        EXCLUDED_EXECUTABLE_TYPES.add(RunnableLambdaWrapper.class.getName());
        EXCLUDED_EXECUTABLE_TYPES.add(CallableLambdaWrapper.class.getName());
        // Spring-JMS polling mechanism that translates to passive onMessage handling
        EXCLUDED_EXECUTABLE_TYPES.add("org.springframework.jms.listener.DefaultMessageListenerContainer$AsyncMessageListenerInvoker");
        // Spring-Kafka polling mechanism - active loop that shouldn't inherit context from the starter
        EXCLUDED_EXECUTABLE_TYPES.add("org.springframework.kafka.listener.KafkaMessageListenerContainer$ListenerConsumer");
        EXCLUDED_EXECUTABLE_TYPES.add("com.zaxxer.hikari.pool.HikariPool$PoolEntryCreator");
        EXCLUDED_EXECUTABLE_TYPES.add("com.github.benmanes.caffeine.cache.BoundedLocalCache.PerformCleanupTask");
    }

    private static void removeContext(Object o) {
        contextMap.remove(o);
    }

    private static boolean shouldAvoidContextPropagation(@Nullable Object executable) {
        return executable == null ||
            Thread.currentThread().getName().startsWith(ThreadUtils.ELASTIC_APM_THREAD_PREFIX) ||
            EXCLUDED_EXECUTABLE_TYPES.contains(executable.getClass().getName()) ||
            needsContext.get() == Boolean.FALSE;
    }

    /**
     * Retrieves the context mapped to the provided task and activates it on the current thread.
     * It is the responsibility of the caller to deactivate the returned context at the right time.
     * If the mapped context is already the active span of this thread, this method returns {@code null}.
     *
     * @param o      a task for which running there may be a context to activate
     * @param tracer the tracer
     * @return the context mapped to the provided task or {@code null} if such does not exist or if the mapped context
     * is already the active one on the current thread.
     */
    @Nullable
    public static TraceState<?> restoreContext(Object o, Tracer tracer) {
        // When an Executor executes directly on the current thread we need to enable this thread for context propagation again
        needsContext.set(Boolean.TRUE);

        // we cannot remove yet, as this decrements the reference count, which may cause already ended spans to be recycled ahead of time
        TraceState<?> context = contextMap.get(o);
        if (context == null) {
            return null;
        }

        try {
            if (tracer.currentContext() != context) {
                return context.activate();
            } else {
                return null;
            }
        } finally {
            contextMap.remove(o);
        }
    }

    /**
     * Instruments or wraps the provided runnable and makes this {@link AbstractSpan} active in the {@link Runnable#run()} method.
     */
    @Nullable
    public static Runnable withContext(@Nullable Runnable runnable, Tracer tracer) {
        if (shouldAvoidContextPropagation(runnable)) {
            return runnable;
        }
        needsContext.set(Boolean.FALSE);
        TraceState<?> active = tracer.currentContext();
        if (active.isEmpty()) {
            return runnable;
        }
        if (isLambda(runnable)) {
            runnable = new RunnableLambdaWrapper(runnable);
        }
        captureContext(runnable, active);
        return runnable;
    }

    private static void captureContext(Object task, TraceState<?> active) {
        DynamicTransformer.ensureInstrumented(task.getClass(), RUNNABLE_CALLABLE_FJTASK_INSTRUMENTATION);
        contextMap.put(task, active);
        // Do no discard branches leading to async operations so not to break span references
        if (active.getSpan() != null) {
            active.getSpan().setNonDiscardable();
        }
    }

    /**
     * Instruments or wraps the provided runnable and makes this {@link AbstractSpan} active in the {@link Runnable#run()} method.
     */
    @Nullable
    public static <T> Callable<T> withContext(@Nullable Callable<T> callable, Tracer tracer) {
        if (shouldAvoidContextPropagation(callable)) {
            return callable;
        }
        needsContext.set(Boolean.FALSE);
        TraceState<?> active = tracer.currentContext();
        if (active.isEmpty()) {
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
        if (shouldAvoidContextPropagation(task)) {
            return task;
        }
        needsContext.set(Boolean.FALSE);
        TraceState<?> active = tracer.currentContext();
        if (active.isEmpty()) {
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

    public static void avoidPropagationOnCurrentThread() {
        needsContext.set(Boolean.FALSE);
    }

    public static void allowContextPropagationOnCurrentThread() {
        needsContext.set(Boolean.TRUE);
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
