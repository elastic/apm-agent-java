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
import co.elastic.apm.agent.context.LifecycleListener;
import co.elastic.apm.agent.impl.error.ErrorCapture;
import co.elastic.apm.agent.impl.sampling.ProbabilitySampler;
import co.elastic.apm.agent.impl.sampling.Sampler;
import co.elastic.apm.agent.impl.stacktrace.StacktraceConfiguration;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.TraceContext;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.metrics.MetricRegistry;
import co.elastic.apm.agent.objectpool.Allocator;
import co.elastic.apm.agent.objectpool.ObjectPool;
import co.elastic.apm.agent.objectpool.impl.QueueBasedObjectPool;
import co.elastic.apm.agent.report.Reporter;
import co.elastic.apm.agent.report.ReporterConfiguration;
import org.jctools.queues.atomic.AtomicQueueFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.stagemonitor.configuration.ConfigurationOption;
import org.stagemonitor.configuration.ConfigurationOptionProvider;
import org.stagemonitor.configuration.ConfigurationRegistry;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import static org.jctools.queues.spec.ConcurrentQueueSpec.createBoundedMpmc;

/**
 * This is the tracer implementation which provides access to lower level agent functionality.
 * <p>
 * Note that this is a internal API, so there are no guarantees in terms of backwards compatibility.
 * </p>
 */
public class ElasticApmTracer {
    private static final Logger logger = LoggerFactory.getLogger(ElasticApmTracer.class);

    private final ConfigurationRegistry configurationRegistry;
    private final StacktraceConfiguration stacktraceConfiguration;
    private final Iterable<LifecycleListener> lifecycleListeners;
    private final ObjectPool<Transaction> transactionPool;
    private final ObjectPool<Span> spanPool;
    private final ObjectPool<ErrorCapture> errorPool;
    private final ObjectPool<InScopeRunnableWrapper> runnableWrapperObjectPool;
    private final Reporter reporter;
    // Maintains a stack of all the activated spans
    // This way its easy to retrieve the bottom of the stack (the transaction)
    // Also, the caller does not have to keep a reference to the previously active span, as that is maintained by the stack
    private final ThreadLocal<Deque<Object>> activeStack = new ThreadLocal<Deque<Object>>() {
        @Override
        protected Deque<Object> initialValue() {
            return new ArrayDeque<Object>();
        }
    };
    private final CoreConfiguration coreConfiguration;
    private final List<SpanListener> spanListeners;
    private final MetricRegistry metricRegistry = new MetricRegistry();
    private Sampler sampler;

    ElasticApmTracer(ConfigurationRegistry configurationRegistry, Reporter reporter, Iterable<LifecycleListener> lifecycleListeners, List<SpanListener> spanListeners) {
        this.configurationRegistry = configurationRegistry;
        this.reporter = reporter;
        this.stacktraceConfiguration = configurationRegistry.getConfig(StacktraceConfiguration.class);
        this.lifecycleListeners = lifecycleListeners;
        this.spanListeners = spanListeners;
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
        runnableWrapperObjectPool = QueueBasedObjectPool.ofRecyclable(AtomicQueueFactory.<InScopeRunnableWrapper>newQueue(createBoundedMpmc(maxPooledElements)), false,
            new Allocator<InScopeRunnableWrapper>() {
                @Override
                public InScopeRunnableWrapper createInstance() {
                    return new InScopeRunnableWrapper(ElasticApmTracer.this);
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
        for (SpanListener spanListener : spanListeners) {
            spanListener.init(this);
        }
        reporter.scheduleMetricReporting(metricRegistry, configurationRegistry.getConfig(ReporterConfiguration.class).getMetricsIntervalMs());
    }

    public Transaction startTransaction() {
        return startTransaction(TraceContext.asRoot(), null);
    }

    public <T> Transaction startTransaction(TraceContext.ChildContextCreator<T> childContextCreator, @Nullable T parent) {
        return startTransaction(childContextCreator, parent, sampler, -1);
    }

    public <T> Transaction startTransaction(TraceContext.ChildContextCreator<T> childContextCreator, @Nullable T parent, Sampler sampler, long epochMicros) {
        Transaction transaction;
        if (!coreConfiguration.isActive()) {
            transaction = noopTransaction();
        } else {
            transaction = transactionPool.createInstance().start(childContextCreator, parent, epochMicros, sampler);
        }
        if (logger.isDebugEnabled()) {
            logger.debug("startTransaction {} {", transaction);
            if (logger.isTraceEnabled()) {
                logger.trace("starting transaction at",
                    new RuntimeException("this exception is just used to record where the transaction has been started from"));
            }
        }
        return transaction;
    }

    public Transaction noopTransaction() {
        return transactionPool.createInstance().startNoop();
    }

    @Nullable
    public Transaction currentTransaction() {
        final Object bottomOfStack = activeStack.get().peekFirst();
        if (bottomOfStack instanceof Transaction) {
            return (Transaction) bottomOfStack;
        }
        return null;
    }

    @Nullable
    public Span currentSpan() {
        final AbstractSpan<?> abstractSpan = activeSpan();
        if (abstractSpan instanceof Span) {
            return (Span) abstractSpan;
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
        return spanPool.createInstance().start(childContextCreator, parentContext);
    }

    /**
     * @param parentContext the trace context of the parent
     * @param epochMicros   the start timestamp of the span in microseconds after epoch
     * @return a new started span
     * @see #startSpan(TraceContext.ChildContextCreator, Object)
     */
    public <T> Span startSpan(TraceContext.ChildContextCreator<T> childContextCreator, T parentContext, long epochMicros) {
        return spanPool.createInstance().start(childContextCreator, parentContext, epochMicros);
    }

    public Span startSpan(AbstractSpan<?> parent, long epochMicros) {
        Span span;
        span = spanPool.createInstance();
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
        span.start(TraceContext.fromParentSpan(), parent, epochMicros, dropped);
        return span;
    }

    private boolean isTransactionSpanLimitReached(Transaction transaction) {
        return coreConfiguration.getTransactionMaxSpans() <= transaction.getSpanCount().getStarted().get();
    }

    public void captureException(@Nullable Throwable e) {
        captureException(System.currentTimeMillis() * 1000, e, activeSpan());
    }

    public void captureException(long epochMicros, @Nullable Throwable e, @Nullable AbstractSpan<?> active) {
        if (e != null) {
            ErrorCapture error = errorPool.createInstance();
            error.withTimestamp(epochMicros);
            error.setException(e);
            if (active != null) {
                if (active instanceof Transaction) {
                    Transaction transaction = (Transaction) active;
                    // The error might have occurred in a different thread than the one the transaction was recorded
                    // That's why we have to ensure the visibility of the transaction properties
                    error.getContext().copyFrom(transaction.getContextEnsureVisibility());
                }
                else if (active instanceof Span) {
                    Span span = (Span) active;
                    error.getContext().getTags().putAll(span.getContext().getTags());
                }
                error.asChildOf(active);
                error.setTransactionSampled(active.isSampled());
            } else {
                error.getTraceContext().getId().setToRandomValue();
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
            transaction.recycle();
        }
    }

    @SuppressWarnings("ReferenceEquality")
    public void endSpan(Span span) {
        if (span.isSampled()) {
            long spanFramesMinDurationMs = stacktraceConfiguration.getSpanFramesMinDurationMs();
            if (spanFramesMinDurationMs != 0 && span.isSampled()) {
                if (span.getDuration() >= spanFramesMinDurationMs) {
                    span.withStacktrace(new Throwable());
                }
            }
            reporter.report(span);
        } else {
            span.recycle();
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
        return runnableWrapperObjectPool.createInstance().wrap(delegate, span);
    }

    public void recycle(InScopeRunnableWrapper wrapper) {
        runnableWrapperObjectPool.recycle(wrapper);
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
    public AbstractSpan<?> activeSpan() {
        final Object active = getActive();
        if (active instanceof AbstractSpan) {
            return (AbstractSpan<?>) active;
        }
        return null;
    }

    @Nullable
    public TraceContext activeTraceContext() {
        final Object active = getActive();
        if (active instanceof TraceContext) {
            return (TraceContext) active;
        }
        return null;
    }

    @Nullable
    public Object getActive() {
        return activeStack.get().peek();
    }

    public void registerSpanListener(SpanListener spanListener) {
        this.spanListeners.add(spanListener);
    }

    public List<SpanListener> getSpanListeners() {
        return spanListeners;
    }

    public void activate(AbstractSpan<?> span) {
        if (logger.isDebugEnabled()) {
            logger.debug("Activating {} on thread {}", span, Thread.currentThread().getId());
        }
        activeStack.get().push(span);
    }

    public void activate(TraceContext traceContext) {
        if (logger.isDebugEnabled()) {
            logger.debug("Activating trace context {} on thread {}", traceContext, Thread.currentThread().getId());
        }
        activeStack.get().push(traceContext);
    }

    public void deactivate(AbstractSpan<?> span) {
        if (logger.isDebugEnabled()) {
            logger.debug("Deactivating {} on thread {}", span, Thread.currentThread().getId());
        }
        assertIsActive(span, activeStack.get().poll());
        if (span instanceof Transaction) {
            // a transaction is always the bottom of this stack
            // clearing to avoid potential leaks in case of wrong api usage
            activeStack.get().clear();
        }
    }

    public void deactivate(TraceContext traceContext) {
        if (logger.isDebugEnabled()) {
            logger.debug("Deactivating trace context {} on thread {}",
                traceContext, Thread.currentThread().getId());
        }
        assertIsActive(traceContext, activeStack.get().poll());
    }

    private void assertIsActive(Object span, @Nullable Object currentlyActive) {
        if (span != currentlyActive) {
            logger.warn("Deactivating a span ({}) which is not the currently active span ({}). " +
                "This can happen when not properly deactivating a previous span.", span, currentlyActive);
        }
        assert span == currentlyActive;
    }

    public MetricRegistry getMetricRegistry() {
        return metricRegistry;
    }
}
