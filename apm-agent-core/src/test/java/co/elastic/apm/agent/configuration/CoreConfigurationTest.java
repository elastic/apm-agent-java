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
package co.elastic.apm.agent.configuration;

import co.elastic.apm.agent.configuration.source.SystemPropertyConfigurationSource;
import org.junit.jupiter.api.Test;
import org.stagemonitor.configuration.ConfigurationRegistry;

import java.util.Arrays;
import java.util.Collections;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class CoreConfigurationTest {

    private final Properties systemProperties = System.getProperties();

    @Test
    void testWithoutDisabledAndEnabledInstrumentations() {
        systemProperties.put("enable_instrumentations", "");
        systemProperties.put("disable_instrumentations", "");
        try {
            CoreConfiguration config = getCoreConfiguration();
            assertThat(config.isInstrumentationEnabled("foo")).isTrue();
            assertThat(config.isInstrumentationEnabled(Collections.singletonList("foo"))).isTrue();
            assertThat(config.isInstrumentationEnabled(Arrays.asList("foo", "bar"))).isTrue();
        } finally {
            systemProperties.remove("enable_instrumentations");
            systemProperties.remove("disable_instrumentations");
        }
    }

    @Test
    void testWithDisabledInstrumentations() {
        systemProperties.put("enable_instrumentations", "");
        systemProperties.put("disable_instrumentations", "foo");
        try {
            CoreConfiguration config = getCoreConfiguration();
            assertThat(config.isInstrumentationEnabled("foo")).isFalse();
            assertThat(config.isInstrumentationEnabled("bar")).isTrue();
            assertThat(config.isInstrumentationEnabled(Collections.singletonList("foo"))).isFalse();
            assertThat(config.isInstrumentationEnabled(Collections.singletonList("bar"))).isTrue();
            assertThat(config.isInstrumentationEnabled(Arrays.asList("foo", "bar"))).isFalse();
        } finally {
            systemProperties.remove("enable_instrumentations");
            systemProperties.remove("disable_instrumentations");
        }
    }

    @Test
    void testWithEnabledInstrumentations() {
        systemProperties.put("enable_instrumentations", "foo");
        systemProperties.put("disable_instrumentations", "");
        try {
            CoreConfiguration config = getCoreConfiguration();
            assertThat(config.isInstrumentationEnabled("foo")).isTrue();
            assertThat(config.isInstrumentationEnabled("bar")).isFalse();
            assertThat(config.isInstrumentationEnabled(Collections.singletonList("foo"))).isTrue();
            assertThat(config.isInstrumentationEnabled(Collections.singletonList("bar"))).isFalse();
            assertThat(config.isInstrumentationEnabled(Arrays.asList("foo", "bar"))).isTrue();
        } finally {
            systemProperties.remove("enable_instrumentations");
            systemProperties.remove("disable_instrumentations");
        }
    }

    @Test
    void testWithDisabledAndEnabledInstrumentations() {
        systemProperties.put("enable_instrumentations", "foo");
        systemProperties.put("disable_instrumentations", "foo");
        try {
            CoreConfiguration config = getCoreConfiguration();
            assertThat(config.isInstrumentationEnabled("foo")).isFalse();
            assertThat(config.isInstrumentationEnabled("bar")).isFalse();
            assertThat(config.isInstrumentationEnabled(Collections.singletonList("foo"))).isFalse();
            assertThat(config.isInstrumentationEnabled(Collections.singletonList("bar"))).isFalse();
            assertThat(config.isInstrumentationEnabled(Arrays.asList("foo", "bar"))).isFalse();
        } finally {
            systemProperties.remove("enable_instrumentations");
            systemProperties.remove("disable_instrumentations");
        }
    }

    @Test
    void testLegacyDefaultDisabledInstrumentation() {
        systemProperties.put("enable_instrumentations", "");
        systemProperties.put("disable_instrumentations", "incubating");
        try {
            CoreConfiguration config = getCoreConfiguration();
            assertThat(config.isInstrumentationEnabled("experimental")).isFalse();
            assertThat(config.isInstrumentationEnabled(Collections.singletonList("experimental"))).isFalse();
        } finally {
            systemProperties.remove("enable_instrumentations");
            systemProperties.remove("disable_instrumentations");
        }
    }

    private static CoreConfiguration getCoreConfiguration() {
        ConfigurationRegistry configurationRegistry = ConfigurationRegistry.builder().addOptionProvider(new CoreConfiguration()).addConfigSource(new SystemPropertyConfigurationSource()).build();

        return configurationRegistry.getConfig(CoreConfiguration.class);
    }
}
