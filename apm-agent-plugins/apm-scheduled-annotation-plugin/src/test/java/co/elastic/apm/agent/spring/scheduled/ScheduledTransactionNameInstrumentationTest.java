/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2019 Elastic and contributors
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

import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import javax.ejb.Schedule;

import org.junit.BeforeClass;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.annotation.Schedules;

import co.elastic.apm.agent.MockReporter;
import co.elastic.apm.agent.bci.ElasticApmAgent;
import co.elastic.apm.agent.configuration.SpyConfiguration;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.ElasticApmTracerBuilder;
import net.bytebuddy.agent.ByteBuddyAgent;

import static org.assertj.core.api.Assertions.assertThat;


class ScheduledTransactionNameInstrumentationTest {

    private static MockReporter reporter;
    private static ElasticApmTracer tracer;

    @BeforeClass
    @BeforeAll
    static void setUpAll() {
        reporter = new MockReporter();
        tracer = new ElasticApmTracerBuilder()
                .configurationRegistry(SpyConfiguration.createSpyConfig())
                .reporter(reporter)
                .build();
        ElasticApmAgent.initInstrumentation(tracer, ByteBuddyAgent.install(),
                Collections.singletonList(new ScheduledTransactionNameInstrumentation(tracer)));
    }


    @Test
    void testSpringScheduledAnnotatedMethodsAreTraced() {
        reporter.reset();
        SpringCounter springCounter = new SpringCounter();
        springCounter.scheduled();
        springCounter.scheduled();
        assertThat(reporter.getTransactions().size()).isEqualTo(springCounter.getInvocationCount());
        assertThat(reporter.getTransactions().get(0).getNameAsString()).isEqualTo("SpringCounter#scheduled");
    }

    @Test
    void testSpringJ8RepeatableScheduledAnnotatedMethodsAreTraced() {
        reporter.reset();
        SpringCounter springCounter = new SpringCounter();
        springCounter.scheduledJava8Repeatable();
        springCounter.scheduledJava8Repeatable();
        assertThat(reporter.getTransactions().size()).isEqualTo(springCounter.getInvocationCount());
        assertThat(reporter.getTransactions().get(0).getNameAsString()).isEqualTo("SpringCounter#scheduledJava8Repeatable");
    }

    @Test
    void testSpringJ7RepeatableScheduledAnnotatedMethodsAreTraced() {
        reporter.reset();
        SpringCounter springCounter = new SpringCounter();
        springCounter.scheduledJava7Repeatable();
        springCounter.scheduledJava7Repeatable();
        assertThat(reporter.getTransactions().size()).isEqualTo(springCounter.getInvocationCount());
        assertThat(reporter.getTransactions().get(0).getNameAsString()).isEqualTo("SpringCounter#scheduledJava7Repeatable");
    }

    @Test
    void testJeeScheduledAnnotatedMethodsAreTraced() {
        reporter.reset();
        JeeCounter jeeCounter = new JeeCounter();
        jeeCounter.scheduled();
        jeeCounter.scheduled();
        assertThat(reporter.getTransactions().size()).isEqualTo(jeeCounter.getInvocationCount());
        assertThat(reporter.getTransactions().get(0).getNameAsString()).isEqualTo("JeeCounter#scheduled");
    }

    @Test
    void testJeeJ7RepeatableScheduledAnnotatedMethodsAreTraced() {
        reporter.reset();
        JeeCounter jeeCounter = new JeeCounter();
        jeeCounter.scheduledJava7Repeatable();
        jeeCounter.scheduledJava7Repeatable();
        assertThat(reporter.getTransactions().size()).isEqualTo(jeeCounter.getInvocationCount());
        assertThat(reporter.getTransactions().get(0).getNameAsString()).isEqualTo("JeeCounter#scheduledJava7Repeatable");
    }


    private class SpringCounter {
        private AtomicInteger count = new AtomicInteger(0);

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

        public int getInvocationCount() {
            return this.count.get();
        }
    }

    private class JeeCounter {
        private AtomicInteger count = new AtomicInteger(0);

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

        public int getInvocationCount() {
            return this.count.get();
        }
    }


}
