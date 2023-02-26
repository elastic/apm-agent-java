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
package co.elastic.apm.agent.pluginapi;

import co.elastic.apm.agent.MockTracer;
import co.elastic.apm.agent.bci.ElasticApmAgent;
import co.elastic.apm.agent.bci.TracerAwareInstrumentation;
import co.elastic.apm.agent.configuration.CoreConfiguration;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.sdk.ElasticApmInstrumentation;
import co.elastic.apm.api.ElasticApm;
import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.stagemonitor.configuration.ConfigurationRegistry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static org.assertj.core.api.Assertions.assertThat;

class ConfigInstrumentationTest {

    private static final Integer INVALID_TRANSACTION_MAX_SPANS = -37;

    private ElasticApmTracer tracer;
    private ConfigurationRegistry configurationRegistry;
    private CoreConfiguration coreConfig;

    @BeforeEach
    void setup() {
        tracer = MockTracer.createRealTracer();
        configurationRegistry = tracer.getConfigurationRegistry();
        coreConfig = configurationRegistry.getConfig(CoreConfiguration.class);
    }

    @AfterEach
    void reset() {
        ElasticApmAgent.reset();
    }

    @Test
    void testConfigAccess() {
        ArrayList<ElasticApmInstrumentation> instrumentations = new ArrayList<>();
        instrumentations.add(new MaxTransInstrumentation());
        instrumentations.add(new ElasticApmApiInstrumentation.ConfigInstrumentation());
        ElasticApmAgent.initInstrumentation(tracer, ByteBuddyAgent.install(), instrumentations);
        assertThat(coreConfig.getTransactionMaxSpans()).isNotEqualTo(INVALID_TRANSACTION_MAX_SPANS);
        assertThat(configValue()).isEqualTo(coreConfig.getTransactionMaxSpans());
    }

    Integer configValue() {
        return INVALID_TRANSACTION_MAX_SPANS;
    }

    public static class MaxTransInstrumentation extends TracerAwareInstrumentation {
        public static class AdviceClass {
            @Advice.AssignReturned.ToReturned
            @Advice.OnMethodExit(inline = false)
            public static Integer onMethodExit() {
                return (Integer) ElasticApm.getConfig("transaction_max_spans");
            }
        }

        @Override
        public ElementMatcher<? super TypeDescription> getTypeMatcher() {
            return named(ConfigInstrumentationTest.class.getName());
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return ElementMatchers.nameEndsWithIgnoreCase("configValue");
        }

        @Override
        public Collection<String> getInstrumentationGroupNames() {
            return Collections.emptyList();
        }

    }

}
