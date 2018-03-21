package co.elastic.apm.configuration;

import org.stagemonitor.configuration.ConfigurationOptionProvider;
import org.stagemonitor.configuration.ConfigurationRegistry;
import org.stagemonitor.configuration.source.SimpleSource;

import java.util.ServiceLoader;

import static org.mockito.Mockito.spy;

public class SpyConfiguration {

    public static final String CONFIG_SOURCE_NAME = "test config source";

    /**
     * Creates a configuration registry where all {@link ConfigurationOptionProvider}s are wrapped with
     * {@link org.mockito.Mockito#spy(Object)}
     * <p>
     * That way, the default configuration values are returned but can be overridden by {@link org.mockito.Mockito#when(Object)}
     *
     * @return a syp configuration registry
     */
    public static ConfigurationRegistry createSpyConfig() {
        ConfigurationRegistry.Builder builder = ConfigurationRegistry.builder();
        for (ConfigurationOptionProvider options : ServiceLoader.load(ConfigurationOptionProvider.class)) {
            builder.addOptionProvider(spy(options));
        }
        return builder
            .addConfigSource(new SimpleSource(CONFIG_SOURCE_NAME).add("service_name", "elastic-apm-test"))
            .build();
    }
}
