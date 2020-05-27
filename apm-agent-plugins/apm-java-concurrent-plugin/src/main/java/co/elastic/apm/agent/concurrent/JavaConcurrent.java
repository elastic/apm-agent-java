package co.elastic.apm.agent.concurrent;

import co.elastic.apm.agent.bci.ElasticApmAgent;
import co.elastic.apm.agent.bci.ElasticApmInstrumentation;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.TraceContext;
import com.blogspot.mydailyjava.weaklockfree.WeakConcurrentMap;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;

public class JavaConcurrent {

    private static final WeakConcurrentMap<Object, AbstractSpan<?>> contextMap = new WeakConcurrentMap<Object, AbstractSpan<?>>(false);
    private final ElasticApmTracer tracer;
    private static final List<Class<? extends ElasticApmInstrumentation>> RUNNABLE_CALLABLE_INSTRUMENTATION = Collections.singletonList(RunnableCallableInstrumentation.class);


    public JavaConcurrent(ElasticApmTracer tracer) {
        this.tracer = tracer;
    }

    public void removeContext(Object o) {
        AbstractSpan<?> context = contextMap.remove(o);
        if (context != null) {
            context.decrementReferences();
        }
    }

    @Nullable
    public AbstractSpan<?> restoreContext(Object o) {
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
    public Runnable withContext(@Nullable Runnable runnable) {
        if (runnable instanceof RunnableLambdaWrapper || runnable == null) {
            return runnable;
        }
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
    public <T> Callable<T> withContext(@Nullable Callable<T> callable) {
        if (callable instanceof CallableLambdaWrapper || callable == null) {
            return callable;
        }
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

    private static boolean isLambda(Object o) {
        return o.getClass().getName().indexOf('/') != -1;
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
