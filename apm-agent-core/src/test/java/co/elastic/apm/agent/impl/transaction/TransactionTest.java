/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2020 Elastic and contributors
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
package co.elastic.apm.agent.impl.transaction;

import co.elastic.apm.agent.MockTracer;
import co.elastic.apm.agent.TransactionUtils;
import co.elastic.apm.agent.impl.stacktrace.StacktraceConfiguration;
import co.elastic.apm.agent.report.ApmServerClient;
import co.elastic.apm.agent.report.serialize.DslJsonSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class TransactionTest {

    private DslJsonSerializer jsonSerializer;

    @BeforeEach
    void setUp() {
        jsonSerializer = new DslJsonSerializer(mock(StacktraceConfiguration.class), mock(ApmServerClient.class));
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

        Arrays.asList(Outcome.SUCCESS, Outcome.UNKNOWN).forEach(o ->{
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

}
