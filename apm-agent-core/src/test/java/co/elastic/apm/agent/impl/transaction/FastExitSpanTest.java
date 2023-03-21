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
import co.elastic.apm.agent.tracer.Outcome;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

class FastExitSpanTest {

    protected static ElasticApmTracer tracer;
    protected static MockReporter reporter;

    @BeforeAll
    public static void setUp() {
        MockTracer.MockInstrumentationSetup mockInstrumentationSetup = MockTracer.createMockInstrumentationSetup();
        tracer = mockInstrumentationSetup.getTracer();
        reporter = mockInstrumentationSetup.getReporter();

        SpanConfiguration spanConfiguration = mockInstrumentationSetup.getConfig().getConfig(SpanConfiguration.class);
        doReturn(true).when(spanConfiguration).isSpanCompressionEnabled();
        doReturn(TimeDuration.of("50ms")).when(spanConfiguration).getSpanCompressionExactMatchMaxDuration();
        doReturn(TimeDuration.of("50ms")).when(spanConfiguration).getExitSpanMinDuration();

        assertThat(tracer.isRunning()).isTrue();
    }

    @AfterEach
    void resetReporter() {
        reporter.reset();
    }

    @Test
    void testExitSpanBelowDuration() {
        Transaction transaction = startTransaction();
        try {
            // each combination of (outcome,service target type, service target name) should have its own bucket
            for (Outcome outcome : Outcome.values()) {
                // without service target name
                startExitSpan(transaction, 0L).withOutcome(outcome).end(49_999L);

                // with service target name
                Span spanWithServiceTargetName = startExitSpan(transaction, 0L).withOutcome(outcome);
                spanWithServiceTargetName.getContext().getDb().withInstance("db-name");
                spanWithServiceTargetName.end(49_999L);
            }

        } finally {
            transaction.end();
        }

        assertThat(reporter.getSpans()).isEmpty();

        SpanCount spanCount = reporter.getFirstTransaction().getSpanCount();
        assertThat(spanCount.getReported().get()).isEqualTo(0);
        assertThat(spanCount.getDropped().get()).isEqualTo(6);

        DroppedSpanStats droppedSpanStats = reporter.getFirstTransaction().getDroppedSpanStats();

        for (Outcome outcome : Outcome.values()) {
            Arrays.asList("db-name", null).forEach(v ->{
                DroppedSpanStats.Stats stats = droppedSpanStats.getStats("postgresql", v, outcome);
                assertThat(stats).isNotNull();
                assertThat(stats.getCount()).isEqualTo(1);
                assertThat(stats.getSum()).isEqualTo(49_999L);
            });
        }

    }



    @Test
    void testCompositeExitSpanBelowDurationAndMoreThanOneDroppedSpanStatsEntry() {
        Transaction transaction = startTransaction();
        try {
            Span span = startExitSpan(transaction, 0L);
            span.end(10_000L);
            startExitSpan(transaction, 10_000L).end(20_000L);
            startExitSpan(transaction, 20_000L).end(30_000L);
            assertThat(span.isComposite()).isTrue();
            //second span destination to ensure more than one dropped span stats entry
            Span span2 = startExitSpan(transaction, 30_000L, "mysql");
            span2.end(40_000L);
            startExitSpan(transaction, 40_000L, "mysql").end(50_000L);
            assertThat(span2.isComposite()).isTrue();
        } finally {
            transaction.end();
        }

        assertThat(reporter.getSpans()).isEmpty();

        SpanCount spanCount = reporter.getFirstTransaction().getSpanCount();
        assertThat(spanCount.getReported().get()).isEqualTo(0);
        assertThat(spanCount.getDropped().get()).isEqualTo(5);

        DroppedSpanStats droppedSpanStats = reporter.getFirstTransaction().getDroppedSpanStats();

        assertThat(droppedSpanStats.getStats("postgresql", null, Outcome.SUCCESS).getCount()).isEqualTo(3);
        assertThat(droppedSpanStats.getStats("postgresql", null, Outcome.SUCCESS).getSum()).isEqualTo(30_000L);
    }

    @Test
    void testExitSpanAboveDuration() {
        Transaction transaction = startTransaction();
        try {
            startExitSpan(transaction, 0L).end(50_001L);
        } finally {
            transaction.end();
        }

        assertThat(reporter.getSpans()).hasSize(1);

        SpanCount spanCount = reporter.getFirstTransaction().getSpanCount();
        assertThat(spanCount.getReported().get()).isEqualTo(1);
        assertThat(spanCount.getDropped().get()).isEqualTo(0);

        DroppedSpanStats droppedSpanStats = reporter.getFirstTransaction().getDroppedSpanStats();
        assertThat(droppedSpanStats.getStats("postgresql", null, Outcome.SUCCESS)).isNull();
    }

    @Test
    void testCompositeExitSpanAboveDuration() {
        Transaction transaction = startTransaction();
        try {
            Span span = startExitSpan(transaction, 0L);
            span.end(20_000L);
            startExitSpan(transaction, 20_000L).end(40_000L);
            startExitSpan(transaction, 40_000L).end(60_000L);
            assertThat(span.isComposite()).isTrue();
        } finally {
            transaction.end();
        }

        assertThat(reporter.getSpans()).hasSize(1);

        SpanCount spanCount = reporter.getFirstTransaction().getSpanCount();
        assertThat(spanCount.getReported().get()).isEqualTo(1);
        assertThat(spanCount.getDropped().get()).isEqualTo(2);

        DroppedSpanStats droppedSpanStats = reporter.getFirstTransaction().getDroppedSpanStats();

        assertThat(droppedSpanStats.getStats("postgresql", null, Outcome.SUCCESS)).isNull();
    }

    private Transaction startTransaction() {
        return tracer.startRootTransaction(null).withName("Some Transaction");
    }

    protected Span startExitSpan(AbstractSpan<?> parent, long startTimestamp) {
        return startExitSpan(parent, startTimestamp, "postgresql");
    }

    protected Span startExitSpan(AbstractSpan<?> parent, long startTimestamp, String subtype) {
        Span span = parent.createExitSpan().withName("Some Name").withType("db").withSubtype(subtype);
        span.getContext().getDestination().withAddress("127.0.0.1").withPort(5432);
        span.setStartTimestamp(startTimestamp);

        return span;
    }
}
