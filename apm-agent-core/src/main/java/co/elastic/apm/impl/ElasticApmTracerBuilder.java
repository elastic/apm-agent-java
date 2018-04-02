package co.elastic.apm.impl;

import co.elastic.apm.configuration.CoreConfiguration;
import co.elastic.apm.configuration.PrefixingConfigurationSourceWrapper;
import co.elastic.apm.context.LifecycleListener;
import co.elastic.apm.impl.stacktrace.StacktraceConfiguration;
import co.elastic.apm.impl.stacktrace.StacktraceFactory;
import co.elastic.apm.report.Reporter;
import co.elastic.apm.report.ReporterFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.stagemonitor.configuration.ConfigurationOptionProvider;
import org.stagemonitor.configuration.ConfigurationRegistry;
import org.stagemonitor.configuration.source.EnvironmentVariableConfigurationSource;
import org.stagemonitor.configuration.source.PropertyFileConfigurationSource;
import org.stagemonitor.configuration.source.SimpleSource;
import org.stagemonitor.configuration.source.SystemPropertyConfigurationSource;

import javax.annotation.Nullable;
import java.util.List;
import java.util.ServiceLoader;
import java.util.concurrent.TimeUnit;

public class ElasticApmTracerBuilder {

    private static final Logger logger = LoggerFactory.getLogger(ElasticApmTracerBuilder.class);

    @Nullable
    private ConfigurationRegistry configurationRegistry;
    @Nullable
    private Reporter reporter;
    @Nullable
    private StacktraceFactory stacktraceFactory;
    @Nullable
    private Iterable<LifecycleListener> lifecycleListeners;

    public ElasticApmTracerBuilder configurationRegistry(ConfigurationRegistry configurationRegistry) {
        this.configurationRegistry = configurationRegistry;
        return this;
    }

    public ElasticApmTracerBuilder reporter(Reporter reporter) {
        this.reporter = reporter;
        return this;
    }

    public ElasticApmTracerBuilder stacktraceFactory(StacktraceFactory stacktraceFactory) {
        this.stacktraceFactory = stacktraceFactory;
        return this;
    }

    public ElasticApmTracerBuilder lifecycleListeners(List<LifecycleListener> lifecycleListeners) {
        this.lifecycleListeners = lifecycleListeners;
        return this;
    }

    public ElasticApmTracer build() {
        if (configurationRegistry == null) {
            configurationRegistry = getDefaultConfigurationRegistry();
        }
        if (reporter == null) {
            reporter = new ReporterFactory().createReporter(configurationRegistry, null, null);
        }
        if (stacktraceFactory == null) {
            StacktraceConfiguration stackConfig = configurationRegistry.getConfig(StacktraceConfiguration.class);
            stacktraceFactory = new StacktraceFactory.CurrentThreadStackTraceFactory(stackConfig);
        }
        if (lifecycleListeners == null) {
            lifecycleListeners = ServiceLoader.load(LifecycleListener.class, getClass().getClassLoader());
        }
        return new ElasticApmTracer(configurationRegistry, reporter, stacktraceFactory, lifecycleListeners);
    }

    private ConfigurationRegistry getDefaultConfigurationRegistry() {
        try {
            final ConfigurationRegistry configurationRegistry = ConfigurationRegistry.builder()
                .addConfigSource(new PrefixingConfigurationSourceWrapper(new SystemPropertyConfigurationSource(), "elastic.apm."))
                .addConfigSource(new PrefixingConfigurationSourceWrapper(new EnvironmentVariableConfigurationSource(), "ELASTIC_APM_"))
                .addConfigSource(new PropertyFileConfigurationSource("elasticapm.properties"))
                .optionProviders(ServiceLoader.load(ConfigurationOptionProvider.class, ElasticApmTracer.class.getClassLoader()))
                .failOnMissingRequiredValues(true)
                .build();
            configurationRegistry.scheduleReloadAtRate(30, TimeUnit.SECONDS);
            return configurationRegistry;
        } catch (IllegalStateException e) {
            logger.warn(e.getMessage());
            return ConfigurationRegistry.builder()
                .addConfigSource(new SimpleSource("Noop Configuration")
                    .add(CoreConfiguration.ACTIVE, "false")
                    .add(CoreConfiguration.INSTRUMENT, "false")
                    .add(CoreConfiguration.SERVICE_NAME, "none")
                    .add(CoreConfiguration.SAMPLE_RATE, "0"))
                .optionProviders(ServiceLoader.load(ConfigurationOptionProvider.class, ElasticApmTracer.class.getClassLoader()))
                .build();
        }
    }

}
