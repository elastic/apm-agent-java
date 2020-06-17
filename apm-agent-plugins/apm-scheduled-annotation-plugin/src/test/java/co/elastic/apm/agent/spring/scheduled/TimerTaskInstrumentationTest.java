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
package co.elastic.apm.agent.spring.scheduled;

import co.elastic.apm.agent.AbstractInstrumentationTest;
import co.elastic.apm.agent.impl.transaction.Transaction;
import org.junit.jupiter.api.Test;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

public class TimerTaskInstrumentationTest extends AbstractInstrumentationTest {

    @Test
    void testTimerTask_scheduleWithFixedRate() throws InterruptedException {
        reporter.reset();
        TestTimerTask timerTask = new TestTimerTask();
        Timer timer = new Timer(true);
        timer.scheduleAtFixedRate(timerTask, 0, 10L);

        reporter.awaitUntilAsserted(200L, () -> {
            timer.cancel();
            assertThat(reporter.getTransactions()).isNotEmpty();
        });

        assertThat(reporter.getTransactions().size()).isEqualTo(timerTask.getInvocationCount());
        Transaction firstTransaction = reporter.getTransactions().get(0);
        assertThat(firstTransaction.getNameAsString()).isEqualTo("TestTimerTask#run");
        assertThat(firstTransaction.getFrameworkName()).isEqualTo("TimerTask");
    }

    @Test
    void testTimerTask_scheduleWithFixedDelay() throws InterruptedException {
        reporter.reset();
        TestTimerTask timerTask = new TestTimerTask();
        Timer timer = new Timer("Timer");
        timer.schedule(timerTask, 1L, 10L);

        reporter.awaitUntilAsserted(200L, () -> {
            timer.cancel();
            assertThat(reporter.getTransactions()).isNotEmpty();
        });

        assertThat(reporter.getTransactions().size()).isEqualTo(timerTask.getInvocationCount());
        assertThat(reporter.getTransactions().get(0).getNameAsString()).isEqualTo("TestTimerTask#run");
    }

    @Test
    void testTimerTask_scheduleOnce() throws InterruptedException {
        reporter.reset();
        TestTimerTask timerTask = new TestTimerTask();
        Timer timer = new Timer("Timer");
        long delay = 50L;
        timer.schedule(timerTask, delay);

        reporter.awaitUntilAsserted(4 * delay, () -> {
            assertThat(reporter.getTransactions()).isNotEmpty();
        });

        assertThat(reporter.getTransactions().size()).isEqualTo(1);
        assertThat(reporter.getTransactions().get(0).getNameAsString()).isEqualTo("TestTimerTask#run");
    }

    @Test
    void testTimerTask_withAnonymousClass() throws InterruptedException {
        reporter.reset();
        AtomicInteger count = new AtomicInteger(0);

        TimerTask repeatedTask = new TimerTask() {
            public void run() {
                count.incrementAndGet();
            }
        };
        Timer timer = new Timer("Timer");
        long delay = 50L;
        timer.schedule(repeatedTask, delay);

        reporter.awaitUntilAsserted(4 * delay, () -> {
            assertThat(reporter.getTransactions()).isNotEmpty();
        });

        assertThat(reporter.getTransactions().size()).isEqualTo(1);
        assertThat(reporter.getTransactions().get(0).getNameAsString()).isEqualTo("1#run");
    }

    public static class TestTimerTask extends TimerTask {
        private AtomicInteger count = new AtomicInteger(0);

        @Override
        public void run() {
            this.count.incrementAndGet();
        }

        public int getInvocationCount() {
            return this.count.get();
        }
    }
}
