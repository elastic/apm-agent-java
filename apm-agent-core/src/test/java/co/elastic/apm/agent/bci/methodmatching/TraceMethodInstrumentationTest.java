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
package co.elastic.apm.agent.bci.methodmatching;

import co.elastic.apm.agent.MockReporter;
import co.elastic.apm.agent.bci.ElasticApmAgent;
import co.elastic.apm.agent.configuration.CoreConfiguration;
import co.elastic.apm.agent.configuration.SpyConfiguration;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.ElasticApmTracerBuilder;
import net.bytebuddy.agent.ByteBuddyAgent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.stagemonitor.configuration.ConfigurationRegistry;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class TraceMethodInstrumentationTest {

    private MockReporter reporter;
    private ElasticApmTracer tracer;

    @BeforeEach
    void setUp() {
        reporter = new MockReporter();
        ConfigurationRegistry config = SpyConfiguration.createSpyConfig();
        when(config.getConfig(CoreConfiguration.class).getTraceMethods()).thenReturn(Collections.singletonList(
            MethodMatcher.of("private co.elastic.apm.agent.bci.methodmatching.TraceMethodInstrumentationTest#traceMe*()"))
        );
        tracer = new ElasticApmTracerBuilder()
            .configurationRegistry(config)
            .reporter(reporter)
            .build();
        ElasticApmAgent.initInstrumentation(tracer, ByteBuddyAgent.install());
    }

    @AfterEach
    void tearDown() {
        ElasticApmAgent.reset();
    }

    @Test
    void testTraceMethod() {
        traceMe();
        assertThat(reporter.getTransactions()).hasSize(1);
        assertThat(reporter.getSpans()).hasSize(1);
    }

    @Test
    void testNotMatched_VisibilityModifier() {
        traceMeNot();
        assertThat(reporter.getTransactions()).isEmpty();
    }

    @Test
    void testNotMatched_Parameters() {
        traceMeNot(false);
        assertThat(reporter.getTransactions()).isEmpty();
    }

    // not traced because visibility modifier does not match
    public void traceMeNot() {
    }

    private void traceMeNot(boolean doesNotMatchParameterMatcher) {
    }

    private void traceMe() {
        traceMeToo();
    }

    private void traceMeToo() {

    }
}
