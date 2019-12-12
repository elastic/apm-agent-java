/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2019 Elastic and contributors
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
package co.elastic.apm.agent.profiler;

import co.elastic.apm.agent.MockReporter;
import co.elastic.apm.agent.MockTracer;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.Scope;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.matcher.WildcardMatcher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.when;

class SamplingProfilerTest {

    private MockReporter reporter;
    private ElasticApmTracer tracer;

    @BeforeEach
    void setUp() {
        reporter = new MockReporter();
        tracer = MockTracer.createRealTracer(reporter);
        when(tracer.getConfigurationRegistry().getConfig(ProfilingConfiguration.class).getIncludedClasses())
            .thenReturn(List.of(WildcardMatcher.valueOf(getClass().getName())));
    }

    @AfterEach
    void tearDown() throws Exception {
        tracer.stop();
    }

    @Test
    void testProfileTransaction() throws Exception {
        Transaction transaction = tracer.startRootTransaction(null).withName("transaction");
        try (Scope scope = transaction.activateInScope()) {
            Span span = transaction.createSpan().withName("span").withType("test");
            try (Scope spanScope = span.activateInScope()) {
                a();
            } finally {
                span.end();
            }
        } finally {
            transaction.end();
        }

        await()
            .pollDelay(10, TimeUnit.MILLISECONDS)
            .timeout(500, TimeUnit.MILLISECONDS)
            .untilAsserted(() -> assertThat(reporter.getSpans()).hasSizeGreaterThanOrEqualTo(2));
        Optional<Span> explicitSpan = reporter.getSpans().stream().filter(s -> s.getNameAsString().equals("span")).findAny();
        assertThat(explicitSpan).isPresent();

        Optional<Span> inferredSpanB = reporter.getSpans().stream().filter(s -> s.getNameAsString().equals("SamplingProfilerTest#b")).findAny();
        assertThat(inferredSpanB).isPresent();

        assertThat(reporter.getSpans().stream()
            .filter(s -> "inferred".equals(s.getSubtype()))
            .filter(s -> s.getTraceContext().isChildOf(explicitSpan.get())))
            .isNotEmpty();
    }

    private void a() throws Exception {
        b();
    }

    private void b() throws Exception {
        Thread.sleep(100);
    }
}
