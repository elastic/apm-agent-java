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

import co.elastic.apm.agent.tracer.Outcome;
import co.elastic.apm.agent.impl.transaction.Transaction;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.annotation.Schedules;

import java.util.List;

public class SpringScheduledTransactionNameInstrumentationTest extends AbstractScheduledInstrumentationTest {

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

}
