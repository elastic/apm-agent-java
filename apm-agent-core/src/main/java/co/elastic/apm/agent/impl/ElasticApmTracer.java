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
package co.elastic.apm.agent.impl;

import co.elastic.apm.agent.common.JvmRuntimeInfo;
import co.elastic.apm.agent.configuration.CoreConfiguration;
import co.elastic.apm.agent.configuration.ServiceNameUtil;
import co.elastic.apm.agent.context.ClosableLifecycleListenerAdapter;
import co.elastic.apm.agent.context.LifecycleListener;
import co.elastic.apm.agent.impl.error.ErrorCapture;
import co.elastic.apm.agent.impl.metadata.MetaDataFuture;
import co.elastic.apm.agent.impl.sampling.ProbabilitySampler;
import co.elastic.apm.agent.impl.sampling.Sampler;
import co.elastic.apm.agent.impl.stacktrace.StacktraceConfiguration;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.BinaryHeaderGetter;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.TextHeaderGetter;
import co.elastic.apm.agent.impl.transaction.TraceContext;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.matcher.WildcardMatcher;
import co.elastic.apm.agent.metrics.MetricRegistry;
import co.elastic.apm.agent.objectpool.ObjectPool;
import co.elastic.apm.agent.objectpool.ObjectPoolFactory;
import co.elastic.apm.agent.report.ApmServerClient;
import co.elastic.apm.agent.report.Reporter;
import co.elastic.apm.agent.report.ReporterConfiguration;
import co.elastic.apm.agent.sdk.weakconcurrent.WeakConcurrent;
import co.elastic.apm.agent.sdk.weakconcurrent.WeakMap;
import co.elastic.apm.agent.util.DependencyInjectingServiceLoader;
import co.elastic.apm.agent.util.ExecutorUtils;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;
import org.stagemonitor.configuration.ConfigurationOption;
import org.stagemonitor.configuration.ConfigurationOptionProvider;
import org.stagemonitor.configuration.ConfigurationRegistry;

import javax.annotation.Nullable;
import java.io.Closeable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;


/**
 * This is the tracer implementation which provides access to lower level agent functionality.
 * <p>
 * Note that this is a internal API, so there are no guarantees in terms of backwards compatibility.
 * </p>
 */
public class ElasticApmTracer implements Tracer {
    private static final Logger logger = LoggerFactory.getLogger(ElasticApmTracer.class);

    private static final WeakMap<ClassLoader, String> serviceNameByClassLoader = WeakConcurrent.buildMap();

    private final ConfigurationRegistry configurationRegistry;
    private final StacktraceConfiguration stacktraceConfiguration;
    private final ApmServerClient apmServerClient;
    private final List<LifecycleListener> lifecycleListeners = new CopyOnWriteArrayList<>();
    private final ObjectPool<Transaction> transactionPool;
    private final ObjectPool<Span> spanPool;
    private final ObjectPool<ErrorCapture> errorPool;
    private final Reporter reporter;
    private final ObjectPoolFactory objectPoolFactory;
    // Maintains a stack of all the activated spans
    // This way its easy to retrieve the bottom of the stack (the transaction)
    // Also, the caller does not have to keep a reference to the previously active span, as that is maintained by the stack
    private final ThreadLocal<Deque<AbstractSpan<?>>> activeStack = new ThreadLocal<Deque<AbstractSpan<?>>>() {
        @Override
        protected Deque<AbstractSpan<?>> initialValue() {
            return new ArrayDeque<AbstractSpan<?>>();
        }
    };

    private final CoreConfiguration coreConfiguration;
    private final List<ActivationListener> activationListeners;
    private final MetricRegistry metricRegistry;
    private final ScheduledThreadPoolExecutor sharedPool;
    private Sampler sampler;
    boolean assertionsEnabled = false;

    /**
     * The tracer state is volatile to ensure thread safety when queried through {@link ElasticApmTracer#isRunning()} or
     * {@link ElasticApmTracer#getState()}, or when updated through one of the lifecycle-effecting synchronized methods
     * {@link ElasticApmTracer#start(boolean)}}, {@link ElasticApmTracer#pause()}, {@link ElasticApmTracer#resume()} or
     * {@link ElasticApmTracer#stop()}.
     */
    private volatile TracerState tracerState = TracerState.UNINITIALIZED;
    private volatile boolean currentlyUnderStress = false;
    private volatile boolean recordingConfigOptionSet;
    private final String ephemeralId;
    private final MetaDataFuture metaDataFuture;

    ElasticApmTracer(ConfigurationRegistry configurationRegistry, Reporter reporter, ObjectPoolFactory poolFactory,
                     ApmServerClient apmServerClient, final String ephemeralId, MetaDataFuture metaDataFuture) {
        this.metricRegistry = new MetricRegistry(configurationRegistry.getConfig(ReporterConfiguration.class));
        this.configurationRegistry = configurationRegistry;
        this.reporter = reporter;
        this.stacktraceConfiguration = configurationRegistry.getConfig(StacktraceConfiguration.class);
        this.apmServerClient = apmServerClient;
        this.ephemeralId = ephemeralId;
        this.metaDataFuture = metaDataFuture;
        int maxPooledElements = configurationRegistry.getConfig(ReporterConfiguration.class).getMaxQueueSize() * 2;
        coreConfiguration = configurationRegistry.getConfig(CoreConfiguration.class);

        TracerConfiguration tracerConfiguration = configurationRegistry.getConfig(TracerConfiguration.class);
        recordingConfigOptionSet = tracerConfiguration.getRecordingConfig().get();
        tracerConfiguration.getRecordingConfig().addChangeListener(new ConfigurationOption.ChangeListener<Boolean>() {
            @Override
            public void onChange(ConfigurationOption<?> configurationOption, Boolean oldValue, Boolean newValue) {
                ElasticApmTracer.this.recordingConfigChanged(oldValue, newValue);
            }
        });

        this.objectPoolFactory = poolFactory;
        transactionPool = poolFactory.createTransactionPool(maxPooledElements, this);
        spanPool = poolFactory.createSpanPool(maxPooledElements, this);

        // we are assuming that we don't need as many errors as spans or transactions
        errorPool = poolFactory.createErrorPool(maxPooledElements / 2, this);

        sampler = ProbabilitySampler.of(coreConfiguration.getSampleRate().get());
        coreConfiguration.getSampleRate().addChangeListener(new ConfigurationOption.ChangeListener<Double>() {
            @Override
            public void onChange(ConfigurationOption<?> configurationOption, Double oldValue, Double newValue) {
                sampler = ProbabilitySampler.of(newValue);
            }
        });
        this.activationListeners = DependencyInjectingServiceLoader.load(ActivationListener.class, this);
        sharedPool = ExecutorUtils.createSingleThreadSchedulingDaemonPool("shared");

        // sets the assertionsEnabled flag to true if indeed enabled
        //noinspection AssertWithSideEffects
        assert assertionsEnabled = true;
    }

    @Override
    @Nullable
    public Transaction startRootTransaction(@Nullable ClassLoader initiatingClassLoader) {
        return startRootTransaction(sampler, -1, initiatingClassLoader);
    }

    @Override
    @Nullable
    public Transaction startRootTransaction(@Nullable ClassLoader initiatingClassLoader, long epochMicro) {
        return startRootTransaction(sampler, epochMicro, initiatingClassLoader);
    }

    @Override
    @Nullable
    public Transaction startRootTransaction(Sampler sampler, long epochMicros, @Nullable ClassLoader initiatingClassLoader) {
        Transaction transaction = null;
        if (isRunning()) {
            transaction = createTransaction().start(TraceContext.asRoot(), null, epochMicros, sampler, initiatingClassLoader);
            afterTransactionStart(initiatingClassLoader, transaction);
        }
        return transaction;
    }

    @Override
    @Nullable
    public <C> Transaction startChildTransaction(@Nullable C headerCarrier, TextHeaderGetter<C> textHeadersGetter, @Nullable ClassLoader initiatingClassLoader) {
        return startChildTransaction(headerCarrier, textHeadersGetter, sampler, -1, initiatingClassLoader);
    }

    @Override
    @Nullable
    public <C> Transaction startChildTransaction(@Nullable C headerCarrier, TextHeaderGetter<C> textHeadersGetter, @Nullable ClassLoader initiatingClassLoader, long epochMicros) {
        return startChildTransaction(headerCarrier, textHeadersGetter, sampler, epochMicros, initiatingClassLoader);
    }

    @Override
    @Nullable
    public <C> Transaction startChildTransaction(@Nullable C headerCarrier, TextHeaderGetter<C> textHeadersGetter, Sampler sampler,
                                                 long epochMicros, @Nullable ClassLoader initiatingClassLoader) {
        Transaction transaction = null;
        if (isRunning()) {
            transaction = createTransaction().start(TraceContext.<C>getFromTraceContextTextHeaders(), headerCarrier,
                textHeadersGetter, epochMicros, sampler, initiatingClassLoader);
            afterTransactionStart(initiatingClassLoader, transaction);
        }
        return transaction;
    }

    @Override
    @Nullable
    public <C> Transaction startChildTransaction(@Nullable C headerCarrier, BinaryHeaderGetter<C> binaryHeadersGetter, @Nullable ClassLoader initiatingClassLoader) {
        return startChildTransaction(headerCarrier, binaryHeadersGetter, sampler, -1, initiatingClassLoader);
    }

    @Override
    @Nullable
    public <C> Transaction startChildTransaction(@Nullable C headerCarrier, BinaryHeaderGetter<C> binaryHeadersGetter,
                                                 Sampler sampler, long epochMicros, @Nullable ClassLoader initiatingClassLoader) {
        Transaction transaction = null;
        if (isRunning()) {
            transaction = createTransaction().start(TraceContext.<C>getFromTraceContextBinaryHeaders(), headerCarrier,
                binaryHeadersGetter, epochMicros, sampler, initiatingClassLoader);
            afterTransactionStart(initiatingClassLoader, transaction);
        }
        return transaction;
    }

    private void afterTransactionStart(@Nullable ClassLoader initiatingClassLoader, Transaction transaction) {
        if (logger.isDebugEnabled()) {
            logger.debug("startTransaction {}", transaction);
            if (logger.isTraceEnabled()) {
                logger.trace("starting transaction at",
                    new RuntimeException("this exception is just used to record where the transaction has been started from"));
            }
        }
        final String serviceName = getServiceName(initiatingClassLoader);
        if (serviceName != null) {
            transaction.getTraceContext().setServiceName(serviceName);
        }
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

    @Override
    @Nullable
    public Transaction currentTransaction() {
        final AbstractSpan<?> bottomOfStack = activeStack.get().peekLast();
        return bottomOfStack != null ? bottomOfStack.getTransaction() : null;
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
        return createSpan().start(childContextCreator, parentContext, epochMicros);
    }

    private Span createSpan() {
        Span span = spanPool.createInstance();
        while (span.getReferenceCount() != 0) {
            logger.warn("Tried to start a span with a non-zero reference count {} {}", span.getReferenceCount(), span);
            span = spanPool.createInstance();
        }
        return span;
    }

    @Override
    public void captureAndReportException(@Nullable Throwable e, ClassLoader initiatingClassLoader) {
        ErrorCapture errorCapture = captureException(System.currentTimeMillis() * 1000, e, getActive(), initiatingClassLoader);
        if (errorCapture != null) {
            errorCapture.end();
        }
    }

    @Override
    @Nullable
    public String captureAndReportException(long epochMicros, @Nullable Throwable e, @Nullable AbstractSpan<?> parent) {
        String id = null;
        ErrorCapture errorCapture = captureException(epochMicros, e, parent, null);
        if (errorCapture != null) {
            id = errorCapture.getTraceContext().getId().toString();
            errorCapture.end();
        }
        return id;
    }

    @Override
    @Nullable
    public ErrorCapture captureException(@Nullable Throwable e, @Nullable AbstractSpan<?> parent, @Nullable ClassLoader initiatingClassLoader) {
        return captureException(System.currentTimeMillis() * 1000, e, parent, initiatingClassLoader);
    }

    @Nullable
    private ErrorCapture captureException(long epochMicros, @Nullable Throwable e, @Nullable AbstractSpan<?> parent, @Nullable ClassLoader initiatingClassLoader) {
        if (!isRunning()) {
            return null;
        }
        // note: if we add inheritance support for exception filtering, caching would be required for performance
        if (e != null && !WildcardMatcher.isAnyMatch(coreConfiguration.getIgnoreExceptions(), e.getClass().getName())) {
            ErrorCapture error = errorPool.createInstance();
            error.withTimestamp(epochMicros);
            error.setException(e);
            Transaction currentTransaction = currentTransaction();
            if (currentTransaction != null) {
                if (currentTransaction.getNameForSerialization().length() > 0) {
                    error.setTransactionName(currentTransaction.getNameForSerialization());
                }
                error.setTransactionType(currentTransaction.getType());
                error.setTransactionSampled(currentTransaction.isSampled());
            }
            if (parent != null) {
                error.asChildOf(parent);
                // don't discard spans leading up to an error, otherwise they'd point to an invalid parent
                parent.setNonDiscardable();
            } else {
                error.getTraceContext().getId().setToRandomValue();
                error.getTraceContext().setServiceName(getServiceName(initiatingClassLoader));
            }
            return error;
        }
        return null;
    }

    public ConfigurationRegistry getConfigurationRegistry() {
        return configurationRegistry;
    }

    public <T extends ConfigurationOptionProvider> T getConfig(Class<T> configProvider) {
        return configurationRegistry.getConfig(configProvider);
    }

    public void endTransaction(Transaction transaction) {
        if (logger.isDebugEnabled()) {
            logger.debug("endTransaction {}", transaction);
            if (logger.isTraceEnabled()) {
                logger.trace("ending transaction at",
                    new RuntimeException("this exception is just used to record where the transaction has been ended from"));
            }
        }
        if (!transaction.isNoop() &&
            (transaction.isSampled() || apmServerClient.supportsKeepingUnsampledTransaction())) {
            // we do report non-sampled transactions (without the context)
            reporter.report(transaction);
        } else {
            transaction.decrementReferences();
        }
    }

    public void endSpan(Span span) {
        if (!span.isSampled()) {
            span.decrementReferences();
            return;
        }
        if (span.getDuration() < coreConfiguration.getSpanMinDuration().getMillis() * 1000) {
            logger.debug("Span faster than span_min_duration. Request discarding {}", span);
            span.requestDiscarding();
        }
        if (span.isDiscarded()) {
            logger.debug("Discarding span {}", span);
            Transaction transaction = span.getTransaction();
            if (transaction != null) {
                transaction.getSpanCount().getDropped().incrementAndGet();
            }
            span.decrementReferences();
            return;
        }
        reportSpan(span);
    }

    private void reportSpan(Span span) {
        AbstractSpan<?> parent = span.getParent();
        if (parent != null && parent.isDiscarded()) {
            logger.warn("Reporting a child of an discarded span. The current span '{}' will not be shown in the UI. Consider deactivating span_min_duration.", span);
        }
        Transaction transaction = span.getTransaction();
        if (transaction != null) {
            transaction.getSpanCount().getReported().incrementAndGet();
        }
        // makes sure that parents are also non-discardable
        span.setNonDiscardable();

        long spanStackTraceMinDurationMs = stacktraceConfiguration.getSpanStackTraceMinDurationMs();
        if (spanStackTraceMinDurationMs >= 0 && span.isSampled() && span.getStackFrames() == null) {
            if (span.getDurationMs() >= spanStackTraceMinDurationMs) {
                span.withStacktrace(new Throwable());
            }
        }
        reporter.report(span);
    }

    public void endError(ErrorCapture error) {
        reporter.report(error);
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

    public synchronized void stop() {
        if (tracerState == TracerState.STOPPED) {
            // may happen if explicitly stopped in a unit test and executed again within a shutdown hook
            return;
        }
        for (LifecycleListener lifecycleListener : lifecycleListeners) {
            try {
                lifecycleListener.stop();
            } catch (Exception e) {
                logger.warn("Suppressed exception while calling stop()", e);
            }
        }
        ExecutorUtils.shutdownAndWaitTermination(sharedPool);
        tracerState = TracerState.STOPPED;
        logger.info("Tracer switched to STOPPED state");
        if (logger.isDebugEnabled()) {
            logger.debug("Tracer stop stack trace: ", new Throwable("Expected - for debugging purposes"));
        }

        try {
            configurationRegistry.close();
            reporter.close();
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

    public ObjectPoolFactory getObjectPoolFactory() {
        return objectPoolFactory;
    }

    @Override
    @Nullable
    public AbstractSpan<?> getActive() {
        return activeStack.get().peek();
    }

    @Nullable
    @Override
    public Span getActiveSpan() {
        final AbstractSpan<?> active = getActive();
        if (active instanceof Span) {
            return (Span) active;
        }
        return null;
    }

    @Nullable
    @Override
    public Span getActiveExitSpan() {
        final Span span = getActiveSpan();
        if (span != null && span.isExit()) {
            return span;
        }
        return null;
    }

    public void registerSpanListener(ActivationListener activationListener) {
        this.activationListeners.add(activationListener);
    }

    public List<ActivationListener> getActivationListeners() {
        return activationListeners;
    }

    /**
     * As opposed to {@link ElasticApmTracer#start(boolean)}, this method does not change the tracer's state and it's purpose
     * is to be called at JVM bootstrap.
     *
     * @param lifecycleListeners Lifecycle listeners
     */
    void init(List<LifecycleListener> lifecycleListeners) {
        this.lifecycleListeners.addAll(lifecycleListeners);
        for (LifecycleListener lifecycleListener : lifecycleListeners) {
            try {
                lifecycleListener.init(this);
            } catch (Exception e) {
                logger.error("Failed to init " + lifecycleListener.getClass().getName(), e);
            }
        }
    }

    /**
     * Starts the tracer. Depending on several environment and setup parameters, the tracer may be started synchronously
     * on this thread, or its start may be delayed and invoked on a dedicated thread.
     *
     * @param premain true if this tracer is attached through the {@code premain()} method (i.e. using the `javaagent`
     *                jvm parameter); false otherwise
     */
    public synchronized void start(boolean premain) {
        long delayInitMs = getConfig(CoreConfiguration.class).getDelayTracerStartMs();
        if (premain && shouldDelayOnPremain()) {
            delayInitMs = Math.max(delayInitMs, 5000L);
        }
        if (delayInitMs > 0) {
            startWithDelay(delayInitMs);
        } else {
            startSync();
        }
    }

    private boolean shouldDelayOnPremain() {
        return JvmRuntimeInfo.ofCurrentVM().getMajorVersion() <= 8 &&
            ClassLoader.getSystemClassLoader().getResource("org/apache/catalina/startup/Bootstrap.class") != null;
    }

    private synchronized void startWithDelay(final long delayInitMs) {
        ThreadPoolExecutor pool = ExecutorUtils.createSingleThreadDaemonPool("tracer-initializer", 1);
        pool.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    logger.info("Delaying initialization of tracer for " + delayInitMs + "ms");
                    Thread.sleep(delayInitMs);
                    logger.info("end wait");
                } catch (InterruptedException e) {
                    logger.error(e.getMessage(), e);
                } finally {
                    ElasticApmTracer.this.startSync();
                }
            }
        });
        pool.shutdown();
    }

    private synchronized void startSync() {
        if (tracerState != TracerState.UNINITIALIZED) {
            logger.warn("Trying to start an already initialized agent");
            return;
        }
        apmServerClient.start();
        reporter.start();
        for (LifecycleListener lifecycleListener : lifecycleListeners) {
            try {
                lifecycleListener.start(this);
            } catch (Exception e) {
                logger.error("Failed to start " + lifecycleListener.getClass().getName(), e);
            }
        }
        tracerState = TracerState.RUNNING;
        if (recordingConfigOptionSet) {
            logger.info("Tracer switched to RUNNING state");
        } else {
            pause();
        }
    }

    public synchronized void onStressDetected() {
        currentlyUnderStress = true;
        if (tracerState == TracerState.RUNNING) {
            pause();
        }
    }

    public synchronized void onStressRelieved() {
        currentlyUnderStress = false;
        if (tracerState == TracerState.PAUSED && recordingConfigOptionSet) {
            resume();
        }
    }

    private synchronized void recordingConfigChanged(boolean oldValue, boolean newValue) {
        // if changed from true to false then:
        //      if current state is RUNNING - pause the agent
        //      otherwise - ignore
        // if changed from false to true then:
        //      if current state is RUNNING or STOPPED - no effect
        //      if current state is PAUSED and currentlyUnderStress==false - then resume
        if (oldValue && !newValue && tracerState == TracerState.RUNNING) {
            pause();
        } else if (!oldValue && newValue && tracerState == TracerState.PAUSED && !currentlyUnderStress) {
            resume();
        }
        recordingConfigOptionSet = newValue;
    }

    synchronized void pause() {
        if (tracerState != TracerState.RUNNING) {
            logger.warn("Attempting to pause the agent when it is already in a {} state", tracerState);
            return;
        }
        tracerState = TracerState.PAUSED;
        logger.info("Tracer switched to PAUSED state");
        for (LifecycleListener lifecycleListener : lifecycleListeners) {
            try {
                lifecycleListener.pause();
            } catch (Exception e) {
                logger.warn("Suppressed exception while calling pause()", e);
            }
        }
    }

    synchronized void resume() {
        if (tracerState != TracerState.PAUSED) {
            logger.warn("Attempting to resume the agent when it is in a {} state", tracerState);
            return;
        }
        for (LifecycleListener lifecycleListener : lifecycleListeners) {
            try {
                lifecycleListener.resume();
            } catch (Exception e) {
                logger.warn("Suppressed exception while calling resume()", e);
            }
        }
        tracerState = TracerState.RUNNING;
        logger.info("Tracer switched to RUNNING state");
    }

    @Override
    public boolean isRunning() {
        return tracerState == TracerState.RUNNING;
    }

    @Override
    @Nullable
    public Span createExitChildSpan() {
        AbstractSpan<?> active = getActive();
        if (active == null) {
            return null;
        }
        return active.createExitSpan();
    }

    @Override
    public TracerState getState() {
        return tracerState;
    }

    @Nullable
    public <T> T getLifecycleListener(Class<T> listenerClass) {
        for (LifecycleListener lifecycleListener : lifecycleListeners) {
            if (listenerClass.isInstance(lifecycleListener)) {
                return (T) lifecycleListener;
            }
        }
        return null;
    }

    public void activate(AbstractSpan<?> span) {
        if (logger.isDebugEnabled()) {
            logger.debug("Activating {} on thread {}", span, Thread.currentThread().getId());
        }
        span.incrementReferences();
        List<ActivationListener> activationListeners = getActivationListeners();
        for (int i = 0, size = activationListeners.size(); i < size; i++) {
            try {
                activationListeners.get(i).beforeActivate(span);
            } catch (Error e) {
                throw e;
            } catch (Throwable t) {
                logger.warn("Exception while calling {}#beforeActivate", activationListeners.get(i).getClass().getSimpleName(), t);
            }
        }
        activeStack.get().push(span);
    }

    public void deactivate(AbstractSpan<?> span) {
        if (logger.isDebugEnabled()) {
            logger.debug("Deactivating {} on thread {}", span, Thread.currentThread().getId());
        }
        try {
            final Deque<AbstractSpan<?>> stack = activeStack.get();
            assertIsActive(span, stack.poll());
            List<ActivationListener> activationListeners = getActivationListeners();
            for (int i = 0, size = activationListeners.size(); i < size; i++) {
                try {
                    // `this` is guaranteed to not be recycled yet as the reference count is only decremented after this method has executed
                    activationListeners.get(i).afterDeactivate(span);
                } catch (Error e) {
                    throw e;
                } catch (Throwable t) {
                    logger.warn("Exception while calling {}#afterDeactivate", activationListeners.get(i).getClass().getSimpleName(), t);
                }
            }
        } finally {
            span.decrementReferences();
        }
    }

    private void assertIsActive(AbstractSpan<?> span, @Nullable AbstractSpan<?> currentlyActive) {
        if (span != currentlyActive) {
            logger.warn("Deactivating a span ({}) which is not the currently active span ({}). " +
                "This can happen when not properly deactivating a previous span.", span, currentlyActive);

            if (assertionsEnabled) {
                throw new AssertionError("Deactivating a span that is not the active one");
            }
        }
    }

    public MetricRegistry getMetricRegistry() {
        return metricRegistry;
    }

    public List<String> getServiceNameOverrides() {
        List<String> serviceNames = new ArrayList<>(serviceNameByClassLoader.approximateSize());
        for (Map.Entry<ClassLoader, String> entry : serviceNameByClassLoader) {
            serviceNames.add(entry.getValue());
        }
        return serviceNames;
    }

    @Override
    public void overrideServiceNameForClassLoader(@Nullable ClassLoader classLoader, @Nullable String serviceName) {
        // overriding the service name for the bootstrap class loader is not an actual use-case
        // null may also mean we don't know about the initiating class loader
        if (classLoader == null
            || serviceName == null || serviceName.isEmpty()
            // if the service name is set explicitly, don't override it
            || coreConfiguration.getServiceNameConfig().getUsedKey() != null) {
            return;
        }

        String sanitizedServiceName = ServiceNameUtil.replaceDisallowedChars(serviceName);
        logger.debug("Using `{}` as the service name for class loader [{}]", sanitizedServiceName, classLoader);
        if (!serviceNameByClassLoader.containsKey(classLoader)) {
            serviceNameByClassLoader.putIfAbsent(classLoader, sanitizedServiceName);
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

    public ApmServerClient getApmServerClient() {
        return apmServerClient;
    }

    public String getEphemeralId() {
        return ephemeralId;
    }

    public MetaDataFuture getMetaDataFuture() {
        return metaDataFuture;
    }

    public ScheduledThreadPoolExecutor getSharedSingleThreadedPool() {
        return sharedPool;
    }

    public void addShutdownHook(Closeable closeable) {
        lifecycleListeners.add(ClosableLifecycleListenerAdapter.of(closeable));
    }
}
