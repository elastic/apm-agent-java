/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 Elastic and contributors
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
package co.elastic.apm.impl;

import co.elastic.apm.configuration.CoreConfiguration;
import co.elastic.apm.context.LifecycleListener;
import co.elastic.apm.impl.error.ErrorCapture;
import co.elastic.apm.impl.sampling.ProbabilitySampler;
import co.elastic.apm.impl.sampling.Sampler;
import co.elastic.apm.impl.stacktrace.StacktraceConfiguration;
import co.elastic.apm.impl.transaction.AbstractSpan;
import co.elastic.apm.impl.transaction.Span;
import co.elastic.apm.impl.transaction.Transaction;
import co.elastic.apm.objectpool.ObjectPool;
import co.elastic.apm.objectpool.RecyclableObjectFactory;
import co.elastic.apm.objectpool.impl.QueueBasedObjectPool;
import co.elastic.apm.report.Reporter;
import co.elastic.apm.report.ReporterConfiguration;
import org.jctools.queues.atomic.AtomicQueueFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.stagemonitor.configuration.ConfigurationOption;
import org.stagemonitor.configuration.ConfigurationOptionProvider;
import org.stagemonitor.configuration.ConfigurationRegistry;

import javax.annotation.Nullable;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.jctools.queues.spec.ConcurrentQueueSpec.createBoundedMpmc;

/**
 * This is the tracer implementation which provides access to lower level agent functionality.
 * <p>
 * Note that this is a internal API, so there are no guarantees in terms of backwards compatibility.
 * </p>
 */
public class ElasticApmTracer {
    public static final double MS_IN_NANOS = TimeUnit.MILLISECONDS.toNanos(1);
    private static final Logger logger = LoggerFactory.getLogger(ElasticApmTracer.class);

    private final ConfigurationRegistry configurationRegistry;
    private final StacktraceConfiguration stacktraceConfiguration;
    private final Iterable<LifecycleListener> lifecycleListeners;
    private final ObjectPool<Transaction> transactionPool;
    private final ObjectPool<Span> spanPool;
    private final ObjectPool<ErrorCapture> errorPool;
    private final Reporter reporter;
    private final ThreadLocal<AbstractSpan> active = new ThreadLocal<>();
    private final CoreConfiguration coreConfiguration;
    private final List<SpanListener> spanListeners;
    private Sampler sampler;

    ElasticApmTracer(ConfigurationRegistry configurationRegistry, Reporter reporter, Iterable<LifecycleListener> lifecycleListeners, List<SpanListener> spanListeners) {
        this.configurationRegistry = configurationRegistry;
        this.reporter = reporter;
        this.stacktraceConfiguration = configurationRegistry.getConfig(StacktraceConfiguration.class);
        this.lifecycleListeners = lifecycleListeners;
        this.spanListeners = spanListeners;
        int maxPooledElements = configurationRegistry.getConfig(ReporterConfiguration.class).getMaxQueueSize() * 2;
        coreConfiguration = configurationRegistry.getConfig(CoreConfiguration.class);
        transactionPool = new QueueBasedObjectPool<>(AtomicQueueFactory.<Transaction>newQueue(createBoundedMpmc(maxPooledElements)), false,
            new RecyclableObjectFactory<Transaction>() {
                @Override
                public Transaction createInstance() {
                    return new Transaction(ElasticApmTracer.this);
                }
            });
        spanPool = new QueueBasedObjectPool<>(AtomicQueueFactory.<Span>newQueue(createBoundedMpmc(maxPooledElements)), false,
            new RecyclableObjectFactory<Span>() {
                @Override
                public Span createInstance() {
                    return new Span(ElasticApmTracer.this);
                }
            });
        // we are assuming that we don't need as many errors as spans or transactions
        errorPool = new QueueBasedObjectPool<>(AtomicQueueFactory.<ErrorCapture>newQueue(createBoundedMpmc(maxPooledElements / 2)), false,
            new RecyclableObjectFactory<ErrorCapture>() {
                @Override
                public ErrorCapture createInstance() {
                    return new ErrorCapture();
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
    }

    public Transaction startTransaction() {
        return startTransaction(null);
    }

    public Transaction startTransaction(@Nullable String traceContextHeader) {
        return startTransaction(traceContextHeader, sampler, System.nanoTime());
    }

    public Transaction startTransaction(@Nullable String traceContextHeader, Sampler sampler, long nanoTime) {
        Transaction transaction;
        if (!coreConfiguration.isActive()) {
            transaction = noopTransaction();
        } else {
            transaction = transactionPool.createInstance().start(traceContextHeader, nanoTime, sampler);
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
        final AbstractSpan<?> activeSpan = active.get();
        if (activeSpan != null) {
            return activeSpan.getTransaction();
        }
        return null;
    }

    @Nullable
    public Span currentSpan() {
        final AbstractSpan<?> abstractSpan = active.get();
        if (abstractSpan instanceof Span) {
            return (Span) abstractSpan;
        }
        return null;
    }

    public Span startSpan(AbstractSpan<?> parent, long startTimeNanos) {
        Span parentSpan = null;
        if (parent instanceof Span) {
            parentSpan = (Span) parent;
        }
        return startSpan(parent.getTransaction(), parentSpan, startTimeNanos);
    }

    private Span startSpan(@Nullable Transaction transaction, @Nullable Span parentSpan, long startTimeNanos) {
        final Span span;
        // makes sure that the active setting is consistent during a transaction
        // even when setting active=false mid-transaction
        if (transaction == null || transaction.isNoop()) {
            return createNoopSpan();
        } else {
            span = createRealSpan(transaction, parentSpan, startTimeNanos);
        }
        if (logger.isDebugEnabled()) {
            logger.debug("startSpan {} {", span);
            if (logger.isTraceEnabled()) {
                logger.trace("starting span at",
                    new RuntimeException("this exception is just used to record where the span has been started from"));
            }
        }
        return span;
    }

    private Span createNoopSpan() {
        return spanPool.createInstance().startNoop();
    }

    private Span createRealSpan(Transaction transaction, @Nullable Span parentSpan, long nanoTime) {
        Span span;
        span = spanPool.createInstance();
        final boolean dropped;
        if (isTransactionSpanLimitReached(transaction)) {
            // TODO only drop leaf spans
            dropped = true;
            transaction.getSpanCount().getDropped().increment();
        } else {
            dropped = false;
        }
        transaction.getSpanCount().increment();
        span.start(transaction, parentSpan, nanoTime, dropped);
        return span;
    }

    private boolean isTransactionSpanLimitReached(Transaction transaction) {
        return coreConfiguration.getTransactionMaxSpans() <= transaction.getSpanCount().getTotal();
    }

    public void captureException(@Nullable Throwable e) {
        captureException(System.currentTimeMillis(), e, getActive());
    }

    public void captureException(long epochTimestampMillis, @Nullable Throwable e, @Nullable AbstractSpan<?> active) {
        if (e != null) {
            ErrorCapture error = new ErrorCapture();
            error.withTimestamp(epochTimestampMillis);
            error.setException(e);
            if (active != null) {
                if (active instanceof Transaction) {
                    Transaction transaction = (Transaction) active;
                    // The error might have occurred in a different thread than the one the transaction was recorded
                    // That's why we have to ensure the visibility of the transaction properties
                    error.getContext().copyFrom(transaction.getContextEnsureVisibility());
                    error.getTransaction().getTransactionId().copyFrom(transaction.getId());
                }
                error.asChildOf(active);
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
            reporter.report(transaction);
        } else {
            transaction.recycle();
        }
    }

    @SuppressWarnings("ReferenceEquality")
    public void endSpan(Span span) {
        if (logger.isDebugEnabled()) {
            logger.debug("} endSpan {}", span.toString());
            if (logger.isTraceEnabled()) {
                logger.trace("ending span at", new RuntimeException("this exception is just used to record where the span has been ended from"));
            }
        }
        long spanFramesMinDurationMs = stacktraceConfiguration.getSpanFramesMinDurationMs();
        if (spanFramesMinDurationMs != 0 && span.isSampled()) {
            if (span.getDuration() >= spanFramesMinDurationMs) {
                span.withStacktrace(new Throwable());
            }
        }
        if (span.isSampled()) {
            reporter.report(span);
        } else {
            span.recycle();
        }
    }

    public void recycle(Transaction transaction) {
        List<Span> spans = transaction.getSpans();
        for (int i = 0; i < spans.size(); i++) {
            recycle(spans.get(i));
        }
        transactionPool.recycle(transaction);
    }

    public void recycle(Span span) {
        spanPool.recycle(span);
    }

    public void recycle(ErrorCapture error) {
        errorPool.recycle(error);
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
    public AbstractSpan<?> getActive() {
        return active.get();
    }

    public void setActive(@Nullable AbstractSpan<?> span) {
        active.set(span);
    }

    public void registerSpanListener(SpanListener spanListener) {
        this.spanListeners.add(spanListener);
    }

    public List<SpanListener> getSpanListeners() {
        return spanListeners;
    }
}
