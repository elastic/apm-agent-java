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

import co.elastic.apm.agent.MockTracer;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.TextHeaderMapAccessor;
import co.elastic.apm.agent.impl.Utf8HeaderMapAccessor;
import co.elastic.apm.agent.impl.baggage.BaggageImpl;
import co.elastic.apm.agent.impl.sampling.ConstantSampler;
import co.elastic.apm.agent.objectpool.TestObjectPoolFactory;
import co.elastic.apm.agent.tracer.Outcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

public class SpanTest {

    private ElasticApmTracer tracer;

    @BeforeEach
    void setUp() {
        tracer = MockTracer.createRealTracer();
    }

    @Test
    void resetState() {
        SpanImpl span = new SpanImpl(tracer)
            .withName("SELECT FROM product_types")
            .withType("db")
            .withSubtype("postgresql")
            .withAction("query");
        span.getContext().getDb()
            .withInstance("customers")
            .withStatement("SELECT * FROM product_types WHERE user_id=?")
            .withType("sql")
            .withUser("readonly_user");
        span.resetState();
        assertThat(span.getContext().hasContent()).isFalse();
        assertThat(span.getNameAsString()).isEqualTo("unnamed");
        assertThat(span.getType()).isNull();
        assertThat(span.getSubtype()).isNull();
        assertThat(span.getAction()).isNull();
        assertThat(span.getOutcome()).isEqualTo(Outcome.UNKNOWN);
    }

    @Test
    void testOutcomeExplicitlyToUnknown() {
        TransactionImpl transaction = tracer.startRootTransaction(null);
        assertThat(transaction).isNotNull();
        SpanImpl span = transaction.createSpan()
            .withName("SELECT FROM product_types")
            .withType("db")
            .withSubtype("postgresql")
            .withAction("query")
            .withOutcome(Outcome.UNKNOWN);

        // end operation will apply heuristic if not set
        span.end();

        assertThat(span.getOutcome()).isEqualTo(Outcome.UNKNOWN);
    }

    @Test
    void normalizeEmptyFields() {
        SpanImpl span = new SpanImpl(tracer)
            .withName("span");

        assertThat(span.withType("").getType()).isNull();
        assertThat(span.withSubtype("").getSubtype()).isNull();
        assertThat(span.withAction("").getAction()).isNull();
    }

    @ParameterizedTest
    @MethodSource("typeTestArguments")
    void normalizeType(String type, String expectedType) {

        TransactionImpl transaction = new TransactionImpl(tracer);
        transaction.startRoot(0, ConstantSampler.of(true), BaggageImpl.EMPTY);
        try {
            SpanImpl span = new SpanImpl(tracer);
            span.start(TraceContextImpl.fromParent(), transaction, BaggageImpl.EMPTY, -1L);
            assertThat(span.getType())
                .describedAs("span type should not be set by default")
                .isNull();
            span.withType(type);
            span.end();
            assertThat(span.getType()).isEqualTo(expectedType);
        } finally {
            transaction.end();
        }

    }

    static Stream<Arguments> typeTestArguments() {
        return Stream.of(
            Arguments.of("", "custom"),
            Arguments.of(null, "custom"),
            Arguments.of("my-type", "my-type")
        );
    }

    @Test
    void testSpanLinks() {
        TestObjectPoolFactory objectPoolFactory = (TestObjectPoolFactory) tracer.getObjectPoolFactory();
        TransactionImpl transaction = tracer.startRootTransaction(null);
        SpanImpl testSpan = Objects.requireNonNull(transaction).createSpan();
        assertThat(objectPoolFactory.getSpanLinksPool().getObjectsInPool()).isEqualTo(0);
        assertThat(objectPoolFactory.getSpanLinksPool().getRequestedObjectCount()).isEqualTo(0);
        assertThat(testSpan.getSpanLinks()).isEmpty();
        SpanImpl parent1 = transaction.createSpan();
        Map<String, String> textTraceContextCarrier = new HashMap<>();
        parent1.propagateContext(textTraceContextCarrier, TextHeaderMapAccessor.INSTANCE, null);
        assertThat(testSpan.addSpanLink(
            TextHeaderMapAccessor.INSTANCE,
            textTraceContextCarrier)
        ).isTrue();
        assertThat(objectPoolFactory.getSpanLinksPool().getObjectsInPool()).isEqualTo(0);
        assertThat(objectPoolFactory.getSpanLinksPool().getRequestedObjectCount()).isEqualTo(1);
        assertThat(testSpan.getSpanLinks()).hasSize(1);
        SpanImpl parent2 = transaction.createSpan();
        Map<String, String> utfTraceContextCarrier = new HashMap<>();
        parent2.propagateContext(utfTraceContextCarrier, Utf8HeaderMapAccessor.INSTANCE, null);
        assertThat(testSpan.addSpanLink(
            TextHeaderMapAccessor.INSTANCE,
            utfTraceContextCarrier)
        ).isTrue();
        assertThat(objectPoolFactory.getSpanLinksPool().getObjectsInPool()).isEqualTo(0);
        assertThat(objectPoolFactory.getSpanLinksPool().getRequestedObjectCount()).isEqualTo(2);
        List<TraceContextImpl> spanLinks = testSpan.getSpanLinks();
        assertThat(spanLinks).hasSize(2);
        assertThat(spanLinks.get(0).getTraceId()).isEqualTo(parent1.getTraceContext().getTraceId());
        assertThat(spanLinks.get(0).getParentId()).isEqualTo(parent1.getTraceContext().getId());
        assertThat(spanLinks.get(1).getTraceId()).isEqualTo(parent2.getTraceContext().getTraceId());
        assertThat(spanLinks.get(1).getParentId()).isEqualTo(parent2.getTraceContext().getId());
        testSpan.resetState();
        assertThat(objectPoolFactory.getSpanLinksPool().getObjectsInPool()).isEqualTo(2);
        assertThat(testSpan.getSpanLinks()).isEmpty();
    }

    @Test
    void testSpanLinksUniqueness() {
        TransactionImpl transaction = tracer.startRootTransaction(null);
        SpanImpl testSpan = Objects.requireNonNull(transaction).createSpan();
        assertThat(testSpan.getSpanLinks()).isEmpty();
        SpanImpl parent1 = transaction.createSpan();
        Map<String, String> textTraceContextCarrier = new HashMap<>();
        parent1.propagateContext(textTraceContextCarrier, TextHeaderMapAccessor.INSTANCE, null);
        assertThat(testSpan.addSpanLink(
            TextHeaderMapAccessor.INSTANCE,
            textTraceContextCarrier)
        ).isTrue();
        assertThat(testSpan.getSpanLinks()).hasSize(1);
        assertThat(testSpan.addSpanLink(
            TextHeaderMapAccessor.INSTANCE,
            textTraceContextCarrier)
        ).isFalse();
        assertThat(testSpan.getSpanLinks()).hasSize(1);

        testSpan.getSpanLinks().clear();
        assertThat(testSpan.getSpanLinks()).isEmpty();

        // verifying that uniqueness cache is cleared properly as well
        assertThat(testSpan.addSpanLink(
            TextHeaderMapAccessor.INSTANCE,
            textTraceContextCarrier)
        ).isTrue();
        assertThat(testSpan.getSpanLinks()).hasSize(1);
    }

    /**
     * A utility to enable arbitrary tests to set an existing {@link SpanImpl} state without making this functionality globally accessible
     * @param recorded should the provided trace context be recorded
     * @param span a span of which state is to be set
     */
    public static void setRecorded(boolean recorded, SpanImpl span) {
        span.getTraceContext().setRecorded(recorded);
    }
}
