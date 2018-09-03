package co.elastic.apm.configuration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.stagemonitor.configuration.ConfigurationOption;
import org.stagemonitor.configuration.ConfigurationOptionProvider;
import org.stagemonitor.configuration.ConfigurationRegistry;
import org.stagemonitor.configuration.source.SimpleSource;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class StartupInfoTest {

    private ConfigurationRegistry config;
    private StartupInfo startupInfo;
    private Logger logger;

    @BeforeEach
    void setUp() {
        this.config = ConfigurationRegistry.builder()
            .addOptionProvider(new ConfigurationOptionProvider() {
                private final ConfigurationOption<String> testOption = ConfigurationOption.<String>stringOption()
                    .key("test")
                    .aliasKeys("test_alias")
                    .dynamic(true)
                    .buildWithDefault("default");
            })
            .addConfigSource(new SimpleSource())
            .build();
        startupInfo = new StartupInfo();
        logger = mock(Logger.class);
    }

    @Test
    void testLogDeprecatedKey() throws Exception {
        config.save("test_alias", "0.5", SimpleSource.NAME);
        startupInfo.logConfiguration(config, logger);
        verify(logger).warn("Detected usage of an old configuration key: '{}'. Please use '{}' instead.", "test_alias", "test");
    }
}
