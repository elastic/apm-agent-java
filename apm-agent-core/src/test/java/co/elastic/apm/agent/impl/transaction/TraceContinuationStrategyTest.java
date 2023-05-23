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
package co.elastic.apm.agent.impl.transaction;

import co.elastic.apm.agent.MockReporter;
import co.elastic.apm.agent.MockTracer;
import co.elastic.apm.agent.configuration.CoreConfiguration;
import co.elastic.apm.agent.configuration.SpyConfiguration;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.ElasticApmTracerBuilder;
import co.elastic.apm.agent.impl.TextHeaderMapAccessor;
import co.elastic.apm.agent.impl.sampling.ConstantSampler;
import co.elastic.apm.agent.objectpool.TestObjectPoolFactory;
import co.elastic.apm.agent.report.ApmServerClient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.stagemonitor.configuration.ConfigurationRegistry;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

public class TraceContinuationStrategyTest {

    private static ElasticApmTracer tracerImpl;

    @BeforeAll
    static void setUp() {
        MockTracer.MockInstrumentationSetup mockInstrumentationSetup = MockTracer.createMockInstrumentationSetup();
        TestObjectPoolFactory objectPoolFactory = new TestObjectPoolFactory();
        MockReporter reporter = new MockReporter();
        ConfigurationRegistry config = SpyConfiguration.createSpyConfig();

        ApmServerClient apmServerClient = new ApmServerClient(config);
        apmServerClient = mock(ApmServerClient.class, delegatesTo(apmServerClient));

        tracerImpl = new ElasticApmTracerBuilder()
            .configurationRegistry(config)
            .reporter(reporter)
            .withObjectPoolFactory(objectPoolFactory)
            .withApmServerClient(apmServerClient)
            .buildAndStart();

        assertThat(tracerImpl.isRunning()).isTrue();
    }

    @Test
    void continueTraceFromNonElasticSystem() {
        traceFromSystem(false, CoreConfiguration.TraceContinuationStrategy.CONTINUE, false);
    }

    @Test
    void continueTraceFromElasticSystem() {
        traceFromSystem(true, CoreConfiguration.TraceContinuationStrategy.CONTINUE, false);
    }

    @Test
    void restartTraceFromNonElasticSystem() {
        traceFromSystem(false, CoreConfiguration.TraceContinuationStrategy.RESTART, true);
    }

    @Test
    void restartTraceFromElasticSystem() {
        traceFromSystem(true, CoreConfiguration.TraceContinuationStrategy.RESTART, true);
    }

    @Test
    void restartExternalTraceFromNonElasticSystem() {
        traceFromSystem(false, CoreConfiguration.TraceContinuationStrategy.RESTART_EXTERNAL, true);
    }

    @Test
    void restartExternalTraceFromElasticSystem() {
        traceFromSystem(true, CoreConfiguration.TraceContinuationStrategy.RESTART_EXTERNAL, false);
    }

    @Test
    void testAssumptions() {
        Transaction transaction = new Transaction(tracerImpl);
        assertThat(transaction.getTraceContext().getParentId().toString()).isEqualTo("0000000000000000");
        assertThat(transaction.getTraceContext().getId().toString()).isEqualTo("0000000000000000");
        assertThat(transaction.getTraceContext().getTraceId().toString()).isEqualTo("00000000000000000000000000000000");
        transaction.getTraceContext().asRootSpan(ConstantSampler.of(true));
        assertThat(transaction.getTraceContext().getParentId().toString()).isEqualTo("0000000000000000");
        assertThat(transaction.getTraceContext().getId().toString()).isNotEqualTo("0000000000000000");
        assertThat(transaction.getTraceContext().getTraceId().toString()).isNotEqualTo("00000000000000000000000000000000");
    }

    void traceFromSystem(boolean fromElastic, CoreConfiguration.TraceContinuationStrategy strategy, boolean restartExpected) {
        doReturn(strategy).when(tracerImpl.getConfig(CoreConfiguration.class)).getTraceContinuationStrategy();
        String traceID = "ca6150c33a473fda1f3a7a0b9eb4d143";
        String parentSpanID = "abc345d9029d61ff";

        final Map<String, String> headerMap = new HashMap<>();
        headerMap.put(TraceContext.W3C_TRACE_PARENT_TEXTUAL_HEADER_NAME, "00-"+traceID+"-"+parentSpanID+"-01");
        if (fromElastic) {
            headerMap.put(TraceContext.TRACESTATE_HEADER_NAME, "es=s:1");
        }
        final Transaction transaction = tracerImpl.startChildTransaction(headerMap, TextHeaderMapAccessor.INSTANCE, ConstantSampler.of(false), 0, null);

        if (restartExpected) {
            assertThat(transaction.getTraceContext().getTraceId().toString()).isNotEqualTo(traceID);
            assertThat(transaction.getTraceContext().getTraceId().toString()).isNotEqualTo("00000000000000000000000000000000");
            assertThat(transaction.getSpanLinks().size()).isEqualTo(1);
            assertThat(transaction.getSpanLinks().get(0).getTraceId().toString()).isEqualTo(traceID);
            //note the span link parent ID (rather than ID) is the link span ID - see AbstractSpan.getSpanLinks()
            assertThat(transaction.getSpanLinks().get(0).getParentId().toString()).isEqualTo(parentSpanID);
            assertThat(transaction.getTraceContext().getParentId().toString()).isEqualTo("0000000000000000");
        } else {
            assertThat(transaction.getTraceContext().getTraceId().toString()).isEqualTo(traceID);
            assertThat(transaction.getSpanLinks()).isEmpty();
            assertThat(transaction.getTraceContext().getParentId().toString()).isEqualTo(parentSpanID);
        }
        assertThat(transaction.getTraceContext().getId().toString()).isNotEqualTo(parentSpanID);
        assertThat(transaction.getTraceContext().getId().toString()).isNotEqualTo("0000000000000000");
    }

}
