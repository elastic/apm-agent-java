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
import co.elastic.apm.agent.configuration.SpyConfiguration;
import co.elastic.apm.agent.configuration.converter.TimeDuration;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.Scope;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.matcher.WildcardMatcher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.stagemonitor.configuration.ConfigurationRegistry;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.when;

// async-profiler doesn't work on Windows
@DisabledOnOs(OS.WINDOWS)
class SamplingProfilerTest {

    private MockReporter reporter;
    private ElasticApmTracer tracer;
    private SamplingProfiler profiler;

    @BeforeEach
    void setUp() {
        reporter = new MockReporter();
        ConfigurationRegistry config = SpyConfiguration.createSpyConfig();
        ProfilingConfiguration profilingConfig = config.getConfig(ProfilingConfiguration.class);
        when(profilingConfig.getIncludedClasses()).thenReturn(List.of(WildcardMatcher.valueOf(getClass().getName())));
        when(profilingConfig.isProfilingEnabled()).thenReturn(true);
        when(profilingConfig.getProfilingDuration()).thenReturn(TimeDuration.of("500ms"));
        when(profilingConfig.getProfilingInterval()).thenReturn(TimeDuration.of("500ms"));
        when(profilingConfig.getSamplingInterval()).thenReturn(TimeDuration.of("5ms"));
        tracer = MockTracer.createRealTracer(reporter, config);
        profiler = tracer.getLifecycleListener(ProfilingFactory.class).getProfiler();
        // ensure profiler is initialized
        await()
            .pollDelay(10, TimeUnit.MILLISECONDS)
            .timeout(6000, TimeUnit.MILLISECONDS)
            .until(() -> profiler.getProfilingSessions() > 1);
    }

    @AfterEach
    void tearDown() {
        tracer.stop();
    }

    @Test
    void testProfileTransaction() throws Exception {
        Transaction transaction = tracer.startRootTransaction(null).withName("transaction");
        try (Scope scope = transaction.activateInScope()) {
            // makes sure that the rest will be captured by another profiling session
            // this tests that restoring which threads to profile works
            Thread.sleep(600);
            aInferred(transaction);
        } finally {
            transaction.end();
        }

        await()
            .pollDelay(10, TimeUnit.MILLISECONDS)
            .timeout(5000, TimeUnit.MILLISECONDS)
            .untilAsserted(() -> assertThat(reporter.getSpans()).hasSize(5));

        Optional<Span> testProfileTransaction = reporter.getSpans().stream().filter(s -> s.getNameAsString().equals("SamplingProfilerTest#testProfileTransaction")).findAny();
        assertThat(testProfileTransaction).isPresent();
        assertThat(testProfileTransaction.get().isChildOf(transaction)).isTrue();

        Optional<Span> inferredSpanA = reporter.getSpans().stream().filter(s -> s.getNameAsString().equals("SamplingProfilerTest#aInferred")).findAny();
        assertThat(inferredSpanA).isPresent();
        assertThat(inferredSpanA.get().isChildOf(testProfileTransaction.get())).isTrue();

        Optional<Span> explicitSpanB = reporter.getSpans().stream().filter(s -> s.getNameAsString().equals("bExplicit")).findAny();
        assertThat(explicitSpanB).isPresent();
        // not supported yet - an explicit span can't be a span of an inferred one
        // assertThat(explicitSpanB.get().isChildOf(inferredSpanA.get())).isTrue();

        Optional<Span> inferredSpanC = reporter.getSpans().stream().filter(s -> s.getNameAsString().equals("SamplingProfilerTest#cInferred")).findAny();
        assertThat(inferredSpanC).isPresent();
        assertThat(inferredSpanC.get().isChildOf(explicitSpanB.get())).isTrue();

        Optional<Span> inferredSpanD = reporter.getSpans().stream().filter(s -> s.getNameAsString().equals("SamplingProfilerTest#dInferred")).findAny();
        assertThat(inferredSpanD).isPresent();
        assertThat(inferredSpanD.get().isChildOf(inferredSpanC.get())).isTrue();
    }

    private void aInferred(Transaction transaction) throws Exception {
        Span span = transaction.createSpan().withName("bExplicit").withType("test");
        try (Scope spanScope = span.activateInScope()) {
            cInferred();
        } finally {
            span.end();
        }
        Thread.sleep(50);
    }

    private void cInferred() throws Exception {
        dInferred();
        Thread.sleep(50);
    }

    private void dInferred() throws Exception {
        Thread.sleep(50);
    }
}
