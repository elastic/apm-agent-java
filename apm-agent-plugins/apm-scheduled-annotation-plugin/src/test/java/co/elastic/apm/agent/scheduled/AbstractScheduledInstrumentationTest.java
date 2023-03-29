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
package co.elastic.apm.agent.scheduled;

import co.elastic.apm.agent.AbstractInstrumentationTest;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.tracer.Outcome;
import co.elastic.apm.agent.impl.transaction.Transaction;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class AbstractScheduledInstrumentationTest extends AbstractInstrumentationTest {

    protected static List<Transaction> checkTransactions(AbstractCounter counter, int expectedCount, String expectedName) {
        List<Transaction> transactions = reporter.getTransactions();
        assertThat(transactions).hasSize(counter.getInvocationCount()).hasSize(expectedCount);
        transactions.forEach(t -> {
            assertThat(t.getNameAsString()).isEqualTo(expectedName);
        });
        return transactions;
    }

    protected static void checkOutcome(List<Transaction> transactions, Outcome outcome) {
        assertThat(transactions.stream()
            .map(AbstractSpan::getOutcome)
            .collect(Collectors.toSet()))
            .containsExactly(outcome);
    }

    protected static abstract class AbstractCounter {
        protected final AtomicInteger count = new AtomicInteger(0);

        public final int getInvocationCount() {
            return this.count.get();
        }
    }
}
