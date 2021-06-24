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
package co.elastic.apm.opentracing;

import co.elastic.apm.agent.AbstractInstrumentationTest;
import io.opentracing.Span;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class OpenTracingBridgeCloseTest extends AbstractInstrumentationTest {
    private ElasticApmTracer apmTracer = new ElasticApmTracer();

    @BeforeEach
    void setUp() {
        // OT always leaks the spans
        // see co.elastic.apm.agent.opentracing.impl.ApmSpanBuilderInstrumentation.CreateSpanInstrumentation.doCreateTransactionOrSpan
        disableRecyclingValidation();
    }

    @Test
    void testCloseTracer() {
        Span transactionSpan = apmTracer.buildSpan("transactionSpan").start();
        Span span = apmTracer.buildSpan("span").asChildOf(transactionSpan).start();
        Span nestedSpan = apmTracer.buildSpan("nestedSpan").asChildOf(span).start();
        nestedSpan.finish();
        apmTracer.close();
        span.finish();
        transactionSpan.finish();
        // events finished after the tracer is closed should not be reported
        assertThat(reporter.getSpans()).hasSize(1);
        assertThat(reporter.getTransactions()).hasSize(0);
    }
}
