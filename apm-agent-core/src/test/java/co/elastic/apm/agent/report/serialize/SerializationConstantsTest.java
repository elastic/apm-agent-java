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
package co.elastic.apm.agent.report.serialize;

import co.elastic.apm.agent.MockTracer;
import co.elastic.apm.agent.bci.ElasticApmAgent;
import co.elastic.apm.agent.configuration.CoreConfiguration;
import co.elastic.apm.agent.configuration.SpyConfiguration;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import net.bytebuddy.agent.ByteBuddyAgent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.stagemonitor.configuration.ConfigurationRegistry;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.mockito.Mockito.doReturn;

class SerializationConstantsTest {

    @AfterEach
    void after() {
        ElasticApmAgent.reset();
    }

    @Test
    void getDefaults() {
        assertThat(SerializationConstants.BUFFER_SIZE).isEqualTo(16384);
        assertThat(SerializationConstants.MAX_VALUE_LENGTH).isEqualTo(1024);
        assertThat(SerializationConstants.getMaxLongStringValueLength()).isEqualTo(10000);
    }

    @Test
    void overrideDefaults() {

        ConfigurationRegistry config = SpyConfiguration.createSpyConfig();
        CoreConfiguration configuration = config.getConfig(CoreConfiguration.class);
        doReturn(42).when(configuration).getLongFieldMaxLength();

        MockTracer.MockInstrumentationSetup mockInstrumentationSetup = MockTracer.createMockInstrumentationSetup(config);
        ElasticApmTracer tracer = mockInstrumentationSetup.getTracer();
        assertThat(tracer.isRunning()).isTrue();
        ElasticApmAgent.initInstrumentation(tracer, ByteBuddyAgent.install());

        assertThat(SerializationConstants.getMaxLongStringValueLength()).isEqualTo(42);
    }
}
