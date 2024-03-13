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

import co.elastic.apm.agent.common.util.WildcardMatcher;
import org.junit.jupiter.api.Test;
import org.stagemonitor.configuration.ConfigurationRegistry;
import org.stagemonitor.configuration.source.SimpleSource;

import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

class CoreConfigurationTest {

    @Test
    void testWithoutDisabledAndEnabledInstrumentations() {
        CoreConfigurationImpl config = getCoreConfiguration("", "");
        assertThat(config.isInstrumentationEnabled("foo")).isTrue();
        assertThat(config.isInstrumentationEnabled(Collections.singletonList("foo"))).isTrue();
        assertThat(config.isInstrumentationEnabled(Arrays.asList("foo", "bar"))).isTrue();
    }

    @Test
    void testWithDisabledInstrumentations() {
        CoreConfigurationImpl config = getCoreConfiguration("", "foo");
        assertThat(config.isInstrumentationEnabled("foo")).isFalse();
        assertThat(config.isInstrumentationEnabled("bar")).isTrue();
        assertThat(config.isInstrumentationEnabled(Collections.singletonList("foo"))).isFalse();
        assertThat(config.isInstrumentationEnabled(Collections.singletonList("bar"))).isTrue();
        assertThat(config.isInstrumentationEnabled(Arrays.asList("foo", "bar"))).isFalse();
    }

    @Test
    void testWithEnabledInstrumentations() {
        CoreConfigurationImpl config = getCoreConfiguration("foo", "");
        assertThat(config.isInstrumentationEnabled("foo")).isTrue();
        assertThat(config.isInstrumentationEnabled("bar")).isFalse();
        assertThat(config.isInstrumentationEnabled(Collections.singletonList("foo"))).isTrue();
        assertThat(config.isInstrumentationEnabled(Collections.singletonList("bar"))).isFalse();
        assertThat(config.isInstrumentationEnabled(Arrays.asList("foo", "bar"))).isTrue();
    }

    @Test
    void testWithDisabledAndEnabledInstrumentations() {
        CoreConfigurationImpl config = getCoreConfiguration("foo", "foo");
        assertThat(config.isInstrumentationEnabled("foo")).isFalse();
        assertThat(config.isInstrumentationEnabled("bar")).isFalse();
        assertThat(config.isInstrumentationEnabled(Collections.singletonList("foo"))).isFalse();
        assertThat(config.isInstrumentationEnabled(Collections.singletonList("bar"))).isFalse();
        assertThat(config.isInstrumentationEnabled(Arrays.asList("foo", "bar"))).isFalse();
    }

    @Test
    void testWithEnabledInstrumentationsButDisabledExperimentalInstrumentations() {
        CoreConfigurationImpl config = getCoreConfiguration("experimental", "");
        assertThat(config.isInstrumentationEnabled("experimental")).isFalse();
        assertThat(config.isInstrumentationEnabled(Collections.singletonList("experimental"))).isFalse();
    }

    @Test
    void testWithEnabledInstrumentationsAndEnabledExperimentalInstrumentations() {
        CoreConfigurationImpl config = ConfigurationRegistry.builder()
            .addOptionProvider(new CoreConfigurationImpl())
            .addConfigSource(SimpleSource.forTest("enable_instrumentations", "experimental"))
            .addConfigSource(SimpleSource.forTest("disable_instrumentations", ""))
            .addConfigSource(SimpleSource.forTest("enable_experimental_instrumentations", "true"))
            .build()
            .getConfig(CoreConfigurationImpl.class);

        assertThat(config.isInstrumentationEnabled("experimental")).isTrue();
        assertThat(config.isInstrumentationEnabled(Collections.singletonList("experimental"))).isTrue();
        assertThat(config.isInstrumentationEnabled("foobar")).isFalse();
        assertThat(config.isInstrumentationEnabled(Collections.singletonList("foobar"))).isFalse();
    }

    @Test
    void testLegacyDefaultDisabledInstrumentation() {
        CoreConfigurationImpl config = getCoreConfiguration("", "incubating");
        assertThat(config.isInstrumentationEnabled("experimental")).isFalse();
        assertThat(config.isInstrumentationEnabled(Collections.singletonList("experimental"))).isFalse();
    }

    @Test
    void testPreventBroadElasticApmExclusionFromInstrumentation() {
        CoreConfigurationImpl coreConfig = ConfigurationRegistry.builder()
            .addOptionProvider(new CoreConfigurationImpl())
            .addConfigSource(SimpleSource.forTest("classes_excluded_from_instrumentation", "co.elastic.apm.*, co.elastic.*, foo.bar.*"))
            .build()
            .getConfig(CoreConfigurationImpl.class);

        assertThat(coreConfig.getClassesExcludedFromInstrumentation())
            .containsExactly(WildcardMatcher.valueOf("foo.bar.*"));
    }

    private static CoreConfigurationImpl getCoreConfiguration(String enabledInstrumentations, String disabledInstrumentations) {
        ConfigurationRegistry configurationRegistry = ConfigurationRegistry.builder()
            .addOptionProvider(new CoreConfigurationImpl())
            .addConfigSource(SimpleSource.forTest("enable_instrumentations", enabledInstrumentations))
            .addConfigSource(SimpleSource.forTest("disable_instrumentations", disabledInstrumentations))
            .build();

        return configurationRegistry.getConfig(CoreConfigurationImpl.class);
    }
}
