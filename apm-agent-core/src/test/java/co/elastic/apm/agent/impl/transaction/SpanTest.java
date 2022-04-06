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
import co.elastic.apm.agent.impl.sampling.ConstantSampler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class SpanTest {

    @Test
    void resetState() {
        Span span = new Span(MockTracer.create())
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
        assertThat(span.getNameAsString()).isNullOrEmpty();
        assertThat(span.getType()).isNull();
        assertThat(span.getSubtype()).isNull();
        assertThat(span.getAction()).isNull();
        assertThat(span.getOutcome()).isEqualTo(Outcome.UNKNOWN);
    }

    @Test
    void testOutcomeExplicitlyToUnknown() {
        Transaction transaction = MockTracer.createRealTracer().startRootTransaction(null);
        assertThat(transaction).isNotNull();
        Span span = transaction.createSpan()
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
        Span span = new Span(MockTracer.create())
            .withName("span");

        assertThat(span.withType("").getType()).isNull();
        assertThat(span.withSubtype("").getSubtype()).isNull();
        assertThat(span.withAction("").getAction()).isNull();
    }

    @ParameterizedTest
    @MethodSource("typeTestArguments")
    void normalizeType(String type, String expectedType) {

        ElasticApmTracer tracer = MockTracer.createRealTracer();
        Transaction transaction = new Transaction(tracer);

        transaction.start(TraceContext.asRoot(), null, 0, ConstantSampler.of(true));
        try {
            Span span = new Span(tracer);

            span.start(TraceContext.fromParent(), transaction, -1L);

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
}
