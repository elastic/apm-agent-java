package co.elastic.apm.impl;

import co.elastic.apm.api.ElasticApm;
import co.elastic.apm.api.Tracer;
import co.elastic.apm.configuration.CoreConfiguration;
import co.elastic.apm.configuration.PrefixingConfigurationSourceWrapper;
import co.elastic.apm.objectpool.ObjectPool;
import co.elastic.apm.objectpool.RecyclableObjectFactory;
import co.elastic.apm.objectpool.impl.RingBufferObjectPool;
import co.elastic.apm.report.ApmServerHttpPayloadSender;
import co.elastic.apm.report.Reporter;
import co.elastic.apm.report.ReporterConfiguration;
import co.elastic.apm.report.serialize.JacksonPayloadSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.afterburner.AfterburnerModule;
import okhttp3.OkHttpClient;
import org.stagemonitor.configuration.ConfigurationOptionProvider;
import org.stagemonitor.configuration.ConfigurationRegistry;
import org.stagemonitor.configuration.source.EnvironmentVariableConfigurationSource;
import org.stagemonitor.configuration.source.PropertyFileConfigurationSource;
import org.stagemonitor.configuration.source.SystemPropertyConfigurationSource;

import java.util.ServiceLoader;
import java.util.concurrent.TimeUnit;

public class ElasticApmTracer implements Tracer {

    static final double MS_IN_NANOS = TimeUnit.MILLISECONDS.toNanos(1);
    private static ElasticApmTracer instance;
    private final ConfigurationRegistry configurationRegistry;
    private final ObjectPool<Transaction> transactionPool;
    private final ObjectPool<Span> spanPool;
    private final Reporter reporter;

    ElasticApmTracer(ConfigurationRegistry configurationRegistry, Reporter reporter) {
        this.configurationRegistry = configurationRegistry;
        this.reporter = reporter;
        transactionPool = new RingBufferObjectPool<>(Reporter.REPORTER_QUEUE_LENGTH * 2, true,
            new RecyclableObjectFactory<Transaction>() {
                @Override
                public Transaction createInstance() {
                    return new Transaction();
                }
            });
        spanPool = new RingBufferObjectPool<>(Reporter.REPORTER_QUEUE_LENGTH * 2, true,
            new RecyclableObjectFactory<Span>() {
                @Override
                public Span createInstance() {
                    return new Span();
                }
            });
    }

    public static Builder builder() {
        return new Builder();
    }

    public static ElasticApmTracer get() {
        return instance;
    }

    private static boolean isStarted() {
        return instance != null;
    }

    /**
     * Statically registers this instance so that it can be obtained via {@link ElasticApmTracer#get()} and {@link ElasticApm#get()}
     */
    public ElasticApmTracer register() {
        synchronized (ElasticApmTracer.class) {
            if (isStarted()) {
                throw new IllegalStateException("Elastic APM is already started");
            }
            instance = this;
            ElasticApm.register(instance);
            return instance;
        }
    }

    public void stop() {
        instance = null;
    }

    @Override
    public Transaction startTransaction() {
        return transactionPool.createInstance().start(this, System.nanoTime());
    }

    @Override
    public Transaction currentTransaction() {
        return instance.currentTransaction();
    }

    @Override
    public Span startSpan() {
        Transaction transaction = currentTransaction();
        Span span = spanPool.createInstance().start(transaction, System.nanoTime());
        transaction.getSpans().add(span);
        return span;
    }

    public <T extends ConfigurationOptionProvider> T getPlugin(Class<T> pluginClass) {
        return configurationRegistry.getConfig(pluginClass);
    }

    void reportTransaction(Transaction transaction) {
        reporter.report(transaction);
    }

    void recycle(Transaction transaction) {
        for (Span span : transaction.getSpans()) {
            spanPool.recycle(span);
                /*TODO recycle stacktrace
                for (Stacktrace st : stacktrace) {
                    st.recycle();
                }*/
        }
        transactionPool.recycle(transaction);
    }

    public static class Builder {

        private ConfigurationRegistry configurationRegistry;
        private Reporter reporter;

        public Builder configurationRegistry(ConfigurationRegistry configurationRegistry) {
            this.configurationRegistry = configurationRegistry;
            return this;
        }

        public Builder reporter(Reporter reporter) {
            this.reporter = reporter;
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
            return new ElasticApmTracer(configurationRegistry, reporter);
        }

        private Reporter createReporter(CoreConfiguration coreConfiguration, ReporterConfiguration reporterConfiguration,
                                        String frameworkName, String frameworkVersion) {
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.registerModule(new AfterburnerModule());
            return new Reporter(
                new ServiceFactory().createService(coreConfiguration, frameworkName, frameworkVersion),
                new ProcessFactory().getProcessInformation(),
                new SystemFactory().getSystem(),
                new ApmServerHttpPayloadSender(new OkHttpClient.Builder()
                    .connectTimeout(reporterConfiguration.getServerTimeout(), TimeUnit.SECONDS)
                    .build(), new JacksonPayloadSerializer(objectMapper), reporterConfiguration), true);
        }
    }
}
