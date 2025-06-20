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
import co.elastic.apm.agent.impl.transaction.AbstractSpanImpl;
import co.elastic.apm.agent.impl.transaction.TransactionImpl;
import org.junit.jupiter.api.Test;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

public class TimerTaskInstrumentationTest extends AbstractInstrumentationTest {

    @Test
    void testTimerTask_scheduleWithFixedRate() {
        new Timer(true)
            .scheduleAtFixedRate(new TestTimerTask(2), 1, 1);

        reporter.awaitTransactionCount(2);

        assertThat(reporter.getTransactions()
            .stream()
            .map(AbstractSpanImpl::getNameAsString))
            .containsExactly("TestTimerTask#run", "TestTimerTask#run");
        assertThat(reporter.getTransactions()
            .stream()
            .map(TransactionImpl::getFrameworkName))
            .containsExactly("TimerTask", "TimerTask");
    }

    @Test
    void testTimerTask_scheduleWithFixedDelay() {
        new Timer("Timer")
            .schedule(new TestTimerTask(2), 1, 1);

        reporter.awaitTransactionCount(2);

        assertThat(reporter.getTransactions()
            .stream()
            .map(AbstractSpanImpl::getNameAsString))
            .containsExactly("TestTimerTask#run", "TestTimerTask#run");
    }

    @Test
    void testTimerTask_scheduleOnce() {
        new Timer("Timer")
            .schedule(new TestTimerTask(1), 1);

        reporter.awaitTransactionCount(1);
        assertThat(reporter.getTransactions().get(0).getNameAsString()).isEqualTo("TestTimerTask#run");
    }

    @Test
    void testTimerTask_withAnonymousClass() {
        new Timer("Timer")
            .schedule(new TimerTask() {
                public void run() {
                    cancel();
                }
            }, 1);

        reporter.awaitTransactionCount(1);
        assertThat(reporter.getTransactions().get(0).getNameAsString()).isEqualTo("TimerTaskInstrumentationTest$1#run");
    }

    public static class TestTimerTask extends TimerTask {
        private final AtomicInteger credits;

        public TestTimerTask(int maxInvocations) {
            credits = new AtomicInteger(maxInvocations);
        }

        @Override
        public void run() {
            if (credits.decrementAndGet() == 0) {
                cancel();
            }
        }
    }
}
