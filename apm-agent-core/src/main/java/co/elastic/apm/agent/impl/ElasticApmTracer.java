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
import co.elastic.apm.agent.common.util.WildcardMatcher;
import co.elastic.apm.agent.configuration.CoreConfiguration;
import co.elastic.apm.agent.configuration.ServiceInfo;
import co.elastic.apm.agent.configuration.SpanConfiguration;
import co.elastic.apm.agent.context.ClosableLifecycleListenerAdapter;
import co.elastic.apm.agent.context.LifecycleListener;
import co.elastic.apm.agent.impl.error.ErrorCapture;
import co.elastic.apm.agent.impl.metadata.MetaDataFuture;
import co.elastic.apm.agent.impl.sampling.ProbabilitySampler;
import co.elastic.apm.agent.impl.sampling.Sampler;
import co.elastic.apm.agent.impl.stacktrace.StacktraceConfiguration;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.ElasticContext;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.logging.LoggingConfiguration;
import co.elastic.apm.agent.metrics.MetricRegistry;
import co.elastic.apm.agent.objectpool.ObjectPoolFactory;
import co.elastic.apm.agent.report.ApmServerClient;
import co.elastic.apm.agent.report.Reporter;
import co.elastic.apm.agent.report.ReporterConfiguration;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;
import co.elastic.apm.agent.sdk.weakconcurrent.WeakConcurrent;
import co.elastic.apm.agent.sdk.weakconcurrent.WeakMap;
import co.elastic.apm.agent.util.DependencyInjectingServiceLoader;
import co.elastic.apm.agent.util.ExecutorUtils;
import org.stagemonitor.configuration.ConfigurationOption;
import org.stagemonitor.configuration.ConfigurationOptionProvider;
import org.stagemonitor.configuration.ConfigurationRegistry;

import javax.annotation.Nullable;
import java.io.Closeable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;


/**
 * This is the tracer implementation which provides access to lower level agent functionality.
 * <p>
 * Note that this is a internal API, so there are no guarantees in terms of backwards compatibility.
 * </p>
 */
public class ElasticApmTracer extends BasicTracer implements MetricsAwareTracer {
    private static final Logger logger = LoggerFactory.getLogger(ElasticApmTracer.class);

    private final ConfigurationRegistry configurationRegistry;
    private final StacktraceConfiguration stacktraceConfiguration;
    private final ApmServerClient apmServerClient;
    private final List<LifecycleListener> lifecycleListeners = new CopyOnWriteArrayList<>();
    private final Reporter reporter;
    private final int approximateContextSize;
    private final CoreConfiguration coreConfiguration;
    private final SpanConfiguration spanConfiguration;
    private final List<ActivationListener> activationListeners;
    private final MetricRegistry metricRegistry;
    private final ScheduledThreadPoolExecutor sharedPool;

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

    static ElasticApmTracer of(
        ConfigurationRegistry configurationRegistry,
        MetricRegistry metricRegistry,
        Reporter reporter,
        ObjectPoolFactory objectPoolFactory,
        ApmServerClient apmServerClient,
        final String ephemeralId,
        MetaDataFuture metaDataFuture) {

        int maxPooledElements = configurationRegistry.getConfig(ReporterConfiguration.class).getMaxQueueSize() * 2;
        CoreConfiguration coreConfiguration = configurationRegistry.getConfig(CoreConfiguration.class);
        int transactionMaxSpans = coreConfiguration.getTransactionMaxSpans();
        SpanConfiguration spanConfiguration = configurationRegistry.getConfig(SpanConfiguration.class);

        TracerConfiguration tracerConfiguration = configurationRegistry.getConfig(TracerConfiguration.class);
        boolean recordingConfigOptionSet = tracerConfiguration.getRecordingConfig().get();

        StacktraceConfiguration stacktraceConfiguration = configurationRegistry.getConfig(StacktraceConfiguration.class);

        Sampler sampler = ProbabilitySampler.of(coreConfiguration.getSampleRate().get());
        ScheduledThreadPoolExecutor sharedPool = ExecutorUtils.createSingleThreadSchedulingDaemonPool("shared");

        // The estimated number of wrappers is linear to the number of the number of external/OTel plugins
        // - for an internal agent context, there will be at most one wrapper per external/OTel plugin.
        // - for a context created by an external/OTel, we have one less wrapper required
        int approximateContextSize = coreConfiguration.getExternalPluginsCount() + 1; // +1 extra is for the OTel API plugin

        return new ElasticApmTracer(
            maxPooledElements,
            transactionMaxSpans,
            approximateContextSize,
            configurationRegistry,
            stacktraceConfiguration,
            apmServerClient,
            reporter,
            objectPoolFactory,
            coreConfiguration,
            tracerConfiguration,
            spanConfiguration,
            metricRegistry,
            sharedPool,
            sampler,
            recordingConfigOptionSet,
            ephemeralId,
            metaDataFuture);
    }

    private ElasticApmTracer(
        int maxPooledElements,
        int transactionMaxSpans,
        int approximateContextSize,
        ConfigurationRegistry configurationRegistry,
        StacktraceConfiguration stacktraceConfiguration,
        ApmServerClient apmServerClient,
        Reporter reporter,
        ObjectPoolFactory objectPoolFactory,
        CoreConfiguration coreConfiguration,
        TracerConfiguration tracerConfiguration,
        SpanConfiguration spanConfiguration,
        MetricRegistry metricRegistry,
        ScheduledThreadPoolExecutor sharedPool,
        Sampler sampler,
        boolean recordingConfigOptionSet,
        String ephemeralId,
        MetaDataFuture metaDataFuture) {
        super(maxPooledElements, transactionMaxSpans, objectPoolFactory);
        this.configurationRegistry = configurationRegistry;
        this.approximateContextSize = approximateContextSize;
        this.stacktraceConfiguration = stacktraceConfiguration;
        this.apmServerClient = apmServerClient;
        this.reporter = reporter;
        this.coreConfiguration = coreConfiguration;
        this.spanConfiguration = spanConfiguration;
        this.activationListeners = DependencyInjectingServiceLoader.load(ActivationListener.class, this);
        this.metricRegistry = metricRegistry;
        this.sharedPool = sharedPool;
        this.sampler = sampler;
        this.recordingConfigOptionSet = recordingConfigOptionSet;
        this.ephemeralId = ephemeralId;
        this.metaDataFuture = metaDataFuture;
        coreConfiguration.getSampleRate().addChangeListener(new ConfigurationOption.ChangeListener<Double>() {
            @Override
            public void onChange(ConfigurationOption<?> configurationOption, Double oldValue, Double newValue) {
                ElasticApmTracer.this.sampler = ProbabilitySampler.of(newValue);
            }
        });
        tracerConfiguration.getRecordingConfig().addChangeListener(new ConfigurationOption.ChangeListener<Boolean>() {
            @Override
            public void onChange(ConfigurationOption<?> configurationOption, Boolean oldValue, Boolean newValue) {
                ElasticApmTracer.this.recordingConfigChanged(oldValue, newValue);
            }
        });
    }

    @Override
    protected boolean isIgnoredException(Throwable e) {
        return WildcardMatcher.isAnyMatch(coreConfiguration.getIgnoreExceptions(), e.getClass().getName());
    }

    @Override
    protected boolean hasServiceName() {
        return coreConfiguration.getServiceNameConfig().getUsedKey() != null;
    }

    public Transaction noopTransaction() {
        return createTransaction().startNoop();
    }

    public ConfigurationRegistry getConfigurationRegistry() {
        return configurationRegistry;
    }

    @Override
    public <T extends ConfigurationOptionProvider> T getConfig(Class<T> configProvider) {
        return configurationRegistry.getConfig(configProvider);
    }

    @Override
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

    @Override
    public void endSpan(Span span) {
        if (logger.isDebugEnabled()) {
            logger.debug("endSpan {}", span);
            if (logger.isTraceEnabled()) {
                logger.trace("ending span at", new RuntimeException("this exception is just used to record where the span has been ended from"));
            }
        }

        if (!span.isSampled()) {
            Transaction transaction = span.getTransaction();
            if (transaction != null) {
                transaction.captureDroppedSpan(span);
            }
            span.decrementReferences();
            return;
        }
        if (span.isExit()) {
            if (span.getDuration() < spanConfiguration.getExitSpanMinDuration().getMicros()) {
                logger.debug("Span faster than exit_span_min_duration. Request discarding {}", span);
                span.requestDiscarding();
            }
        } else if (!span.isComposite()) {
            if (span.getDuration() < coreConfiguration.getSpanMinDuration().getMicros()) {
                logger.debug("Span faster than span_min_duration. Request discarding {}", span);
                span.requestDiscarding();
            }
        }
        if (span.isDiscarded()) {
            logger.debug("Discarding span {}", span);
            Transaction transaction = span.getTransaction();
            if (transaction != null) {
                transaction.captureDroppedSpan(span);
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

    @Override
    public void endError(ErrorCapture error) {
        reporter.report(error);
    }

    @Override
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
        LoggingConfiguration.shutdown();
    }

    public Reporter getReporter() {
        return reporter;
    }

    public Sampler getSampler() {
        return sampler;
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

    /**
     * Lazily wraps the currently active context if required, wrapper instance is cached with wrapperClass as key.
     * Wrapping is transparently handled by {@link #currentContext()}.
     *
     * @param wrapperClass wrapper type
     * @param wrapFunction wrapper creation function
     * @param <T>          wrapper type
     * @return newly (or previously) created wrapper
     */
    public <T extends ElasticContext<T>> T wrapActiveContextIfRequired(Class<T> wrapperClass, Callable<T> wrapFunction) {
        return activeStack.get().wrapActiveContextIfRequired(wrapperClass, wrapFunction, approximateContextSize);
    }

    @Override
    public void activate(ElasticContext<?> context) {
        activeStack.get().activate(context, activationListeners);
    }

    @Override
    public void deactivate(ElasticContext<?> context) {
        activeStack.get().deactivate(context, activationListeners, assertionsEnabled);
    }

    @Override
    public MetricRegistry getMetricRegistry() {
        return metricRegistry;
    }

    public List<ServiceInfo> getServiceInfoOverrides() {
        List<ServiceInfo> serviceInfos = new ArrayList<>(serviceInfoByClassLoader.approximateSize());
        for (Map.Entry<ClassLoader, ServiceInfo> entry : serviceInfoByClassLoader) {
            serviceInfos.add(entry.getValue());
        }
        return serviceInfos;
    }

    public void resetServiceInfoOverrides() {
        serviceInfoByClassLoader.clear();
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
