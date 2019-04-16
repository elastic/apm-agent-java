/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2019 Elastic and contributors
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package co.elastic.apm.agent.impl;

import co.elastic.apm.agent.configuration.CoreConfiguration;
import co.elastic.apm.agent.configuration.ServiceNameUtil;
import co.elastic.apm.agent.context.LifecycleListener;
import co.elastic.apm.agent.impl.async.ContextInScopeCallableWrapper;
import co.elastic.apm.agent.impl.async.ContextInScopeRunnableWrapper;
import co.elastic.apm.agent.impl.async.SpanInScopeCallableWrapper;
import co.elastic.apm.agent.impl.async.SpanInScopeRunnableWrapper;
import co.elastic.apm.agent.impl.error.ErrorCapture;
import co.elastic.apm.agent.impl.sampling.ProbabilitySampler;
import co.elastic.apm.agent.impl.sampling.Sampler;
import co.elastic.apm.agent.impl.stacktrace.StacktraceConfiguration;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.TraceContext;
import co.elastic.apm.agent.impl.transaction.TraceContextHolder;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.metrics.MetricRegistry;
import co.elastic.apm.agent.objectpool.Allocator;
import co.elastic.apm.agent.objectpool.ObjectPool;
import co.elastic.apm.agent.objectpool.impl.QueueBasedObjectPool;
import co.elastic.apm.agent.report.Reporter;
import co.elastic.apm.agent.report.ReporterConfiguration;
import co.elastic.apm.agent.util.DependencyInjectingServiceLoader;
import com.blogspot.mydailyjava.weaklockfree.WeakConcurrentMap;
import org.jctools.queues.atomic.AtomicQueueFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.stagemonitor.configuration.ConfigurationOption;
import org.stagemonitor.configuration.ConfigurationOptionProvider;
import org.stagemonitor.configuration.ConfigurationRegistry;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Callable;

import static org.jctools.queues.spec.ConcurrentQueueSpec.createBoundedMpmc;

/**
 * This is the tracer implementation which provides access to lower level agent functionality.
 * <p>
 * Note that this is a internal API, so there are no guarantees in terms of backwards compatibility.
 * </p>
 */
public class ElasticApmTracer {
    private static final Logger logger = LoggerFactory.getLogger(ElasticApmTracer.class);

    /**
     * The number of required {@link Runnable} wrappers does not depend on the size of the disruptor
     * but rather on the amount of application threads.
     * The requirement increases if the application tends to wrap multiple {@link Runnable}s.
     */
    private static final int MAX_POOLED_RUNNABLES = 256;

    private final ConfigurationRegistry configurationRegistry;
    private final StacktraceConfiguration stacktraceConfiguration;
    private final Iterable<LifecycleListener> lifecycleListeners;
    private final ObjectPool<Transaction> transactionPool;
    private final ObjectPool<Span> spanPool;
    private final ObjectPool<ErrorCapture> errorPool;
    private final ObjectPool<SpanInScopeRunnableWrapper> runnableSpanWrapperObjectPool;
    private final ObjectPool<SpanInScopeCallableWrapper<?>> callableSpanWrapperObjectPool;
    private final ObjectPool<ContextInScopeRunnableWrapper> runnableContextWrapperObjectPool;
    private final ObjectPool<ContextInScopeCallableWrapper<?>> callableContextWrapperObjectPool;
    private final Reporter reporter;
    // Maintains a stack of all the activated spans
    // This way its easy to retrieve the bottom of the stack (the transaction)
    // Also, the caller does not have to keep a reference to the previously active span, as that is maintained by the stack
    private final ThreadLocal<Deque<TraceContextHolder<?>>> activeStack = new ThreadLocal<Deque<TraceContextHolder<?>>>() {
        @Override
        protected Deque<TraceContextHolder<?>> initialValue() {
            return new ArrayDeque<TraceContextHolder<?>>();
        }
    };
    private final CoreConfiguration coreConfiguration;
    private final List<ActivationListener> activationListeners;
    private final MetricRegistry metricRegistry;
    private Sampler sampler;
    boolean assertionsEnabled = false;
    private static final WeakConcurrentMap<ClassLoader, String> serviceNameByClassLoader = new WeakConcurrentMap.WithInlinedExpunction<>();

    ElasticApmTracer(ConfigurationRegistry configurationRegistry, Reporter reporter, Iterable<LifecycleListener> lifecycleListeners) {
        this.metricRegistry = new MetricRegistry(configurationRegistry.getConfig(ReporterConfiguration.class));
        this.configurationRegistry = configurationRegistry;
        this.reporter = reporter;
        this.stacktraceConfiguration = configurationRegistry.getConfig(StacktraceConfiguration.class);
        this.lifecycleListeners = lifecycleListeners;
        int maxPooledElements = configurationRegistry.getConfig(ReporterConfiguration.class).getMaxQueueSize() * 2;
        coreConfiguration = configurationRegistry.getConfig(CoreConfiguration.class);
        transactionPool = QueueBasedObjectPool.ofRecyclable(AtomicQueueFactory.<Transaction>newQueue(createBoundedMpmc(maxPooledElements)), false,
            new Allocator<Transaction>() {
                @Override
                public Transaction createInstance() {
                    return new Transaction(ElasticApmTracer.this);
                }
            });
        spanPool = QueueBasedObjectPool.ofRecyclable(AtomicQueueFactory.<Span>newQueue(createBoundedMpmc(maxPooledElements)), false,
            new Allocator<Span>() {
                @Override
                public Span createInstance() {
                    return new Span(ElasticApmTracer.this);
                }
            });
        // we are assuming that we don't need as many errors as spans or transactions
        errorPool = QueueBasedObjectPool.ofRecyclable(AtomicQueueFactory.<ErrorCapture>newQueue(createBoundedMpmc(maxPooledElements / 2)), false,
            new Allocator<ErrorCapture>() {
                @Override
                public ErrorCapture createInstance() {
                    return new ErrorCapture(ElasticApmTracer.this);
                }
            });
        // consider specialized object pools which return the objects to the thread-local pool of their originating thread
        // with a combination of DetachedThreadLocal and org.jctools.queues.MpscRelaxedArrayQueue
        runnableSpanWrapperObjectPool = QueueBasedObjectPool.ofRecyclable(AtomicQueueFactory.<SpanInScopeRunnableWrapper>newQueue(createBoundedMpmc(MAX_POOLED_RUNNABLES)), false,
            new Allocator<SpanInScopeRunnableWrapper>() {
                @Override
                public SpanInScopeRunnableWrapper createInstance() {
                    return new SpanInScopeRunnableWrapper(ElasticApmTracer.this);
                }
            });
        callableSpanWrapperObjectPool = QueueBasedObjectPool.ofRecyclable(AtomicQueueFactory.<SpanInScopeCallableWrapper<?>>newQueue(createBoundedMpmc(MAX_POOLED_RUNNABLES)), false,
            new Allocator<SpanInScopeCallableWrapper<?>>() {
                @Override
                public SpanInScopeCallableWrapper<?> createInstance() {
                    return new SpanInScopeCallableWrapper<>(ElasticApmTracer.this);
                }
            });
        runnableContextWrapperObjectPool = QueueBasedObjectPool.ofRecyclable(AtomicQueueFactory.<ContextInScopeRunnableWrapper>newQueue(createBoundedMpmc(MAX_POOLED_RUNNABLES)), false,
            new Allocator<ContextInScopeRunnableWrapper>() {
                @Override
                public ContextInScopeRunnableWrapper createInstance() {
                    return new ContextInScopeRunnableWrapper(ElasticApmTracer.this);
                }
            });
        callableContextWrapperObjectPool = QueueBasedObjectPool.ofRecyclable(AtomicQueueFactory.<ContextInScopeCallableWrapper<?>>newQueue(createBoundedMpmc(MAX_POOLED_RUNNABLES)), false,
            new Allocator<ContextInScopeCallableWrapper<?>>() {
                @Override
                public ContextInScopeCallableWrapper<?> createInstance() {
                    return new ContextInScopeCallableWrapper<>(ElasticApmTracer.this);
                }
            });
        sampler = ProbabilitySampler.of(coreConfiguration.getSampleRate().get());
        coreConfiguration.getSampleRate().addChangeListener(new ConfigurationOption.ChangeListener<Double>() {
            @Override
            public void onChange(ConfigurationOption<?> configurationOption, Double oldValue, Double newValue) {
                sampler = ProbabilitySampler.of(newValue);
            }
        });
        for (LifecycleListener lifecycleListener : lifecycleListeners) {
            lifecycleListener.start(this);
        }
        this.activationListeners = DependencyInjectingServiceLoader.load(ActivationListener.class, this);
        reporter.scheduleMetricReporting(metricRegistry, configurationRegistry.getConfig(ReporterConfiguration.class).getMetricsIntervalMs());

        // sets the assertionsEnabled flag to true if indeed enabled
        assert assertionsEnabled = true;
    }

    /**
     * Starts a transaction as a child of the provided parent
     *
     * @param childContextCreator   used to make the transaction a child of the provided parent
     * @param parent                the parent of the transaction. May be a traceparent header.
     * @param initiatingClassLoader the class loader corresponding to the service which initiated the creation of the transaction.
     *                              Used to determine the service name.
     * @param <T>                   the type of the parent. {@code String} in case of a traceparent header.
     * @return a transaction which is a child of the provided parent
     */
    public <T> Transaction startTransaction(TraceContext.ChildContextCreator<T> childContextCreator, @Nullable T parent, @Nullable ClassLoader initiatingClassLoader) {
        return startTransaction(childContextCreator, parent, sampler, -1, initiatingClassLoader);
    }

    /**
     * Starts a transaction as a child of the provided parent
     *
     * @param childContextCreator   used to make the transaction a child of the provided parent
     * @param parent                the parent of the transaction. May be a traceparent header.
     * @param sampler               the {@link Sampler} instance which is responsible for determining the sampling decision if this is a root transaction
     * @param epochMicros           the start timestamp
     * @param initiatingClassLoader the class loader corresponding to the service which initiated the creation of the transaction
     *                              Used to determine the service name.
     * @param <T>                   the type of the parent. {@code String} in case of a traceparent header.
     * @return a transaction which is a child of the provided parent
     */
    public <T> Transaction startTransaction(TraceContext.ChildContextCreator<T> childContextCreator, @Nullable T parent, Sampler sampler, long epochMicros, @Nullable ClassLoader initiatingClassLoader) {
        Transaction transaction;
        if (!coreConfiguration.isActive()) {
            transaction = noopTransaction();
        } else {
            transaction = createTransaction().start(childContextCreator, parent, epochMicros, sampler);
        }
        if (logger.isDebugEnabled()) {
            logger.debug("startTransaction {} {", transaction);
            if (logger.isTraceEnabled()) {
                logger.trace("starting transaction at",
                    new RuntimeException("this exception is just used to record where the transaction has been started from"));
            }
        }
        final String serviceName = getServiceName(initiatingClassLoader);
        if (serviceName != null) {
            transaction.getTraceContext().setServiceName(serviceName);
        }
        return transaction;
    }

    public Transaction noopTransaction() {
        return createTransaction().startNoop();
    }

    private Transaction createTransaction() {
        Transaction transaction = transactionPool.createInstance();
        while (transaction.getReferenceCount() != 0) {
            logger.warn("Tried to start a transaction with a non-zero reference count {} {}", transaction.getReferenceCount(), transaction);
            transaction = transactionPool.createInstance();
        }
        return transaction;
    }

    @Nullable
    public Transaction currentTransaction() {
        final TraceContextHolder<?> bottomOfStack = activeStack.get().peekLast();
        if (bottomOfStack instanceof Transaction) {
            return (Transaction) bottomOfStack;
        } else {
            for (Iterator<TraceContextHolder<?>> it = activeStack.get().descendingIterator(); it.hasNext(); ) {
                TraceContextHolder<?> context = it.next();
                if (context instanceof Transaction) {
                    return (Transaction) context;
                }
            }
        }
        return null;
    }

    /**
     * Starts a span with a given parent context.
     * <p>
     * This method makes it possible to start a span after the parent has already ended.
     * </p>
     *
     * @param parentContext the trace context of the parent
     * @return a new started span
     */
    public <T> Span startSpan(TraceContext.ChildContextCreator<T> childContextCreator, T parentContext) {
        return startSpan(childContextCreator, parentContext, -1);
    }

    public Span startSpan(AbstractSpan<?> parent, long epochMicros) {
        return startSpan(TraceContext.fromParent(), parent, epochMicros);
    }

    /**
     * @param parentContext the trace context of the parent
     * @param epochMicros   the start timestamp of the span in microseconds after epoch
     * @return a new started span
     * @see #startSpan(TraceContext.ChildContextCreator, Object)
     */
    public <T> Span startSpan(TraceContext.ChildContextCreator<T> childContextCreator, T parentContext, long epochMicros) {
        Span span = createSpan();
        final boolean dropped;
        Transaction transaction = currentTransaction();
        if (transaction != null) {
            if (isTransactionSpanLimitReached(transaction)) {
                dropped = true;
                transaction.getSpanCount().getDropped().incrementAndGet();
            } else {
                dropped = false;
                transaction.getSpanCount().getStarted().incrementAndGet();
            }
        } else {
            dropped = false;
        }
        span.start(childContextCreator, parentContext, epochMicros, dropped);
        return span;
    }

    private Span createSpan() {
        Span span = spanPool.createInstance();
        while (span.getReferenceCount() != 0) {
            logger.warn("Tried to start a span with a non-zero reference count {} {}", span.getReferenceCount(), span);
            span = spanPool.createInstance();
        }
        return span;
    }

    private boolean isTransactionSpanLimitReached(Transaction transaction) {
        return coreConfiguration.getTransactionMaxSpans() <= transaction.getSpanCount().getStarted().get();
    }

    /**
     * Captures an exception without providing an explicit reference to a parent {@link TraceContextHolder}
     *
     * @param e                     the exception to capture
     * @param initiatingClassLoader the class
     */
    public void captureException(@Nullable Throwable e, ClassLoader initiatingClassLoader) {
        captureException(System.currentTimeMillis() * 1000, e, getActive(), initiatingClassLoader);
    }

    public void captureException(long epochMicros, @Nullable Throwable e, TraceContextHolder<?> parent) {
        captureException(epochMicros, e, parent, null);
    }

    public void captureException(long epochMicros, @Nullable Throwable e, @Nullable TraceContextHolder<?> parent, @Nullable ClassLoader initiatingClassLoader) {
        if (e != null) {
            ErrorCapture error = errorPool.createInstance();
            error.withTimestamp(epochMicros);
            error.setException(e);
            Transaction currentTransaction = currentTransaction();
            if (currentTransaction != null) {
                error.setTransactionType(currentTransaction.getType());
                error.setTransactionSampled(currentTransaction.isSampled());
            }
            if (parent != null) {
                error.asChildOf(parent);
            } else {
                error.getTraceContext().getId().setToRandomValue();
                error.getTraceContext().setServiceName(getServiceName(initiatingClassLoader));
            }
            reporter.report(error);
        }
    }

    public ConfigurationRegistry getConfigurationRegistry() {
        return configurationRegistry;
    }

    public <T extends ConfigurationOptionProvider> T getConfig(Class<T> pluginClass) {
        return configurationRegistry.getConfig(pluginClass);
    }

    @SuppressWarnings("ReferenceEquality")
    public void endTransaction(Transaction transaction) {
        if (logger.isDebugEnabled()) {
            logger.debug("} endTransaction {}", transaction);
            if (logger.isTraceEnabled()) {
                logger.trace("ending transaction at",
                    new RuntimeException("this exception is just used to record where the transaction has been ended from"));
            }
        }
        if (!transaction.isNoop()) {
            // we do report non-sampled transactions (without the context)
            reporter.report(transaction);
        } else {
            transaction.decrementReferences();
        }
    }

    @SuppressWarnings("ReferenceEquality")
    public void endSpan(Span span) {
        if (span.isSampled()) {
            long spanFramesMinDurationMs = stacktraceConfiguration.getSpanFramesMinDurationMs();
            if (spanFramesMinDurationMs != 0 && span.isSampled()) {
                if (span.getDurationMs() >= spanFramesMinDurationMs) {
                    span.withStacktrace(new Throwable());
                }
            }
            reporter.report(span);
        } else {
            span.decrementReferences();
        }
    }

    public void recycle(Transaction transaction) {
        transactionPool.recycle(transaction);
    }

    public void recycle(Span span) {
        spanPool.recycle(span);
    }

    public void recycle(ErrorCapture error) {
        errorPool.recycle(error);
    }

    public Runnable wrapRunnable(Runnable delegate, AbstractSpan<?> span) {
        if (delegate instanceof SpanInScopeRunnableWrapper) {
            return delegate;
        }
        return runnableSpanWrapperObjectPool.createInstance().wrap(delegate, span);
    }

    public void recycle(SpanInScopeRunnableWrapper wrapper) {
        runnableSpanWrapperObjectPool.recycle(wrapper);
    }

    public <V> Callable<V> wrapCallable(Callable<V> delegate, AbstractSpan<?> span) {
        if (delegate instanceof SpanInScopeCallableWrapper) {
            return delegate;
        }
        return ((SpanInScopeCallableWrapper<V>) callableSpanWrapperObjectPool.createInstance()).wrap(delegate, span);
    }

    public void recycle(SpanInScopeCallableWrapper<?> wrapper) {
        callableSpanWrapperObjectPool.recycle(wrapper);
    }

    public Runnable wrapRunnable(Runnable delegate, TraceContext traceContext) {
        if (delegate instanceof ContextInScopeRunnableWrapper || delegate instanceof SpanInScopeRunnableWrapper) {
            return delegate;
        }
        return runnableContextWrapperObjectPool.createInstance().wrap(delegate, traceContext);
    }

    public void recycle(ContextInScopeRunnableWrapper wrapper) {
        runnableContextWrapperObjectPool.recycle(wrapper);
    }

    public <V> Callable<V> wrapCallable(Callable<V> delegate, TraceContext traceContext) {
        if (delegate instanceof ContextInScopeCallableWrapper || delegate instanceof SpanInScopeCallableWrapper) {
            return delegate;
        }
        return ((ContextInScopeCallableWrapper<V>) callableContextWrapperObjectPool.createInstance()).wrap(delegate, traceContext);
    }

    public void recycle(ContextInScopeCallableWrapper<?> callableWrapper) {
        callableContextWrapperObjectPool.recycle(callableWrapper);
    }

    /**
     * Called when the container shuts down.
     * Cleans up thread pools and other resources.
     */
    public void stop() {
        try {
            configurationRegistry.close();
            reporter.close();
            transactionPool.close();
            spanPool.close();
            errorPool.close();
            for (LifecycleListener lifecycleListener : lifecycleListeners) {
                lifecycleListener.stop();
            }
        } catch (Exception e) {
            logger.warn("Suppressed exception while calling stop()", e);
        }
    }

    public Reporter getReporter() {
        return reporter;
    }

    public Sampler getSampler() {
        return sampler;
    }

    @Nullable
    public TraceContextHolder<?> getActive() {
        return activeStack.get().peek();
    }

    public void registerSpanListener(ActivationListener activationListener) {
        this.activationListeners.add(activationListener);
    }

    public List<ActivationListener> getActivationListeners() {
        return activationListeners;
    }

    public void activate(TraceContextHolder<?> holder) {
        if (logger.isDebugEnabled()) {
            logger.debug("Activating {} on thread {}", holder.getTraceContext(), Thread.currentThread().getId());
        }
        activeStack.get().push(holder);
    }

    public void deactivate(TraceContextHolder<?> holder) {
        if (logger.isDebugEnabled()) {
            logger.debug("Deactivating {} on thread {}", holder.getTraceContext(), Thread.currentThread().getId());
        }
        final Deque<TraceContextHolder<?>> stack = activeStack.get();
        assertIsActive(holder, stack.poll());
    }

    private void assertIsActive(Object span, @Nullable Object currentlyActive) {
        if (span != currentlyActive) {
            logger.warn("Deactivating a span ({}) which is not the currently active span ({}). " +
                "This can happen when not properly deactivating a previous span.", span, currentlyActive);

            if (assertionsEnabled) {
                throw new AssertionError();
            }
        }
    }

    public MetricRegistry getMetricRegistry() {
        return metricRegistry;
    }

    /**
     * Overrides the service name for all {@link Transaction}s,
     * {@link Span}s and {@link ErrorCapture}s which are created by the service which corresponds to the provided {@link ClassLoader}.
     * <p>
     * The main use case is being able to differentiate between multiple services deployed to the same application server.
     * </p>
     *
     * @param classLoader the class loader which corresponds to a particular service
     * @param serviceName the service name for this class loader
     */
    public void overrideServiceNameForClassLoader(@Nullable ClassLoader classLoader, @Nullable String serviceName) {
        // overriding the service name for the bootstrap class loader is not an actual use-case
        // null may also mean we don't know about the initiating class loader
        if (classLoader == null
            || serviceName == null || serviceName.isEmpty()
            // if the service name is set explicitly, don't override it
            || !coreConfiguration.getServiceNameConfig().isDefault()) {
            return;
        }
        if (!serviceNameByClassLoader.containsKey(classLoader)) {
            serviceNameByClassLoader.putIfAbsent(classLoader, ServiceNameUtil.replaceDisallowedChars(serviceName));
        }
    }

    @Nullable
    private String getServiceName(@Nullable ClassLoader initiatingClassLoader) {
        if (initiatingClassLoader == null) {
            return null;
        }
        return serviceNameByClassLoader.get(initiatingClassLoader);
    }

    public void resetServiceNameOverrides() {
        serviceNameByClassLoader.clear();
    }
}
