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
package co.elastic.apm.agent.concurrent;

import co.elastic.apm.agent.MockReporter;
import co.elastic.apm.agent.bci.ElasticApmAgent;
import co.elastic.apm.agent.bci.methodmatching.MethodMatcher;
import co.elastic.apm.agent.configuration.CoreConfiguration;
import co.elastic.apm.agent.configuration.SpyConfiguration;
import co.elastic.apm.agent.configuration.converter.TimeDuration;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.ElasticApmTracerBuilder;
import net.bytebuddy.agent.ByteBuddyAgent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.stagemonitor.configuration.ConfigurationRegistry;

import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class AsyncTraceMethodInstrumentationTest {

    private MockReporter reporter;
    private ElasticApmTracer tracer;
    private CoreConfiguration coreConfiguration;

    @BeforeEach
    void setUp(TestInfo testInfo) {
        reporter = new MockReporter();
        ConfigurationRegistry config = SpyConfiguration.createSpyConfig();
        coreConfiguration = config.getConfig(CoreConfiguration.class);
        when(coreConfiguration.getTraceMethods()).thenReturn(Arrays.asList(
            MethodMatcher.of("private co.elastic.apm.agent.concurrent.AsyncTraceMethodInstrumentationTest$TestAsyncTraceMethodsClass#*"))
        );

        Set<String> tags = testInfo.getTags();
        if (!tags.isEmpty()) {
            when(coreConfiguration.getTraceMethodsDurationThreshold()).thenReturn(TimeDuration.of(tags.iterator().next()));
        }

        tracer = new ElasticApmTracerBuilder()
            .configurationRegistry(config)
            .reporter(reporter)
            .build();
        ElasticApmAgent.initInstrumentation(tracer, ByteBuddyAgent.install());
    }

    @AfterEach
    void tearDown() {
        ElasticApmAgent.reset();
    }

    @Test
    void testWithDefaultConfig() {
        new TestAsyncTraceMethodsClass().invokeAsync();
        assertThat(reporter.getTransactions()).hasSize(1);
        assertThat(reporter.getSpans()).hasSize(4);
    }

    @Test
    @Tag("200ms")
    void testWithHighThreshold() {
        new TestAsyncTraceMethodsClass().invokeAsync();
        assertThat(reporter.getTransactions()).hasSize(1);
        assertThat(reporter.getSpans()).hasSize(2);
    }

    @Test
    @Tag("50ms")
    void testWithCrossedThreshold() {
        new TestAsyncTraceMethodsClass().invokeAsync();
        assertThat(reporter.getTransactions()).hasSize(1);
        assertThat(reporter.getSpans()).hasSize(4);
    }


    private static class TestAsyncTraceMethodsClass {

        /**
         * Calling this method results in this method call tree:
         *
         *                      main thread                         |           worker thread
         * -------------------------------------------------------------------------------------------
         * invokeAsync                                              |
         *      |                                                   |
         *      --- blockingMethodOnMainThread                      |
         *                     |                                    |
         *                     --- nonBlockingMethodOnMainThread    |
         *                                      |                   |
         *                                      --------------------------> methodOnWorkerThread
         *                                                          |                |
         *                                                          |                --- longMethod
         *                                                          |
         */
        private void invokeAsync() {
            blockingMethodOnMainThread();
        }

        private void blockingMethodOnMainThread() {
            try {
                nonBlockingMethodOnMainThread().get();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        private Future<?> nonBlockingMethodOnMainThread() {
            return ExecutorServiceWrapper.wrap(Executors.newFixedThreadPool(1)).submit(TestAsyncTraceMethodsClass.this::methodOnWorkerThread);
        }

        private void methodOnWorkerThread() {
            longMethod();
        }

        private void longMethod() {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

    }
}
