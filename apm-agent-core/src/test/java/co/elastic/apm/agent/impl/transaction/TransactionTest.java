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
import co.elastic.apm.agent.TransactionUtils;
import co.elastic.apm.agent.impl.metadata.MetaDataMock;
import co.elastic.apm.agent.impl.sampling.ConstantSampler;
import co.elastic.apm.agent.impl.stacktrace.StacktraceConfiguration;
import co.elastic.apm.agent.report.ApmServerClient;
import co.elastic.apm.agent.report.serialize.DslJsonSerializer;
import co.elastic.apm.agent.tracer.Outcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

public class TransactionTest {

    private DslJsonSerializer.Writer jsonSerializer;

    @BeforeEach
    void setUp() {
        jsonSerializer = new DslJsonSerializer(
            mock(StacktraceConfiguration.class),
            mock(ApmServerClient.class),
            MetaDataMock.create()
        ).newWriter();
    }

    @Test
    void resetState() {
        final Transaction transaction = new Transaction(MockTracer.create());
        TransactionUtils.fillTransaction(transaction);
        transaction.resetState();
        assertThat(jsonSerializer.toJsonString(transaction)).isEqualTo(jsonSerializer.toJsonString(new Transaction(MockTracer.create())));
    }

    @Test
    void getSetOutcome() {
        Transaction transaction = new Transaction(MockTracer.create());

        assertThat(transaction.getOutcome())
            .describedAs("default outcome should be unknown")
            .isEqualTo(Outcome.UNKNOWN);

        assertThat(transaction.withOutcome(Outcome.SUCCESS).getOutcome())
            .isSameAs(Outcome.SUCCESS);

        assertThat(transaction.withOutcome(Outcome.FAILURE).getOutcome())
            .isSameAs(Outcome.FAILURE);

        Arrays.asList(Outcome.SUCCESS, Outcome.UNKNOWN).forEach(o -> {
            assertThat(transaction.withUserOutcome(o).getOutcome())
                .describedAs("user outcome should have higher priority over outcome")
                .isSameAs(o);
        });

        assertThat(transaction
            .withUserOutcome(Outcome.SUCCESS)
            .withUserOutcome(Outcome.FAILURE)
            .getOutcome())
            .describedAs("takes last value when set by user multiple times")
            .isSameAs(Outcome.FAILURE);

        transaction.resetState();

        assertThat(transaction.getOutcome())
            .describedAs("reset should reset to unknown state")
            .isEqualTo(Outcome.UNKNOWN);

    }

    @ParameterizedTest
    @MethodSource("typeTestArguments")
    void normalizeType(String type, String expectedType) {
        Transaction transaction = new Transaction(MockTracer.createRealTracer());

        transaction.startRoot(0, ConstantSampler.of(true));
        assertThat(transaction.getType())
            .describedAs("transaction type should not be set by default")
            .isNull();

        transaction.withType(type);

        transaction.end();

        assertThat(transaction.getType()).isEqualTo(expectedType);
    }

    static Stream<Arguments> typeTestArguments() {
        return Stream.of(
            Arguments.of("", "custom"),
            Arguments.of(null, "custom"),
            Arguments.of("my-type", "my-type")
        );
    }

    /**
     * A utility to enable arbitrary tests to set an existing {@link Transaction} state without making this functionality globally accessible
     * @param recorded should the provided trace context be recorded
     * @param transaction a span of which state is to be set
     */
    public static void setRecorded(boolean recorded, Transaction transaction) {
        transaction.getTraceContext().setRecorded(recorded);
    }
}
