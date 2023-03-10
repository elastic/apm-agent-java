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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;


abstract class AbstractScheduledTransactionNameInstrumentationTest extends AbstractScheduledInstrumentationTest {

    @Test
    void testJeeScheduledAnnotatedMethodsAreTraced() {
        JeeCounter jeeCounter = createJeeCounterImpl();
        jeeCounter.scheduled();
        jeeCounter.scheduled();
        jeeCounter.scheduled();
        List<Transaction> transactions = checkTransactions(jeeCounter, 3, "JeeCounterImpl#scheduled");
        checkOutcome(transactions, Outcome.SUCCESS);
    }

    @Test
    void testJeeJ7RepeatableScheduledAnnotatedMethodsAreTraced() {
        JeeCounter jeeCounter = createJeeCounterImpl();
        jeeCounter.scheduledJava7Repeatable();
        jeeCounter.scheduledJava7Repeatable();
        List<Transaction> transactions = checkTransactions(jeeCounter, 2, "JeeCounterImpl#scheduledJava7Repeatable");
        checkOutcome(transactions, Outcome.SUCCESS);
    }

    @Test
    void testThrownErrorOutcomes() {
        ThrowingCounter throwCounter = createThrowingCounterImpl();

        assertThatThrownBy(throwCounter::throwingException);

        List<Transaction> transactions = checkTransactions(throwCounter, 1, "ThrowingCounterImpl#throwingException");
        checkOutcome(transactions, Outcome.FAILURE);
    }

    abstract JeeCounter createJeeCounterImpl();

    abstract ThrowingCounter createThrowingCounterImpl();

    protected static abstract class JeeCounter extends AbstractCounter {

        public abstract void scheduled();

        public abstract void scheduledJava7Repeatable();
    }

    protected static abstract class ThrowingCounter extends AbstractCounter {

        public abstract void throwingException();
    }

}
