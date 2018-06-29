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
import co.elastic.apm.impl.stacktrace.Stacktrace;
import co.elastic.apm.impl.stacktrace.StacktraceConfiguration;
import co.elastic.apm.impl.stacktrace.StacktraceFactory;
import co.elastic.apm.impl.transaction.Span;
import co.elastic.apm.impl.transaction.Transaction;
import co.elastic.apm.objectpool.NoopObjectPool;
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
    private final ObjectPool<Stacktrace> stackTracePool;
    private final ObjectPool<ErrorCapture> errorPool;
    private final Reporter reporter;
    private final StacktraceFactory stacktraceFactory;
    private final ThreadLocal<Transaction> currentTransaction = new ThreadLocal<>();
    private final ThreadLocal<Span> currentSpan = new ThreadLocal<>();
    private final CoreConfiguration coreConfiguration;
    private Sampler sampler;

    ElasticApmTracer(ConfigurationRegistry configurationRegistry, Reporter reporter, StacktraceFactory stacktraceFactory, Iterable<LifecycleListener> lifecycleListeners) {
        this.configurationRegistry = configurationRegistry;
        this.reporter = reporter;
        this.stacktraceFactory = stacktraceFactory;
        this.stacktraceConfiguration = configurationRegistry.getConfig(StacktraceConfiguration.class);
        this.lifecycleListeners = lifecycleListeners;
        int maxPooledElements = configurationRegistry.getConfig(ReporterConfiguration.class).getMaxQueueSize() * 2;
        transactionPool = new QueueBasedObjectPool<>(AtomicQueueFactory.<Transaction>newQueue(createBoundedMpmc(maxPooledElements)),false,
            new RecyclableObjectFactory<Transaction>() {
                @Override
                public Transaction createInstance() {
                    return new Transaction();
                }
            });
        spanPool = new QueueBasedObjectPool<>(AtomicQueueFactory.<Span>newQueue(createBoundedMpmc(maxPooledElements)), false,
            new RecyclableObjectFactory<Span>() {
                @Override
                public Span createInstance() {
                    return new Span();
                }
            });
        errorPool = new QueueBasedObjectPool<>(AtomicQueueFactory.<ErrorCapture>newQueue(createBoundedMpmc(maxPooledElements)), false,
            new RecyclableObjectFactory<ErrorCapture>() {
                @Override
                public ErrorCapture createInstance() {
                    return new ErrorCapture();
                }
            });
        stackTracePool = new NoopObjectPool<Stacktrace>(new RecyclableObjectFactory<Stacktrace>() {
            @Override
            public Stacktrace createInstance() {
                return new Stacktrace();
            }
        });
        coreConfiguration = configurationRegistry.getConfig(CoreConfiguration.class);
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
    }

    public Transaction startTransaction() {
        return startTransaction(null);
    }

    public Transaction startTransaction(@Nullable String traceContextHeader) {
        Transaction transaction = startManualTransaction(traceContextHeader, sampler, System.nanoTime());
        activate(transaction);
        return transaction;
    }

    public Transaction startManualTransaction(@Nullable String traceContextHeader, Sampler sampler, long nanoTime) {
        Transaction transaction;
        if (!coreConfiguration.isActive()) {
            transaction = noopTransaction();
        } else {
            transaction = transactionPool.createInstance().start(this, traceContextHeader, nanoTime, sampler);
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
        return transactionPool.createInstance().startNoop(this);
    }

    public void activate(Transaction transaction) {
        currentTransaction.set(transaction);
    }

    @Nullable
    public Transaction currentTransaction() {
        return currentTransaction.get();
    }

    @Nullable
    public Span currentSpan() {
        return currentSpan.get();
    }

    @Nullable
    public Span startSpan() {
        Transaction transaction = currentTransaction();
        final Span span = startManualSpan(transaction, currentSpan(), System.nanoTime());
        if (span != null) {
            activate(span);
        }
        return span;
    }

    @Nullable
    public Span startManualSpan(@Nullable Transaction transaction, @Nullable Span parentSpan, long nanoTime) {
        final Span span;
        // makes sure that the active setting is consistent during a transaction
        // even when setting active=false mid-transaction
        if (transaction == null || transaction.isNoop()) {
            return null;
        } else {
            span = createRealSpan(transaction, parentSpan, nanoTime);
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

    public void activate(Span span) {
        currentSpan.set(span);
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
        span.start(this, transaction, parentSpan, nanoTime, dropped);
        return span;
    }

    private boolean isTransactionSpanLimitReached(Transaction transaction) {
        return coreConfiguration.getTransactionMaxSpans() <= transaction.getSpanCount().getTotal();
    }

    public void captureException(@Nullable Exception e) {
        captureException(System.currentTimeMillis(), e);
    }

    public void captureException(long epochTimestampMillis, @Nullable Exception e) {
        if (e != null) {
            ErrorCapture error = new ErrorCapture();
            error.withTimestamp(epochTimestampMillis);
            error.getException().withMessage(e.getMessage());
            error.getException().withType(e.getClass().getName());
            stacktraceFactory.fillStackTrace(error.getException().getStacktrace(), e.getStackTrace());
            Transaction transaction = currentTransaction();
            if (transaction != null) {
                error.asChildOf(transaction);
                error.getTransaction().getTransactionId().copyFrom(transaction.getId());
                error.getContext().copyFrom(transaction.getContext());
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
    public void endTransaction(Transaction transaction, boolean releaseActiveTransaction) {
        if (logger.isDebugEnabled()) {
            logger.debug("} endTransaction {}", transaction);
            if (logger.isTraceEnabled()) {
                logger.trace("ending transaction at",
                    new RuntimeException("this exception is just used to record where the transaction has been ended from"));
            }
        }
        if (releaseActiveTransaction) {
            if (currentTransaction.get() != null && currentTransaction.get() != transaction) {
                logger.warn("Trying to end a transaction which is not the current (thread local) transaction!");
                assert false;
            }
            releaseActiveTransaction();
        }
        if (!transaction.isNoop()) {
            reporter.report(transaction);
        } else {
            transaction.recycle();
        }
    }

    @SuppressWarnings("ReferenceEquality")
    public void endSpan(Span span, boolean releaseActiveSpan) {
        if (logger.isDebugEnabled()) {
            logger.debug("} endSpan {}", span.toString());
            if (logger.isTraceEnabled()) {
                logger.trace("ending span at", new RuntimeException("this exception is just used to record where the span has been ended from"));
            }
        }
        if (releaseActiveSpan) {
            if (currentSpan.get() != null && currentSpan.get() != span) {
                logger.warn("Trying to end a span which is not the current (thread local) span!");
                assert false;
            }
            releaseActiveSpan();
        }
        int spanFramesMinDurationMs = stacktraceConfiguration.getSpanFramesMinDurationMs();
        if (spanFramesMinDurationMs != 0 && span.isSampled()) {
            if (span.getDuration() >= spanFramesMinDurationMs) {
                stacktraceFactory.fillStackTrace(span.getStacktrace());
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
        List<Stacktrace> stacktrace = span.getStacktrace();
        for (int i = 0; i < stacktrace.size(); i++) {
            stackTracePool.recycle(stacktrace.get(i));
        }
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
            stackTracePool.close();
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

    public void releaseActiveTransaction() {
        currentTransaction.set(null);
    }

    public void releaseActiveSpan() {
        currentSpan.set(null);
    }
}
