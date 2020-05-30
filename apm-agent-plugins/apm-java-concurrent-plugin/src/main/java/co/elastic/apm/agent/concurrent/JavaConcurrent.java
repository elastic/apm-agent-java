package co.elastic.apm.agent.concurrent;

import co.elastic.apm.agent.bci.ElasticApmAgent;
import co.elastic.apm.agent.bci.ElasticApmInstrumentation;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.TraceContext;
import com.blogspot.mydailyjava.weaklockfree.WeakConcurrentMap;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;

public class JavaConcurrent {

    private static final WeakConcurrentMap<Object, AbstractSpan<?>> contextMap = new WeakConcurrentMap<Object, AbstractSpan<?>>(false);
    private static final List<Class<? extends ElasticApmInstrumentation>> RUNNABLE_CALLABLE_INSTRUMENTATION = Collections.singletonList(RunnableCallableInstrumentation.class);
    private static final ThreadLocal<Boolean> needsContext = new ThreadLocal<>();

    private static void removeContext(Object o) {
        AbstractSpan<?> context = contextMap.remove(o);
        if (context != null) {
            context.decrementReferences();
        }
    }

    @Nullable
    public static AbstractSpan<?> restoreContext(Object o, @Nullable ElasticApmTracer tracer) {
        if (tracer == null) {
            return null;
        }
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
     * Wraps the provided runnable and makes this {@link AbstractSpan} active in the {@link Runnable#run()} method.
     *
     * <p>
     * Note: does activates the {@link AbstractSpan} and not only the {@link TraceContext}.
     * This should only be used when the span is closed in thread the provided {@link Runnable} is executed in.
     * </p>
     */
    @Nullable
    public static Runnable withContext(@Nullable Runnable runnable, @Nullable ElasticApmTracer tracer) {
        if (runnable instanceof RunnableLambdaWrapper || runnable == null || tracer == null || needsContext.get() == Boolean.FALSE) {
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
        ElasticApmAgent.ensureInstrumented(runnable.getClass(), RUNNABLE_CALLABLE_INSTRUMENTATION);
        contextMap.put(runnable, active);
        active.incrementReferences();
        // Do no discard branches leading to async operations so not to break span references
        active.setNonDiscardable();
        return runnable;
    }

    /**
     * Wraps the provided runnable and makes this {@link AbstractSpan} active in the {@link Runnable#run()} method.
     *
     * <p>
     * Note: does activates the {@link AbstractSpan} and not only the {@link TraceContext}.
     * This should only be used when the span is closed in thread the provided {@link Runnable} is executed in.
     * </p>
     */
    @Nullable
    public static <T> Callable<T> withContext(@Nullable Callable<T> callable, @Nullable ElasticApmTracer tracer) {
        if (callable instanceof CallableLambdaWrapper || callable == null || tracer == null  || needsContext.get() == Boolean.FALSE) {
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
        ElasticApmAgent.ensureInstrumented(callable.getClass(), RUNNABLE_CALLABLE_INSTRUMENTATION);
        contextMap.put(callable, active);
        active.incrementReferences();
        return callable;
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
    public static <T> Collection<? extends Callable<T>> withContext(@Nullable Collection<? extends Callable<T>> callables, @Nullable ElasticApmTracer tracer) {
        if (callables == null || tracer == null) {
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
        for (Callable<T> callable : callables) {
            final Callable<T> potentiallyWrappedCallable = withContext(callable, tracer);
            needsContext.set(Boolean.TRUE);
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
