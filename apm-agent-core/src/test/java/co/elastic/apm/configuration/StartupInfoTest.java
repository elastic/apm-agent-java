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
package co.elastic.apm.configuration;

import co.elastic.apm.configuration.converter.TimeDuration;
import co.elastic.apm.configuration.converter.TimeDurationValueConverter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.stagemonitor.configuration.ConfigurationOption;
import org.stagemonitor.configuration.ConfigurationOptionProvider;
import org.stagemonitor.configuration.ConfigurationRegistry;
import org.stagemonitor.configuration.source.SimpleSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class StartupInfoTest {

    private ConfigurationRegistry configurationRegistry;
    private StartupInfo startupInfo;
    private Logger logger;
    private TestConfig config;

    @BeforeEach
    void setUp() {
        config = new TestConfig();
        this.configurationRegistry = ConfigurationRegistry.builder()
            .addOptionProvider(config)
            .addOptionProvider(new CoreConfiguration())
            .addConfigSource(new SimpleSource().add("duration", "1"))
            .build();
        startupInfo = new StartupInfo();
        logger = mock(Logger.class);
    }

    @Test
    void testLogDeprecatedKey() throws Exception {
        configurationRegistry.save("test_alias", "0.5", SimpleSource.NAME);
        startupInfo.logConfiguration(configurationRegistry, logger);
        verify(logger).warn("Detected usage of an old configuration key: '{}'. Please use '{}' instead.", "test_alias", "test");
    }

    @Test
    void testLogDeprecationWarningWhenNotSpecifyingTimeUnit() throws Exception {
        assertThat(config.duration.get().getMillis()).isEqualTo(1);
        assertThat(config.duration.getValueAsString()).isEqualTo("1");

        startupInfo.logConfiguration(configurationRegistry, logger);
        verify(logger).warn("DEPRECATION WARNING: {}: '{}' (source: {}) is not using a time unit. Please use one of 'ms', 's' or 'm'.",
            "duration", "1", SimpleSource.NAME);
    }

    private static class TestConfig extends ConfigurationOptionProvider {
        final ConfigurationOption<String> testOption = ConfigurationOption.<String>stringOption()
            .key("test")
            .aliasKeys("test_alias")
            .dynamic(true)
            .buildWithDefault("default");
        final ConfigurationOption<TimeDuration> duration = TimeDurationValueConverter.durationOption("ms")
            .key("duration")
            .dynamic(true)
            .buildWithDefault(TimeDuration.of("5ms"));

    }
}
