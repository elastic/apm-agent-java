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

import co.elastic.apm.agent.bci.ElasticApmAgent;
import co.elastic.apm.agent.bci.IndyBootstrap;
import co.elastic.apm.agent.bci.InstrumentationStats;
import co.elastic.apm.agent.collections.WeakReferenceCountedMap;
import co.elastic.apm.agent.common.JvmRuntimeInfo;
import co.elastic.apm.agent.common.util.WildcardMatcher;
import co.elastic.apm.agent.configuration.AutoDetectedServiceInfo;
import co.elastic.apm.agent.configuration.CoreConfigurationImpl;
import co.elastic.apm.agent.configuration.MetricsConfigurationImpl;
import co.elastic.apm.agent.configuration.ServerlessConfigurationImpl;
import co.elastic.apm.agent.impl.error.RedactedException;
import co.elastic.apm.agent.impl.metadata.FaaSMetaDataExtension;
import co.elastic.apm.agent.impl.metadata.Framework;
import co.elastic.apm.agent.impl.metadata.MetaDataFuture;
import co.elastic.apm.agent.impl.metadata.NameAndIdField;
import co.elastic.apm.agent.impl.metadata.ServiceFactory;
import co.elastic.apm.agent.impl.transaction.*;
import co.elastic.apm.agent.sdk.internal.util.LoggerUtils;
import co.elastic.apm.agent.tracer.Tracer;
import co.elastic.apm.agent.tracer.metrics.DoubleSupplier;
import co.elastic.apm.agent.tracer.metrics.Labels;
import co.elastic.apm.agent.tracer.pooling.Allocator;
import co.elastic.apm.agent.tracer.service.Service;
import co.elastic.apm.agent.tracer.service.ServiceInfo;
import co.elastic.apm.agent.configuration.SpanConfiguration;
import co.elastic.apm.agent.context.ClosableLifecycleListenerAdapter;
import co.elastic.apm.agent.tracer.LifecycleListener;
import co.elastic.apm.agent.impl.baggage.BaggageImpl;
import co.elastic.apm.agent.impl.baggage.W3CBaggagePropagation;
import co.elastic.apm.agent.impl.error.ErrorCaptureImpl;
import co.elastic.apm.agent.impl.sampling.ProbabilitySampler;
import co.elastic.apm.agent.impl.sampling.Sampler;
import co.elastic.apm.agent.impl.stacktrace.StacktraceConfigurationImpl;
import co.elastic.apm.agent.logging.LoggingConfigurationImpl;
import co.elastic.apm.agent.metrics.MetricRegistry;
import co.elastic.apm.agent.objectpool.ObservableObjectPool;
import co.elastic.apm.agent.objectpool.ObjectPoolFactoryImpl;
import co.elastic.apm.agent.report.ApmServerClient;
import co.elastic.apm.agent.report.Reporter;
import co.elastic.apm.agent.report.ReporterConfigurationImpl;
import co.elastic.apm.agent.sdk.internal.util.PrivilegedActionUtils;
import co.elastic.apm.agent.sdk.internal.util.VersionUtils;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;
import co.elastic.apm.agent.sdk.weakconcurrent.WeakConcurrent;
import co.elastic.apm.agent.sdk.weakconcurrent.WeakMap;
import co.elastic.apm.agent.tracer.GlobalTracer;
import co.elastic.apm.agent.tracer.Scope;
import co.elastic.apm.agent.tracer.dispatch.HeaderGetter;
import co.elastic.apm.agent.tracer.reference.ReferenceCounted;
import co.elastic.apm.agent.tracer.reference.ReferenceCountedMap;
import co.elastic.apm.agent.universalprofiling.UniversalProfilingIntegration;
import co.elastic.apm.agent.util.DependencyInjectingServiceLoader;
import co.elastic.apm.agent.util.ExecutorUtils;
import com.dslplatform.json.JsonWriter;
import org.stagemonitor.configuration.ConfigurationOption;
import org.stagemonitor.configuration.ConfigurationOptionProvider;
import org.stagemonitor.configuration.ConfigurationRegistry;

import javax.annotation.Nullable;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;


/**
 * This is the tracer implementation which provides access to lower level agent functionality.
 * <p>
 * Note that this is a internal API, so there are no guarantees in terms of backwards compatibility.
 * </p>
 */
public class ElasticApmTracer implements Tracer {
    private static final Logger logger = LoggerFactory.getLogger(ElasticApmTracer.class);
    private static final Logger enabledInstrumentationsLogger = LoggerUtils.logOnce(logger);

    private static final WeakMap<ClassLoader, ServiceInfo> serviceInfoByClassLoader = WeakConcurrent.buildMap();

    private static final Map<Class<?>, Class<? extends ConfigurationOptionProvider>> configs = new HashMap<>();

    public static final Set<String> TRACE_HEADER_NAMES;
    public static final int ACTIVATION_STACK_BASE_SIZE = 16;

    static {
        Set<String> headerNames = new HashSet<>();
        headerNames.addAll(TraceContextImpl.TRACE_TEXTUAL_HEADERS);
        headerNames.add(W3CBaggagePropagation.BAGGAGE_HEADER_NAME);
        TRACE_HEADER_NAMES = Collections.unmodifiableSet(headerNames);
    }

    private static volatile boolean classloaderCheckOk = false;

    private final ConfigurationRegistry configurationRegistry;
    private final ApmServerClient apmServerClient;
    private final List<LifecycleListener> lifecycleListeners = new CopyOnWriteArrayList<>();
    private final ObservableObjectPool<TransactionImpl> transactionPool;
    private final ObservableObjectPool<SpanImpl> spanPool;
    private final ObservableObjectPool<ErrorCaptureImpl> errorPool;
    private final ObservableObjectPool<TraceContextImpl> spanLinkPool;
    private final ObservableObjectPool<IdImpl> profilingCorrelationStackTraceIdPool;
    private final Reporter reporter;
    private final ObjectPoolFactoryImpl objectPoolFactory;

    private final EmptyTraceState emptyContext;

    private final ThreadLocal<ActiveStack> activeStack = new ThreadLocal<ActiveStack>() {
        @Override
        protected ActiveStack initialValue() {
            //We allow transactionMaxSpan activation plus a constant minimum of 16 to account for
            // * the activation of the transaction itself
            // * account for baggage updates, which also count towards the depth
            return new ActiveStack(ACTIVATION_STACK_BASE_SIZE + transactionMaxSpans, emptyContext);
        }
    };

    private final CoreConfigurationImpl coreConfiguration;
    private final int transactionMaxSpans;
    private final SpanConfiguration spanConfiguration;
    private final List<ActivationListener> activationListeners;
    private final MetricRegistry metricRegistry;

    private final UniversalProfilingIntegration profilingIntegration;
    private final ScheduledThreadPoolExecutor sharedPool;
    private final int approximateContextSize;
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

    static {
        checkClassloader();
        configs.put(co.elastic.apm.agent.tracer.configuration.CoreConfiguration.class, CoreConfigurationImpl.class);
        configs.put(co.elastic.apm.agent.tracer.configuration.LoggingConfiguration.class, LoggingConfigurationImpl.class);
        configs.put(co.elastic.apm.agent.tracer.configuration.MetricsConfiguration.class, MetricsConfigurationImpl.class);
        configs.put(co.elastic.apm.agent.tracer.configuration.ReporterConfiguration.class, ReporterConfigurationImpl.class);
        configs.put(co.elastic.apm.agent.tracer.configuration.ServerlessConfiguration.class, ServerlessConfigurationImpl.class);
        configs.put(co.elastic.apm.agent.tracer.configuration.StacktraceConfiguration.class, StacktraceConfigurationImpl.class);
    }

    private static void checkClassloader() {
        ClassLoader cl = PrivilegedActionUtils.getClassLoader(GlobalTracer.class);

        // agent currently loaded in the bootstrap CL, which is the current correct location
        if (cl == null) {
            return;
        }

        if (classloaderCheckOk) {
            return;
        }

        String agentLocation = PrivilegedActionUtils.getProtectionDomain(GlobalTracer.class).getCodeSource().getLocation().getFile();
        if (!agentLocation.endsWith(".jar")) {
            // agent is not packaged, thus we assume running tests
            classloaderCheckOk = true;
            return;
        }

        String premainClass = VersionUtils.getManifestEntry(new File(agentLocation), "Premain-Class");
        if (null == premainClass) {
            // packaged within a .jar, but not within an agent jar, thus we assume it's still for testing
            classloaderCheckOk = true;
            return;
        }

        if (premainClass.startsWith("co.elastic.apm.agent")) {
            // premain class will only be present when packaged as an agent jar
            classloaderCheckOk = true;
            return;
        }

        // A packaged agent class has been loaded outside of bootstrap classloader, we are not in the context of
        // unit/integration tests, that's likely a setup issue where the agent jar has been added to application
        // classpath.
        throw new IllegalStateException(String.format("Agent setup error: agent jar file \"%s\"  likely referenced in JVM or application classpath", agentLocation));

    }

    ElasticApmTracer(ConfigurationRegistry configurationRegistry, MetricRegistry metricRegistry, Reporter reporter, ObjectPoolFactoryImpl poolFactory,
                     ApmServerClient apmServerClient, final String ephemeralId, MetaDataFuture metaDataFuture) {
        this.emptyContext = new EmptyTraceState(this);
        this.metricRegistry = metricRegistry;
        this.configurationRegistry = configurationRegistry;
        this.reporter = reporter;
        this.apmServerClient = apmServerClient;
        this.ephemeralId = ephemeralId;
        this.metaDataFuture = metaDataFuture;
        int maxPooledElements = configurationRegistry.getConfig(ReporterConfigurationImpl.class).getMaxQueueSize() * 2;
        coreConfiguration = configurationRegistry.getConfig(CoreConfigurationImpl.class);
        transactionMaxSpans = coreConfiguration.getTransactionMaxSpans();
        spanConfiguration = configurationRegistry.getConfig(SpanConfiguration.class);

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

        // span links pool allows for 10X the maximum allowed span links per span
        spanLinkPool = poolFactory.createSpanLinkPool(AbstractSpanImpl.MAX_ALLOWED_SPAN_LINKS * 10, this);

        profilingCorrelationStackTraceIdPool = poolFactory.createRecyclableObjectPool(maxPooledElements, new Allocator<IdImpl>() {
            @Override
            public IdImpl createInstance() {
                return IdImpl.new128BitId();
            }
        });

        sampler = ProbabilitySampler.of(coreConfiguration.getSampleRate().get());
        coreConfiguration.getSampleRate().addChangeListener(new ConfigurationOption.ChangeListener<Double>() {
            @Override
            public void onChange(ConfigurationOption<?> configurationOption, Double oldValue, Double newValue) {
                sampler = ProbabilitySampler.of(newValue);
            }
        });
        this.activationListeners = DependencyInjectingServiceLoader.load(ActivationListener.class, this);
        sharedPool = ExecutorUtils.createSingleThreadSchedulingDaemonPool("shared");
        IndyBootstrap.setFallbackLogExecutor(sharedPool);

        // The estimated number of wrappers is linear to the number of the number of external/OTel plugins
        // - for an internal agent context, there will be at most one wrapper per external/OTel plugin.
        // - for a context created by an external/OTel, we have one less wrapper required
        approximateContextSize = coreConfiguration.getExternalPluginsCount() + 1; // +1 extra is for the OTel API plugin

        // sets the assertionsEnabled flag to true if indeed enabled
        //noinspection AssertWithSideEffects
        assert assertionsEnabled = true;
        profilingIntegration = new UniversalProfilingIntegration();
    }

    @Override
    @Nullable
    public TransactionImpl startRootTransaction(@Nullable ClassLoader initiatingClassLoader) {
        return startRootTransaction(sampler, -1, currentContext().getBaggage(), initiatingClassLoader);
    }

    @Nullable
    public TransactionImpl startRootTransaction(@Nullable ClassLoader initiatingClassLoader, long epochMicro) {
        return startRootTransaction(sampler, epochMicro, currentContext().getBaggage(), initiatingClassLoader);
    }

    @Nullable
    public TransactionImpl startRootTransaction(@Nullable ClassLoader initiatingClassLoader, BaggageImpl baseBaggage, long epochMicro) {
        return startRootTransaction(sampler, epochMicro, baseBaggage, initiatingClassLoader);
    }

    @Nullable
    public TransactionImpl startRootTransaction(Sampler sampler, long epochMicros, @Nullable ClassLoader initiatingClassLoader) {
        return startRootTransaction(sampler, epochMicros, currentContext().getBaggage(), initiatingClassLoader);
    }

    /**
     * Starts a trace-root transaction with a specified sampler and start timestamp
     *
     * @param sampler               the {@link Sampler} instance which is responsible for determining the sampling decision if this is a root transaction
     * @param epochMicros           the start timestamp
     * @param initiatingClassLoader the class loader corresponding to the service which initiated the creation of the transaction.
     *                              Used to determine the service name and to load application-scoped classes like the {@link org.slf4j.MDC},
     *                              for log correlation.
     * @return a transaction that will be the root of the current trace if the agent is currently RUNNING; null otherwise
     */
    @Nullable
    public TransactionImpl startRootTransaction(Sampler sampler, long epochMicros, BaggageImpl baseBaggage, @Nullable ClassLoader initiatingClassLoader) {
        TransactionImpl transaction = null;
        if (isRunning()) {
            transaction = createTransaction().startRoot(epochMicros, sampler, baseBaggage);
            afterTransactionStart(initiatingClassLoader, transaction);
        }
        return transaction;
    }

    @Nullable
    public <T, C> TransactionImpl startChildTransaction(@Nullable C headerCarrier, HeaderGetter<T, C> headerGetter, @Nullable ClassLoader initiatingClassLoader) {
        return startChildTransaction(headerCarrier, headerGetter, sampler, -1, initiatingClassLoader);
    }

    @Nullable
    public <T, C> TransactionImpl startChildTransaction(@Nullable C headerCarrier, HeaderGetter<T, C> headersGetter, @Nullable ClassLoader initiatingClassLoader, BaggageImpl baseBaggage, long epochMicros) {
        return startChildTransaction(headerCarrier, headersGetter, sampler, epochMicros, initiatingClassLoader);
    }

    /**
     * Starts a transaction as a child of the context headers obtained through the provided {@link HeaderGetter}.
     * If the created transaction cannot be started as a child transaction (for example - if no parent context header is
     * available), then it will be started as the root transaction of the trace.
     *
     * @param headerCarrier         the Object from which context headers can be obtained, typically a request or a message
     * @param headersGetter         provides the trace context headers required in order to create a child transaction
     * @param sampler               the {@link Sampler} instance which is responsible for determining the sampling decision if this is a root transaction
     * @param epochMicros           the start timestamp
     * @param initiatingClassLoader the class loader corresponding to the service which initiated the creation of the transaction.
     *                              Used to determine the service name and to load application-scoped classes like the {@link org.slf4j.MDC},
     *                              for log correlation.
     * @return a transaction which is a child of the provided parent if the agent is currently RUNNING; null otherwise
     */
    @Nullable
    public <T, C> TransactionImpl startChildTransaction(@Nullable C headerCarrier, HeaderGetter<T, C> headersGetter, Sampler sampler,
                                                        long epochMicros, @Nullable ClassLoader initiatingClassLoader) {
        return startChildTransaction(headerCarrier, headersGetter, sampler, epochMicros, currentContext().getBaggage(), initiatingClassLoader);
    }

    @Nullable
    private <T, C> TransactionImpl startChildTransaction(@Nullable C headerCarrier, HeaderGetter<T, C> headersGetter, Sampler sampler,
                                                         long epochMicros, BaggageImpl baseBaggage, @Nullable ClassLoader initiatingClassLoader) {
        TransactionImpl transaction = null;
        if (isRunning()) {
            transaction = createTransaction().start(headerCarrier,
                headersGetter, epochMicros, sampler, baseBaggage);
            afterTransactionStart(initiatingClassLoader, transaction);
        }
        return transaction;
    }

    private void afterTransactionStart(@Nullable ClassLoader initiatingClassLoader, TransactionImpl transaction) {
        if (logger.isDebugEnabled()) {
            logger.debug("startTransaction {}", transaction);
            if (logger.isTraceEnabled()) {
                logger.trace("starting transaction at",
                    new RuntimeException("this exception is just used to record where the transaction has been started from"));
            }
        }
        final ServiceInfo serviceInfo = getServiceInfoForClassLoader(initiatingClassLoader);
        if (serviceInfo != null) {
            transaction.getTraceContext().setServiceInfo(serviceInfo.getServiceName(), serviceInfo.getServiceVersion());
        }
        profilingIntegration.afterTransactionStart(transaction);
    }

    public TransactionImpl noopTransaction() {
        return createTransaction().startNoop();
    }

    private TransactionImpl createTransaction() {
        TransactionImpl transaction = transactionPool.createInstance();
        while (transaction.getReferenceCount() != 0) {
            logger.warn("Tried to start a transaction with a non-zero reference count {} {}", transaction.getReferenceCount(), transaction);
            transaction = transactionPool.createInstance();
        }
        return transaction;
    }

    @Override
    @Nullable
    public TransactionImpl currentTransaction() {
        return currentContext().getTransaction();
    }

    @Nullable
    @Override
    public ErrorCaptureImpl getActiveError() {
        return ErrorCaptureImpl.getActive();
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
    /**
     * Starts a span with a given parent context.
     * <p>
     * This method makes it possible to start a span after the parent has already ended.
     * </p>
     *
     * @param childContextCreator extracts the trace context to generate based on the provided parent
     * @param parentContext       the trace context of the parent
     * @param baggage             the baggage to use for the newly created span
     * @param <T>                 the type of the parent context
     * @return a new started span
     */
    public <T> SpanImpl startSpan(TraceContextImpl.ChildContextCreator<T> childContextCreator, T parentContext, BaggageImpl baggage) {
        return startSpan(childContextCreator, parentContext, baggage, -1);
    }

    public SpanImpl startSpan(AbstractSpanImpl<?> parent, BaggageImpl baggage, long epochMicros) {
        return startSpan(TraceContextImpl.fromParent(), parent, baggage, epochMicros);
    }

    /**
     * @param parentContext the trace context of the parent
     * @param epochMicros   the start timestamp of the span in microseconds after epoch
     * @return a new started span
     */
    public <T> SpanImpl startSpan(TraceContextImpl.ChildContextCreator<T> childContextCreator, T parentContext, BaggageImpl baggage, long epochMicros) {
        return createSpan().start(childContextCreator, parentContext, baggage, epochMicros);
    }

    private SpanImpl createSpan() {
        SpanImpl span = spanPool.createInstance();
        while (span.getReferenceCount() != 0) {
            logger.warn("Tried to start a span with a non-zero reference count {} {}", span.getReferenceCount(), span);
            span = spanPool.createInstance();
        }
        return span;
    }

    public void captureAndReportException(@Nullable Throwable e, ClassLoader initiatingClassLoader) {
        ErrorCaptureImpl errorCapture = captureException(System.currentTimeMillis() * 1000, e, currentContext(), initiatingClassLoader);
        if (errorCapture != null) {
            errorCapture.end();
        }
    }

    @Nullable
    public String captureAndReportException(long epochMicros, @Nullable Throwable e, TraceStateImpl<?> parentContext) {
        String id = null;
        ErrorCaptureImpl errorCapture = captureException(epochMicros, e, parentContext, null);
        if (errorCapture != null) {
            id = errorCapture.getTraceContext().getId().toString();
            errorCapture.end();
        }
        return id;
    }

    @Nullable
    public ErrorCaptureImpl captureException(@Nullable Throwable e, TraceStateImpl<?> parentContext, @Nullable ClassLoader initiatingClassLoader) {
        return captureException(System.currentTimeMillis() * 1000, e, parentContext, initiatingClassLoader);
    }

    @Nullable
    @Override
    public ErrorCaptureImpl captureException(@Nullable Throwable e, @Nullable ClassLoader initiatingClassLoader) {
        return captureException(System.currentTimeMillis() * 1000, e, currentContext(), initiatingClassLoader);
    }

    @Nullable
    private ErrorCaptureImpl captureException(long epochMicros, @Nullable Throwable e, TraceStateImpl<?> parentContext, @Nullable ClassLoader initiatingClassLoader) {
        if (!isRunning() || e == null) {
            return null;
        }

        if (!coreConfiguration.captureExceptionDetails()) {
            return null;
        }

        e = redactExceptionIfRequired(e);

        while (e != null && WildcardMatcher.anyMatch(coreConfiguration.getUnnestExceptions(), e.getClass().getName()) != null) {
            e = e.getCause();
        }

        // note: if we add inheritance support for exception filtering, caching would be required for performance
        if (e != null && !WildcardMatcher.isAnyMatch(coreConfiguration.getIgnoreExceptions(), e.getClass().getName())) {
            ErrorCaptureImpl error = errorPool.createInstance();
            error.withTimestamp(epochMicros);
            error.setException(e);
            TransactionImpl currentTransaction = currentTransaction();
            if (currentTransaction != null) {
                if (currentTransaction.getNameForSerialization().length() > 0) {
                    error.setTransactionName(currentTransaction.getNameForSerialization());
                }
                error.setTransactionType(currentTransaction.getType());
                error.setTransactionSampled(currentTransaction.isSampled());
            }
            AbstractSpanImpl<?> parent = parentContext.getSpan();
            if (parent != null) {
                error.asChildOf(parent);
                // don't discard spans leading up to an error, otherwise they'd point to an invalid parent
                parent.setNonDiscardable();
            } else {
                error.getTraceContext().getId().setToRandomValue();
                ServiceInfo serviceInfo = getServiceInfoForClassLoader(initiatingClassLoader);
                if (serviceInfo != null) {
                    error.getTraceContext().setServiceInfo(serviceInfo.getServiceName(), serviceInfo.getServiceVersion());
                }
            }
            parentContext.getBaggage()
                .storeBaggageInContext(error.getContext(), getConfig(CoreConfigurationImpl.class).getBaggageToAttach());
            return error;
        }
        return null;
    }

    public ConfigurationRegistry getConfigurationRegistry() {
        return configurationRegistry;
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public <T> T getConfig(Class<T> configProvider) {
        T configuration = null;
        if (configs.containsKey(configProvider)) {
            configuration = (T) configurationRegistry.getConfig(configs.get(configProvider));
        } else if (ConfigurationOptionProvider.class.isAssignableFrom(configProvider)) {
             configuration = (T) configurationRegistry.getConfig((Class) configProvider);
        }
        if (configuration == null) {
            throw new IllegalStateException("no configuration available for " + configProvider.getName());
        }
        return configuration;
    }

    public void endTransaction(TransactionImpl transaction) {
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
            profilingIntegration.correlateAndReport(transaction);
        } else {
            profilingIntegration.drop(transaction);
            transaction.decrementReferences();
        }
    }

    public void reportPartialTransaction(TransactionImpl transaction) {
        reporter.reportPartialTransaction(transaction);
    }

    public void endSpan(SpanImpl span) {
        if (logger.isDebugEnabled()) {
            logger.debug("endSpan {}", span);
            if (logger.isTraceEnabled()) {
                logger.trace("ending span at", new RuntimeException("this exception is just used to record where the span has been ended from"));
            }
        }

        if (!span.isSampled()) {
            TransactionImpl transaction = span.getTransaction();
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
            TransactionImpl transaction = span.getTransaction();
            if (transaction != null) {
                transaction.captureDroppedSpan(span);
            }
            span.decrementReferences();
            return;

        }
        reportSpan(span);
    }

    private void reportSpan(SpanImpl span) {
        AbstractSpanImpl<?> parent = span.getParent();
        if (parent != null && parent.isDiscarded()) {
            logger.warn("Reporting a child of an discarded span. The current span '{}' will not be shown in the UI. Consider deactivating span_min_duration.", span);
        }
        TransactionImpl transaction = span.getTransaction();
        if (transaction != null) {
            transaction.getSpanCount().getReported().incrementAndGet();
        }
        // makes sure that parents are also non-discardable
        span.setNonDiscardable();

        reporter.report(span);
    }

    public void endError(ErrorCaptureImpl error) {
        reporter.report(error);
    }

    public TraceContextImpl createSpanLink() {
        return spanLinkPool.createInstance();
    }

    public IdImpl createProfilingCorrelationStackTraceId() {
        return profilingCorrelationStackTraceIdPool.createInstance();
    }

    public void recycle(TransactionImpl transaction) {
        transactionPool.recycle(transaction);
    }

    public void recycle(SpanImpl span) {
        spanPool.recycle(span);
    }

    public void recycle(ErrorCaptureImpl error) {
        errorPool.recycle(error);
    }

    public void recycle(TraceContextImpl traceContext) {
        spanLinkPool.recycle(traceContext);
    }

    public void recycleProfilingCorrelationStackTraceId(IdImpl id) {
        profilingCorrelationStackTraceIdPool.recycle(id);
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

        profilingIntegration.stop();
        try {
            configurationRegistry.close();
            reporter.close();
        } catch (Exception e) {
            logger.warn("Suppressed exception while calling stop()", e);
        }
        //Shutting down logging resets the log level to OFF - subsequent tests in the class will get no log output, hence the guard
        if (!assertionsEnabled) {
            LoggingConfigurationImpl.shutdown();
        }
    }

    public Reporter getReporter() {
        return reporter;
    }

    public UniversalProfilingIntegration getProfilingIntegration() {
        return profilingIntegration;
    }

    public Sampler getSampler() {
        return sampler;
    }

    @Override
    public ObjectPoolFactoryImpl getObjectPoolFactory() {
        return objectPoolFactory;
    }

    @Override
    public <K, V extends ReferenceCounted> ReferenceCountedMap<K, V> newReferenceCountedMap() {
        return new WeakReferenceCountedMap<>();
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
        long delayInitMs = getConfig(CoreConfigurationImpl.class).getDelayTracerStartMs();
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
        profilingIntegration.start(this);
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

    @Nullable
    public SpanImpl createExitChildSpan() {
        AbstractSpanImpl<?> active = getActive();
        if (active == null) {
            return null;
        }
        return active.createExitSpan();
    }

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
     * @return the currently active context, {@literal null} if there is none.
     */

    public TraceStateImpl<?> currentContext() {
        return activeStack.get().currentContext();
    }

    @Nullable
    @Override
    public AbstractSpanImpl<?> getActive() {
        return currentContext().getSpan();
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
    public <T extends TraceStateImpl<T>> T wrapActiveContextIfRequired(Class<T> wrapperClass, Callable<T> wrapFunction) {
        return activeStack.get().wrapActiveContextIfRequired(wrapperClass, wrapFunction, approximateContextSize);
    }

    public void activate(TraceStateImpl<?> context) {
        activeStack.get().activate(context, activationListeners);
    }

    public Scope activateInScope(final TraceStateImpl<?> context) {
        // already in scope
        if (currentContext() == context) {
            return NoopScope.INSTANCE;
        }
        context.activate();

        if (context instanceof Scope) {
            // we can take shortcut and avoid creating a separate object
            return (Scope) context;
        }
        return new Scope() {
            @Override
            public void close() {
                context.deactivate();
            }
        };
    }

    public void deactivate(TraceStateImpl<?> context) {
        activeStack.get().deactivate(context, activationListeners, assertionsEnabled);
    }

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

    @Override
    public void setServiceInfoForClassLoader(@Nullable ClassLoader classLoader, ServiceInfo serviceInfo) {
        // overriding the service name/version for the bootstrap class loader is not an actual use-case
        // null may also mean we don't know about the initiating class loader
        if (classLoader == null
            || !serviceInfo.hasServiceName()
            // if the service name is set explicitly, don't override it
            || coreConfiguration.getServiceNameConfig().getUsedKey() != null) {
            return;
        }

        logger.debug("Using `{}` as the service name and `{}` as the service version for class loader [{}]", serviceInfo.getServiceName(), serviceInfo.getServiceVersion(), classLoader);
        if (!serviceInfoByClassLoader.containsKey(classLoader)) {
            serviceInfoByClassLoader.putIfAbsent(classLoader, serviceInfo);
        }
    }

    @Nullable
    public ServiceInfo getServiceInfoForClassLoader(@Nullable ClassLoader initiatingClassLoader) {
        if (initiatingClassLoader == null) {
            return null;
        }
        return serviceInfoByClassLoader.get(initiatingClassLoader);
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

    @Override
    public void addShutdownHook(AutoCloseable closeable) {
        lifecycleListeners.add(ClosableLifecycleListenerAdapter.of(closeable));
    }

    @Nullable
    @Override
    public <T extends co.elastic.apm.agent.tracer.Tracer> T probe(Class<T> type) {
        if (type.isInstance(this)) {
            return type.cast(this);
        } else {
            return null;
        }
    }

    @Override
    public <T extends co.elastic.apm.agent.tracer.Tracer> T require(Class<T> type) {
        T cast = probe(type);
        if (cast == null) {
            throw new IllegalStateException(this + " does not implement " + type.getName());
        }
        return cast;
    }

    @Override
    public Set<String> getTraceHeaderNames() {
        return TRACE_HEADER_NAMES;
    }

    @Override
    public ServiceInfo autoDetectedServiceInfo() {
        return AutoDetectedServiceInfo.autoDetected();
    }

    @Override
    public void reportLog(String log) {
        reporter.reportLog(log);
    }

    @Override
    public void reportLog(byte[] log) {
        reporter.reportLog(log);
    }

    @Nullable
    @Override
    public Service createService(String ephemeralId) {
        return new ServiceFactory().createService(
            coreConfiguration,
            ephemeralId,
            configurationRegistry.getConfig(ServerlessConfigurationImpl.class).runsOnAwsLambda()
        );
    }

    @Override
    @Nullable
    public Throwable redactExceptionIfRequired(@Nullable Throwable original) {
        if (original != null && coreConfiguration.isRedactExceptions()) {
            return new RedactedException();
        }
        return original;
    }

    @Override
    public void flush() {
        long flushTimeout = configurationRegistry.getConfig(ServerlessConfigurationImpl.class).getDataFlushTimeout();
        try {
            if (!reporter.flush(flushTimeout, TimeUnit.MILLISECONDS, true)) {
                logger.error("APM data flush haven't completed within {} milliseconds.", flushTimeout);
            }
        } catch (Exception e) {
            logger.error("An error occurred on flushing APM data.", e);
        }
        logEnabledInstrumentations();
    }

    private void logEnabledInstrumentations() {
        if (enabledInstrumentationsLogger.isInfoEnabled()) {
            InstrumentationStats instrumentationStats = ElasticApmAgent.getInstrumentationStats();
            enabledInstrumentationsLogger.info("Used instrumentation groups: {}", instrumentationStats.getUsedInstrumentationGroups());
        }
    }

    @Override
    public void completeMetaData(String name, String version, String id, String region) {
        metaDataFuture.getFaaSMetaDataExtensionFuture().complete(new FaaSMetaDataExtension(
            new Framework(name, version),
            new NameAndIdField(null, id),
            region
        ));
    }

    @Override
    public void removeGauge(String name, Labels.Immutable labels) {
        metricRegistry.removeGauge(name, labels);
    }

    @Override
    public void addGauge(String name, Labels.Immutable labels, DoubleSupplier supplier) {
        metricRegistry.add(name, labels, supplier);
    }

    @Override
    public void submit(Runnable job) {
        sharedPool.submit(job);
    }

    @Override
    public void schedule(Runnable job, long interval, TimeUnit timeUnit) {
        sharedPool.scheduleAtFixedRate(job, 0, interval, timeUnit);
    }

    public void reportMetric(JsonWriter metrics) {
        reporter.reportMetrics(metrics);
    }

    /**
     * An enumeration used to represent the current tracer state.
     */
    public enum TracerState {
        /**
         * The agent's state before it has been started for the first time.
         */
        UNINITIALIZED,

        /**
         * Indicates that the agent is currently fully functional - tracing, monitoring and sending data to the APM server.
         */
        RUNNING,

        /**
         * The agent is mostly idle, consuming minimal resources, ready to quickly resume back to RUNNING. When the agent
         * is PAUSED, it is not tracing and not communicating with the APM server. However, classes are still instrumented
         * and threads are still alive.
         */
        PAUSED,

        /**
         * Indicates that the agent had been stopped.
         * NOTE: this state is irreversible- the agent cannot resume if it has already been stopped.
         */
        STOPPED
    }
}
