/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2020 Elastic and contributors
 * %%
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
 * #L%
 */
package co.elastic.apm.agent.micrometer;

import co.elastic.apm.agent.MockReporter;
import co.elastic.apm.agent.MockTracer;
import co.elastic.apm.agent.bci.ElasticApmAgent;
import co.elastic.apm.agent.configuration.SpyConfiguration;
import co.elastic.apm.agent.report.ReporterConfiguration;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import net.bytebuddy.agent.ByteBuddyAgent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.stagemonitor.configuration.ConfigurationRegistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class MicrometerInstrumentationTest {

    private MockReporter reporter;

    @BeforeEach
    void setUp() {
        ConfigurationRegistry config = SpyConfiguration.createSpyConfig();
        when(config.getConfig(ReporterConfiguration.class).getMetricsIntervalMs()).thenReturn(5L);
        reporter = new MockReporter();
        ElasticApmAgent.initInstrumentation(MockTracer.createRealTracer(reporter, config), ByteBuddyAgent.install());
    }

    @AfterEach
    void tearDown() {
        ElasticApmAgent.reset();
    }

    @Test
    void testRegisterMeterRegisty() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        registry.counter("foo").increment();
        reporter.awaitUntilAsserted(() -> assertThat(reporter.getBytes()).isNotEmpty());
    }
}
