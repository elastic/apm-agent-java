package co.elastic.apm.impl;

import co.elastic.apm.api.ElasticApm;
import co.elastic.apm.api.Tracer;
import co.elastic.apm.api.TracerRegisterer;
import co.elastic.apm.configuration.CoreConfiguration;
import co.elastic.apm.configuration.PrefixingConfigurationSourceWrapper;
import co.elastic.apm.impl.error.ErrorCapture;
import co.elastic.apm.impl.payload.ProcessFactory;
import co.elastic.apm.impl.payload.ServiceFactory;
import co.elastic.apm.impl.payload.SystemInfo;
import co.elastic.apm.impl.stacktrace.Stacktrace;
import co.elastic.apm.impl.stacktrace.StacktraceConfiguration;
import co.elastic.apm.impl.stacktrace.StacktraceFactory;
import co.elastic.apm.impl.transaction.Span;
import co.elastic.apm.impl.transaction.Transaction;
import co.elastic.apm.objectpool.NoopObjectPool;
import co.elastic.apm.objectpool.ObjectPool;
import co.elastic.apm.objectpool.RecyclableObjectFactory;
import co.elastic.apm.objectpool.impl.RingBufferObjectPool;
import co.elastic.apm.report.ApmServerHttpPayloadSender;
import co.elastic.apm.report.ApmServerReporter;
import co.elastic.apm.report.Reporter;
import co.elastic.apm.report.ReporterConfiguration;
import co.elastic.apm.report.serialize.JacksonPayloadSerializer;
import com.blogspot.mydailyjava.weaklockfree.DetachedThreadLocal;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.afterburner.AfterburnerModule;
import okhttp3.OkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.stagemonitor.configuration.ConfigurationOptionProvider;
import org.stagemonitor.configuration.ConfigurationRegistry;
import org.stagemonitor.configuration.source.EnvironmentVariableConfigurationSource;
import org.stagemonitor.configuration.source.PropertyFileConfigurationSource;
import org.stagemonitor.configuration.source.SystemPropertyConfigurationSource;

import javax.annotation.Nullable;
import java.util.ServiceLoader;
import java.util.concurrent.TimeUnit;

/**
 * This is the implementation of the {@link Tracer} interface which provides access to lower level agent functionality.
 * <p>
 * Note that this is a internal API, so there are no guarantees in terms of backwards compatibility.
 * </p>
 */
public class ElasticApmTracer implements Tracer {
    public static final double MS_IN_NANOS = TimeUnit.MILLISECONDS.toNanos(1);
    private static final Logger logger = LoggerFactory.getLogger(ElasticApmTracer.class);
    @Nullable
    private static ElasticApmTracer instance;

    private final ConfigurationRegistry configurationRegistry;
    private final StacktraceConfiguration stacktraceConfiguration;
    private final ObjectPool<Transaction> transactionPool;
    private final ObjectPool<Span> spanPool;
    private final ObjectPool<Stacktrace> stackTracePool;
    private final ObjectPool<ErrorCapture> errorPool;
    private final Reporter reporter;
    private final StacktraceFactory stacktraceFactory;
    private final DetachedThreadLocal<Transaction> currentTransaction = new DetachedThreadLocal<>(DetachedThreadLocal.Cleaner.INLINE);
    private final DetachedThreadLocal<Span> currentSpan = new DetachedThreadLocal<>(DetachedThreadLocal.Cleaner.INLINE);
    private final CoreConfiguration coreConfiguration;

    ElasticApmTracer(ConfigurationRegistry configurationRegistry, Reporter reporter, StacktraceFactory stacktraceFactory) {
        this.configurationRegistry = configurationRegistry;
        this.reporter = reporter;
        this.stacktraceFactory = stacktraceFactory;
        this.stacktraceConfiguration = configurationRegistry.getConfig(StacktraceConfiguration.class);
        int maxPooledElements = configurationRegistry.getConfig(ReporterConfiguration.class).getMaxQueueSize() * 2;
        transactionPool = new RingBufferObjectPool<>(maxPooledElements, false,
            new RecyclableObjectFactory<Transaction>() {
                @Override
                public Transaction createInstance() {
                    return new Transaction();
                }
            });
        spanPool = new RingBufferObjectPool<>(maxPooledElements, false,
            new RecyclableObjectFactory<Span>() {
                @Override
                public Span createInstance() {
                    return new Span();
                }
            });
        errorPool = new RingBufferObjectPool<>(64, false,
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
    }

    public static Builder builder() {
        return new Builder();
    }

    public static ElasticApmTracer get() {
        if (instance == null) {
            // TODO init with noop tracer, instance should never be null
            throw new IllegalStateException("Tracer instance was null");
        }
        return instance;
    }

    // @VisibleForTesting
    static void unregister() {
        synchronized (ElasticApmTracer.class) {
            instance = null;
            TracerRegisterer.unregister();
        }
    }

    private static boolean isRegistered() {
        return instance != null;
    }

    /**
     * Statically registers this instance so that it can be obtained via {@link ElasticApmTracer#get()} and {@link ElasticApm#get()}
     */
    public ElasticApmTracer register() {
        synchronized (ElasticApmTracer.class) {
            if (isRegistered()) {
                // throwing an exception would be too harsh as we don't want to crash applications running in production
                // by using an assert, we can verify this invariant in tests
                logger.error("ElasticApmTracer.register has already been called");
                assert false;
            } else {
                instance = this;
                TracerRegisterer.register(instance);
            }
            return this;
        }
    }

    @Override
    public Transaction startTransaction() {
        Transaction transaction = transactionPool.createInstance().start(this, System.nanoTime(), true);
        currentTransaction.set(transaction);
        return transaction;
    }

    @Override
    public Transaction currentTransaction() {
        return currentTransaction.get();
    }

    @Override
    public Span currentSpan() {
        return currentSpan.get();
    }

    @Override
    public Span startSpan() {
        Transaction transaction = currentTransaction();
        Span span = spanPool.createInstance();
        final boolean dropped;
        if (coreConfiguration.getTransactionMaxSpans() > transaction.getSpans().size()) {
            dropped = false;
            transaction.addSpan(span);
        } else {
            dropped = true;
            transaction.getSpanCount().getDropped().increment();
        }
        span.start(this, transaction, currentSpan(), System.nanoTime(), dropped);
        currentSpan.set(span);
        return span;
    }

    public void captureException(Exception e) {
        ErrorCapture error = new ErrorCapture();
        error.withTimestamp(System.currentTimeMillis());
        error.getException().withMessage(e.getMessage());
        error.getException().withType(e.getClass().getName());
        stacktraceFactory.fillStackTrace(error.getException().getStacktrace(), e.getStackTrace());
        Transaction transaction = currentTransaction();
        if (transaction != null) {
            error.getTransaction().withId(transaction.getId());
            error.getContext().copyFrom(transaction.getContext());
        }
        reporter.report(error);
    }

    public <T extends ConfigurationOptionProvider> T getPlugin(Class<T> pluginClass) {
        return configurationRegistry.getConfig(pluginClass);
    }

    @SuppressWarnings("ReferenceEquality")
    public void endTransaction(Transaction transaction) {
        if (currentTransaction.get() != transaction) {
            logger.warn("Trying to end a transaction which is not the current (thread local) transaction!");
            assert false;
            return;
        }
        currentTransaction.clear();
        reporter.report(transaction);
    }

    @SuppressWarnings("ReferenceEquality")
    public void endSpan(Span span) {
        if (currentSpan.get() != span) {
            logger.warn("Trying to end a span which is not the current (thread local) span!");
            assert false;
            return;
        }
        int spanFramesMinDurationMs = stacktraceConfiguration.getSpanFramesMinDurationMs();
        if (spanFramesMinDurationMs != 0) {
            if (span.getDuration() >= spanFramesMinDurationMs) {
                stacktraceFactory.fillStackTrace(span.getStacktrace());
            }
        }
        currentSpan.clear();
    }

    public void recycle(Transaction transaction) {
        for (Span span : transaction.getSpans()) {
            for (Stacktrace st : span.getStacktrace()) {
                stackTracePool.recycle(st);
            }
            spanPool.recycle(span);
        }
        transactionPool.recycle(transaction);
    }

    public void recycle(ErrorCapture error) {
        errorPool.recycle(error);
    }

    public static class Builder {

        @Nullable
        private ConfigurationRegistry configurationRegistry;
        @Nullable
        private Reporter reporter;
        @Nullable
        private StacktraceFactory stacktraceFactory;

        public Builder configurationRegistry(ConfigurationRegistry configurationRegistry) {
            this.configurationRegistry = configurationRegistry;
            return this;
        }

        public Builder reporter(Reporter reporter) {
            this.reporter = reporter;
            return this;
        }

        public Builder stacktraceFactory(StacktraceFactory stacktraceFactory) {
            this.stacktraceFactory = stacktraceFactory;
            return this;
        }

        public ElasticApmTracer build() {
            if (configurationRegistry == null) {
                configurationRegistry = ConfigurationRegistry.builder()
                    .addConfigSource(new PrefixingConfigurationSourceWrapper(new SystemPropertyConfigurationSource(), "elastic.apm"))
                    .addConfigSource(new PropertyFileConfigurationSource("elasticapm.properties"))
                    .addConfigSource(new PrefixingConfigurationSourceWrapper(new EnvironmentVariableConfigurationSource(), "ELASTIC_APM"))
                    .optionProviders(ServiceLoader.load(ConfigurationOptionProvider.class, ElasticApmTracer.class.getClassLoader()))
                    .build();
            }
            if (reporter == null) {
                reporter = createReporter(configurationRegistry.getConfig(CoreConfiguration.class),
                    configurationRegistry.getConfig(ReporterConfiguration.class),
                    null, null);
            }
            if (stacktraceFactory == null) {
                StacktraceConfiguration stackConfig = configurationRegistry.getConfig(StacktraceConfiguration.class);
                stacktraceFactory = new StacktraceFactory.CurrentThreadStackTraceFactory(stackConfig);
            }
            return new ElasticApmTracer(configurationRegistry, reporter, stacktraceFactory);
        }

        private Reporter createReporter(CoreConfiguration coreConfiguration, ReporterConfiguration reporterConfiguration,
                                        @Nullable String frameworkName, @Nullable String frameworkVersion) {
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.registerModule(new AfterburnerModule());
            return new ApmServerReporter(
                new ServiceFactory().createService(coreConfiguration, frameworkName, frameworkVersion),
                ProcessFactory.ForCurrentVM.INSTANCE.getProcessInformation(),
                SystemInfo.create(),
                new ApmServerHttpPayloadSender(new OkHttpClient.Builder()
                    .connectTimeout(reporterConfiguration.getServerTimeout(), TimeUnit.SECONDS)
                    .build(), new JacksonPayloadSerializer(objectMapper), reporterConfiguration), true, reporterConfiguration);
        }
    }
}
