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
import co.elastic.apm.agent.configuration.SpanConfiguration;
import co.elastic.apm.agent.configuration.converter.TimeDuration;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

public class SpanCompressionIT {

    private static final int numberOfSpans = 2 * Runtime.getRuntime().availableProcessors();

    private static final ExecutorService executor = Executors.newFixedThreadPool(numberOfSpans);

    private static ElasticApmTracer tracer;
    private static MockReporter reporter;

    @BeforeAll
    public static void setUp() {
        MockTracer.MockInstrumentationSetup mockInstrumentationSetup = MockTracer.createMockInstrumentationSetup();
        tracer = mockInstrumentationSetup.getTracer();
        reporter = mockInstrumentationSetup.getReporter();

        SpanConfiguration spanConfiguration = mockInstrumentationSetup.getConfig().getConfig(SpanConfiguration.class);
        when(spanConfiguration.isSpanCompressionEnabled()).thenReturn(true);
        when(spanConfiguration.getSpanCompressionExactMatchMaxDuration()).thenReturn(TimeDuration.of("10ms"));
        when(spanConfiguration.getSpanCompressionSameKindMaxDuration()).thenReturn(TimeDuration.of("10ms"));

        assertThat(tracer.isRunning()).isTrue();
    }

    @BeforeEach
    void resetReporter() {
        reporter.reset();
    }

    @AfterAll
    static void shutdownExecutor() {
        executor.shutdown();
    }

    @RepeatedTest(100)
    void testParallelExitSpanCreation() {
        runInTransactionScope((transaction, i) -> {
            return () -> createExitSpan(transaction, i, 1000L + i);
        });

        assertReportedSpans(reporter.getSpans());
    }

    @RepeatedTest(100)
    void testParallelExitAndNonExitSpanCreation() {
        runInTransactionScope((transaction, i) -> {
            if (ThreadLocalRandom.current().nextInt(100) < 10) {
                return () -> createSpan(transaction, i, 1000L + i);
            } else {
                return () -> createExitSpan(transaction, i, 1000L + i);
            }
        });

        assertReportedSpans(reporter.getSpans());
    }

    private static void runInTransactionScope(BiFunction<AbstractSpan<?>, Integer, Runnable> r) {
        Transaction transaction = tracer.startRootTransaction(null).withName("Some Transaction");
        try {
            CompletableFuture<?>[] tasks = new CompletableFuture<?>[numberOfSpans];
            for (int i = 0; i < numberOfSpans; ++i) {
                tasks[i] = CompletableFuture.runAsync(r.apply(transaction, i), executor);
            }

            CompletableFuture.allOf(tasks).join();
        } finally {
            transaction.end();
        }
    }

    private static void createSpan(AbstractSpan<?> parent, long startTimestamp, long endTimestamp) {
        Span span = parent.createSpan().withName("Some Name").withType("app");
        span.setStartTimestamp(startTimestamp);
        span.end(endTimestamp);
    }

    private static void createExitSpan(AbstractSpan<?> parent, long startTimestamp, long endTimestamp) {
        Span span = parent.createSpan().asExit().withName("Some Other Name").withType("db").withSubtype("postgresql");
        span.getContext().getDestination().withAddress("127.0.0.1").withPort(5432);
        span.setStartTimestamp(startTimestamp);
        span.end(endTimestamp);
    }

    private static void assertReportedSpans(List<Span> reportedSpans) {
        int numberOfReportedSpans = reportedSpans.stream()
            .mapToInt(s -> s.isComposite() ? s.getComposite().getCount() : 1)
            .sum();
        assertThat(numberOfReportedSpans).isEqualTo(numberOfReportedSpans);
        assertThat(reportedSpans).filteredOn(Span::isComposite)
            .allSatisfy(span -> {
                int numberOfCompositeSpans = span.getComposite().getCount();
                assertThat(span.getDuration()).isGreaterThanOrEqualTo(1000L + numberOfCompositeSpans - 1L);
                assertThat(span.getComposite().getSum()).isEqualTo(1000L * numberOfCompositeSpans);
            });
    }
}
