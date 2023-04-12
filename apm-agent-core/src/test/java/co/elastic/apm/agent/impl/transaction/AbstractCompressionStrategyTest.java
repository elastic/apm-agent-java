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
import co.elastic.apm.agent.impl.context.ServiceTarget;
import co.elastic.apm.agent.tracer.Outcome;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.function.Consumer;

import static co.elastic.apm.agent.testutils.assertions.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

abstract class AbstractCompressionStrategyTest {

    protected static ElasticApmTracer tracer;
    protected static MockReporter reporter;

    private final String compressionStrategy;

    AbstractCompressionStrategyTest(String compressionStrategy) {
        this.compressionStrategy = compressionStrategy;
    }

    @BeforeAll
    public static void setUp() {
        MockTracer.MockInstrumentationSetup mockInstrumentationSetup = MockTracer.createMockInstrumentationSetup();
        tracer = mockInstrumentationSetup.getTracer();

        reporter = mockInstrumentationSetup.getReporter();
        reporter.disableCheckStrictSpanType();
        reporter.disableCheckUnknownOutcome();
        reporter.disableCheckDestinationAddress();

        SpanConfiguration spanConfiguration = mockInstrumentationSetup.getConfig().getConfig(SpanConfiguration.class);
        doReturn(TimeDuration.of("50ms")).when(spanConfiguration).getSpanCompressionExactMatchMaxDuration();
        doReturn(TimeDuration.of("50ms")).when(spanConfiguration).getSpanCompressionSameKindMaxDuration();

        assertThat(tracer.isRunning()).isTrue();
    }

    @AfterEach
    void resetReporter() {
        reporter.reset();
    }

    @Test
    void testCompositeSpanIsNotCreatedWhenCompressionIsNotEnabled() {
        doReturn(false).when(tracer.getConfig(SpanConfiguration.class)).isSpanCompressionEnabled();
        try {
            runInTransactionScope(t -> {
                startExitSpan(t).end();
                startExitSpan(t).end();
                startExitSpan(t).end();
            });

            List<Span> reportedSpans = reporter.getSpans();
            assertThat(reportedSpans).hasSize(3);
            assertThat(reportedSpans).filteredOn(Span::isComposite).isEmpty();
        } finally {
            doReturn(true).when(tracer.getConfig(SpanConfiguration.class)).isSpanCompressionEnabled();
        }
    }

    @Test
    void testCompositeSpanIsCreated() {
        runInTransactionScope(t -> {
            Span span1 = startExitSpan(t);
            span1.setStartTimestamp(0);
            span1.end(1234);
            Span span2 = startExitSpan(t);
            span2.setStartTimestamp(2345);
            span2.end(3456);
            Span span3 = startExitSpan(t);
            span3.setStartTimestamp(3456);
            span3.end(4567);
            Span span4 = startExitSpan(t);
            span4.setStartTimestamp(3467);
            span4.end(4556);
        });

        runInTransactionScope(t -> {
            startExitSpan(t).end();
        });

        List<Span> reportedSpans = reporter.getSpans();
        assertThat(reportedSpans).hasSize(2);
        assertCompositeSpan(reportedSpans.get(0), 4);
        assertThat(reportedSpans.get(0).getComposite().getSum()).isEqualTo(1234 + (3456 - 2345) + (4567 - 3456) + (4556 - 3467));
        assertThat(reportedSpans.get(0).getDuration()).isEqualTo(4567);
        assertThat(reportedSpans.get(1).isComposite()).isFalse();

        SpanCount spanCount = reporter.getFirstTransaction().getSpanCount();
        assertThat(spanCount.getReported().get()).isEqualTo(1);
        assertThat(spanCount.getDropped().get()).isEqualTo(3);
    }

    @Test
    void testUnknownOutcomeStopsRegularCompression() {
        runInTransactionScope(t -> {
            startExitSpan(t).end();
            startExitSpan(t).withOutcome(Outcome.UNKNOWN).end();
        });

        List<Span> reportedSpans = reporter.getSpans();
        assertThat(reportedSpans).hasSize(2);
        assertThat(reportedSpans.get(0).isComposite()).isFalse();
        assertThat(reportedSpans.get(1).isComposite()).isFalse();

        SpanCount spanCount = reporter.getFirstTransaction().getSpanCount();
        assertThat(spanCount.getReported().get()).isEqualTo(2);
        assertThat(spanCount.getDropped().get()).isEqualTo(0);
    }

    @Test
    void testUnknownOutcomeStopsCompositeCompression() {
        runInTransactionScope(t -> {
            startExitSpan(t).end();
            startExitSpan(t).end();
            startExitSpan(t).withOutcome(Outcome.UNKNOWN).end();
        });

        List<Span> reportedSpans = reporter.getSpans();
        assertThat(reportedSpans).hasSize(2);
        assertCompositeSpan(reportedSpans.get(0), 2);
        assertThat(reportedSpans.get(1).isComposite()).isFalse();

        SpanCount spanCount = reporter.getFirstTransaction().getSpanCount();
        assertThat(spanCount.getReported().get()).isEqualTo(2);
        assertThat(spanCount.getDropped().get()).isEqualTo(1);
    }

    @Test
    void testFailedOutcomeStopsRegularCompression() {
        runInTransactionScope(t -> {
            startExitSpan(t).end();
            startExitSpan(t).withOutcome(Outcome.FAILURE).end();
        });

        List<Span> reportedSpans = reporter.getSpans();
        assertThat(reportedSpans).hasSize(2);
        assertThat(reportedSpans.get(0).isComposite()).isFalse();
        assertThat(reportedSpans.get(1).isComposite()).isFalse();

        SpanCount spanCount = reporter.getFirstTransaction().getSpanCount();
        assertThat(spanCount.getReported().get()).isEqualTo(2);
        assertThat(spanCount.getDropped().get()).isEqualTo(0);
    }

    @Test
    void testFailedOutcomeStopsCompositeCompression() {
        runInTransactionScope(t -> {
            startExitSpan(t).end();
            startExitSpan(t).end();
            startExitSpan(t).withOutcome(Outcome.FAILURE).end();
        });

        List<Span> reportedSpans = reporter.getSpans();
        assertThat(reportedSpans).hasSize(2);
        assertCompositeSpan(reportedSpans.get(0), 2);
        assertThat(reportedSpans.get(1).isComposite()).isFalse();

        SpanCount spanCount = reporter.getFirstTransaction().getSpanCount();
        assertThat(spanCount.getReported().get()).isEqualTo(2);
        assertThat(spanCount.getDropped().get()).isEqualTo(1);
    }

    @Test
    void testContextPropagationStopsRegularCompression() {
        runInTransactionScope(t -> {
            startExitSpan(t).end();
            Span span = startExitSpan(t);
            span.propagateTraceContext(new HashMap<String, String>(), (h, v, c) -> {
                c.put(h, v);
            });
            span.end();
        });

        List<Span> reportedSpans = reporter.getSpans();
        assertThat(reportedSpans).hasSize(2);
        assertThat(reportedSpans.get(0).isComposite()).isFalse();
        assertThat(reportedSpans.get(1).isComposite()).isFalse();

        SpanCount spanCount = reporter.getFirstTransaction().getSpanCount();
        assertThat(spanCount.getReported().get()).isEqualTo(2);
        assertThat(spanCount.getDropped().get()).isEqualTo(0);
    }

    @Test
    void testContextPropagationStopsCompositeCompression() {
        runInTransactionScope(t -> {
            startExitSpan(t).end();
            startExitSpan(t).end();
            Span span = startExitSpan(t);
            span.propagateTraceContext(new HashMap<String, String>(), (h, v, c) -> {
                c.put(h, v);
            });
            span.end();
        });

        List<Span> reportedSpans = reporter.getSpans();
        assertThat(reportedSpans).hasSize(2);
        assertCompositeSpan(reportedSpans.get(0), 2);
        assertThat(reportedSpans.get(1).isComposite()).isFalse();

        SpanCount spanCount = reporter.getFirstTransaction().getSpanCount();
        assertThat(spanCount.getReported().get()).isEqualTo(2);
        assertThat(spanCount.getDropped().get()).isEqualTo(1);
    }

    @Test
    void testNonExitSpanStopsRegularCompression() {
        runInTransactionScope(t -> {
            startExitSpan(t).end();
            startSpan(t).end();
        });

        List<Span> reportedSpans = reporter.getSpans();
        assertThat(reportedSpans).hasSize(2);
        assertThat(reportedSpans.get(0).isComposite()).isFalse();
        assertThat(reportedSpans.get(1).isComposite()).isFalse();

        SpanCount spanCount = reporter.getFirstTransaction().getSpanCount();
        assertThat(spanCount.getReported().get()).isEqualTo(2);
        assertThat(spanCount.getDropped().get()).isEqualTo(0);
    }

    @Test
    void testNonExitSpanStopsCompositeCompression() {
        runInTransactionScope(t -> {
            startExitSpan(t).end();
            startExitSpan(t).end();
            startSpan(t).end();
        });

        List<Span> reportedSpans = reporter.getSpans();
        assertThat(reportedSpans).hasSize(2);
        assertCompositeSpan(reportedSpans.get(0), 2);
        assertThat(reportedSpans.get(1).isComposite()).isFalse();

        SpanCount spanCount = reporter.getFirstTransaction().getSpanCount();
        assertThat(spanCount.getReported().get()).isEqualTo(2);
        assertThat(spanCount.getDropped().get()).isEqualTo(1);
    }

    @Test
    void testDifferentTypeStopsRegularCompression() {
        runInTransactionScope(t -> {
            startExitSpan(t).end();
            startExitSpan(t).withType("another_type").end();
        });

        List<Span> reportedSpans = reporter.getSpans();
        assertThat(reportedSpans).hasSize(2);
        assertThat(reportedSpans.get(0).isComposite()).isFalse();
        assertThat(reportedSpans.get(1).isComposite()).isFalse();

        SpanCount spanCount = reporter.getFirstTransaction().getSpanCount();
        assertThat(spanCount.getReported().get()).isEqualTo(2);
        assertThat(spanCount.getDropped().get()).isEqualTo(0);
    }

    @Test
    void testDifferentTypeStopsCompositeCompression() {
        runInTransactionScope(t -> {
            startExitSpan(t).end();
            startExitSpan(t).end();
            startExitSpan(t).withType("another_type").end();
        });

        List<Span> reportedSpans = reporter.getSpans();
        assertThat(reportedSpans).hasSize(2);
        assertCompositeSpan(reportedSpans.get(0), 2);
        assertThat(reportedSpans.get(1).isComposite()).isFalse();

        SpanCount spanCount = reporter.getFirstTransaction().getSpanCount();
        assertThat(spanCount.getReported().get()).isEqualTo(2);
        assertThat(spanCount.getDropped().get()).isEqualTo(1);
    }

    @Test
    void testDifferentSubtypeStopsRegularCompression() {
        runInTransactionScope(t -> {
            startExitSpan(t).end();
            startExitSpan(t).withSubtype("another_subtype").end();
        });

        List<Span> reportedSpans = reporter.getSpans();
        assertThat(reportedSpans).hasSize(2);
        assertThat(reportedSpans.get(0).isComposite()).isFalse();
        assertThat(reportedSpans.get(1).isComposite()).isFalse();

        SpanCount spanCount = reporter.getFirstTransaction().getSpanCount();
        assertThat(spanCount.getReported().get()).isEqualTo(2);
        assertThat(spanCount.getDropped().get()).isEqualTo(0);
    }

    @Test
    void testDifferentSubtypeStopsCompositeCompression() {
        runInTransactionScope(t -> {
            startExitSpan(t).end();
            startExitSpan(t).end();
            startExitSpan(t).withSubtype("another_subtype").end();
        });

        List<Span> reportedSpans = reporter.getSpans();
        assertThat(reportedSpans).hasSize(2);
        assertCompositeSpan(reportedSpans.get(0), 2);
        assertThat(reportedSpans.get(1).isComposite()).isFalse();

        SpanCount spanCount = reporter.getFirstTransaction().getSpanCount();
        assertThat(spanCount.getReported().get()).isEqualTo(2);
        assertThat(spanCount.getDropped().get()).isEqualTo(1);
    }

    @Test
    void testSpanExceedingMaxDurationStopsRegularCompression() {
        runInTransactionScope(t -> {
            startExitSpan(t).end();
            Span span = startExitSpan(t);
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {

            }
            span.end();
        });

        List<Span> reportedSpans = reporter.getSpans();
        assertThat(reportedSpans).hasSize(2);
        assertThat(reportedSpans.get(0).isComposite()).isFalse();
        assertThat(reportedSpans.get(1).isComposite()).isFalse();

        SpanCount spanCount = reporter.getFirstTransaction().getSpanCount();
        assertThat(spanCount.getReported().get()).isEqualTo(2);
        assertThat(spanCount.getDropped().get()).isEqualTo(0);
    }

    @Test
    void testSpanExceedingMaxDurationStopsCompositeCompression() {
        runInTransactionScope(t -> {
            startExitSpan(t).end();
            startExitSpan(t).end();
            Span span = startExitSpan(t);
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {

            }
            span.end();
        });

        List<Span> reportedSpans = reporter.getSpans();
        assertThat(reportedSpans).hasSize(2);
        assertCompositeSpan(reportedSpans.get(0), 2);
        assertThat(reportedSpans.get(1).isComposite()).isFalse();

        SpanCount spanCount = reporter.getFirstTransaction().getSpanCount();
        assertThat(spanCount.getReported().get()).isEqualTo(2);
        assertThat(spanCount.getDropped().get()).isEqualTo(1);
    }

    @Test
    void testDifferentDestinationServiceResourceStopsRegularCompression() {
        runInTransactionScope(t -> {
            startExitSpan(t).end();
            Span span = startExitSpan(t);

            // set alternative resource from user API
            assertThat(span.getContext().getServiceTarget().withUserType(null).withUserName("another_resource").withNameOnlyDestinationResource())
                .hasName("another_resource")
                .hasDestinationResource("another_resource");

            span.end();
        });

        List<Span> reportedSpans = reporter.getSpans();
        assertThat(reportedSpans).hasSize(2);
        assertThat(reportedSpans.get(0).isComposite()).isFalse();
        assertThat(reportedSpans.get(1).isComposite()).isFalse();

        SpanCount spanCount = reporter.getFirstTransaction().getSpanCount();
        assertThat(spanCount.getReported().get()).isEqualTo(2);
        assertThat(spanCount.getDropped().get()).isEqualTo(0);
    }

    @Test
    void testDifferentDestinationServiceResourceStopsCompositeCompression() {
        runInTransactionScope(t -> {
            startExitSpan(t).end();
            startExitSpan(t).end();
            Span span = startExitSpan(t);

            // set alternative resource from type
            ServiceTarget serviceTarget = span.getContext().getServiceTarget();
            serviceTarget.resetState();
            assertThat(serviceTarget.withType("another"))
                .hasType("another")
                .hasDestinationResource("another");

            span.end();
        });

        List<Span> reportedSpans = reporter.getSpans();
        assertThat(reportedSpans).hasSize(2);
        assertCompositeSpan(reportedSpans.get(0), 2);
        assertThat(reportedSpans.get(1).isComposite()).isFalse();

        SpanCount spanCount = reporter.getFirstTransaction().getSpanCount();
        assertThat(spanCount.getReported().get()).isEqualTo(2);
        assertThat(spanCount.getDropped().get()).isEqualTo(1);
    }

    protected static void runInTransactionScope(Consumer<AbstractSpan<?>> r) {
        Transaction transaction = tracer.startRootTransaction(null).withName("Some Transaction");
        try {
            r.accept(transaction);
        } finally {
            transaction.end();
        }
    }

    protected Span startExitSpan(AbstractSpan<?> parent) {
        Span span = startSpan(parent).asExit();
        span.getContext().getServiceTarget().withType("service-type").withName("service-name");
        return span;
    }

    protected Span startSpan(AbstractSpan<?> parent) {
        return parent.createSpan().withName(getSpanName()).withType("some_type").withSubtype("some_subtype");
    }

    protected abstract String getSpanName();

    protected void assertCompositeSpan(Span span, int count) {
        assertThat(span.isComposite()).isTrue();
        assertThat(span.getComposite().getCount()).isEqualTo(count);
        assertThat(span.getComposite().getCompressionStrategy()).isEqualTo(compressionStrategy);
        assertThat(span.getNameAsString()).isEqualTo(getCompositeSpanName(span));
    }

    protected abstract String getCompositeSpanName(Span span);
}
