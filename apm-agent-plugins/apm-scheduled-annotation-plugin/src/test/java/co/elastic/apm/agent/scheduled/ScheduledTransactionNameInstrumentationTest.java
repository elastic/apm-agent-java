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
package co.elastic.apm.agent.scheduled;

import co.elastic.apm.agent.AbstractInstrumentationTest;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Outcome;
import co.elastic.apm.agent.impl.transaction.Transaction;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.annotation.Schedules;

import javax.ejb.Schedule;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


class ScheduledTransactionNameInstrumentationTest extends AbstractInstrumentationTest {

    @Test
    void testSpringScheduledAnnotatedMethodsAreTraced() {
        SpringCounter springCounter = new SpringCounter();
        springCounter.scheduled();
        springCounter.scheduled();

        List<Transaction> transactions = checkTransactions(springCounter, 2, "SpringCounter#scheduled");
        checkOutcome(transactions, Outcome.SUCCESS);
    }

    @Test
    void testSpringJ8RepeatableScheduledAnnotatedMethodsAreTraced() {
        SpringCounter springCounter = new SpringCounter();
        springCounter.scheduledJava8Repeatable();
        springCounter.scheduledJava8Repeatable();

        List<Transaction> transactions = checkTransactions(springCounter, 2, "SpringCounter#scheduledJava8Repeatable");
        checkOutcome(transactions, Outcome.SUCCESS);
    }

    @Test
    void testSpringJ7RepeatableScheduledAnnotatedMethodsAreTraced() {
        SpringCounter springCounter = new SpringCounter();
        springCounter.scheduledJava7Repeatable();
        springCounter.scheduledJava7Repeatable();

        List<Transaction> transactions = checkTransactions(springCounter, 2, "SpringCounter#scheduledJava7Repeatable");
        checkOutcome(transactions, Outcome.SUCCESS);
    }

    @Test
    void testJeeScheduledAnnotatedMethodsAreTraced() {
        JeeCounter jeeCounter = new JeeCounter();
        jeeCounter.scheduled();
        jeeCounter.scheduled();
        jeeCounter.scheduled();
        List<Transaction> transactions = checkTransactions(jeeCounter, 3, "JeeCounter#scheduled");
        checkOutcome(transactions, Outcome.SUCCESS);
    }

    @Test
    void testJeeJ7RepeatableScheduledAnnotatedMethodsAreTraced() {
        JeeCounter jeeCounter = new JeeCounter();
        jeeCounter.scheduledJava7Repeatable();
        jeeCounter.scheduledJava7Repeatable();
        List<Transaction> transactions = checkTransactions(jeeCounter, 2, "JeeCounter#scheduledJava7Repeatable");
        checkOutcome(transactions, Outcome.SUCCESS);
    }

    @Test
    void testThrownErrorOutcomes(){
        ThrowingCounter throwCounter = new ThrowingCounter();

        assertThatThrownBy(throwCounter::throwingException);

        List<Transaction> transactions = checkTransactions(throwCounter, 1, "ThrowingCounter#throwingException");
        checkOutcome(transactions, Outcome.FAILURE);
    }

    private static List<Transaction> checkTransactions(AbstractCounter counter, int expectedCount, String expectedName) {
        List<Transaction> transactions = reporter.getTransactions();
        assertThat(transactions).hasSize(counter.getInvocationCount()).hasSize(expectedCount);
        transactions.forEach(t -> {
            assertThat(t.getNameAsString()).isEqualTo(expectedName);
        });
        return transactions;
    }

    private static void checkOutcome(List<Transaction> transactions, Outcome outcome) {
        assertThat(transactions.stream()
            .map(AbstractSpan::getOutcome)
            .collect(Collectors.toSet()))
            .containsExactly(outcome);
    }

    private static abstract class AbstractCounter {
        protected final AtomicInteger count = new AtomicInteger(0);

        public final int getInvocationCount() {
            return this.count.get();
        }
    }

    private static class SpringCounter extends AbstractCounter {

        @Scheduled(fixedDelay = 5)
        public void scheduled() {
            this.count.incrementAndGet();
        }

        @Scheduled(fixedDelay = 5)
        @Scheduled(fixedDelay = 10)
        public void scheduledJava8Repeatable() {
            this.count.incrementAndGet();
        }

        @Schedules({
            @Scheduled(fixedDelay = 5),
            @Scheduled(fixedDelay = 10)
        })
        public void scheduledJava7Repeatable() {
            this.count.incrementAndGet();
        }

    }

    private static class JeeCounter extends AbstractCounter {

        @Schedule(minute = "5")
        public void scheduled() {
            this.count.incrementAndGet();
        }

        @javax.ejb.Schedules({
            @Schedule(minute = "5"),
            @Schedule(minute = "10")
        })
        public void scheduledJava7Repeatable() {
            this.count.incrementAndGet();
        }
    }

    private static class ThrowingCounter extends AbstractCounter {

        @Schedule(minute = "5") // whatever the used annotation here, the behavior should be the same
        public void throwingException() {
            count.incrementAndGet();
            throw new RuntimeException("intentional exception");
        }
    }

}
