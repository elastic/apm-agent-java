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
package co.elastic.apm.agent.concurrent;

import co.elastic.apm.agent.MockReporter;
import co.elastic.apm.agent.MockTracer;
import co.elastic.apm.agent.bci.ElasticApmAgent;
import co.elastic.apm.agent.bci.methodmatching.MethodMatcher;
import co.elastic.apm.agent.configuration.CoreConfiguration;
import co.elastic.apm.agent.configuration.converter.TimeDuration;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import net.bytebuddy.agent.ByteBuddyAgent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.stagemonitor.configuration.ConfigurationRegistry;

import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

class AsyncTraceMethodInstrumentationTest {

    private MockReporter reporter;
    private ElasticApmTracer tracer;
    private CoreConfiguration coreConfiguration;

    @BeforeEach
    void setUp(TestInfo testInfo) {
        MockTracer.MockInstrumentationSetup mockInstrumentationSetup = MockTracer.createMockInstrumentationSetup();
        reporter = mockInstrumentationSetup.getReporter();
        ConfigurationRegistry config = mockInstrumentationSetup.getConfig();
        coreConfiguration = config.getConfig(CoreConfiguration.class);
        when(coreConfiguration.getTraceMethods()).thenReturn(Arrays.asList(
            MethodMatcher.of("private co.elastic.apm.agent.concurrent.AsyncTraceMethodInstrumentationTest$TestAsyncTraceMethodsClass#*"))
        );

        for (String tag : testInfo.getTags()) {
            TimeDuration duration = TimeDuration.of(tag.split("=")[1]);
            if (tag.startsWith("span_min_duration=")) {
                doReturn(duration).when(coreConfiguration).getSpanMinDuration();
            }
            if (tag.startsWith("trace_methods_duration_threshold=")) {
                doReturn(duration).when(coreConfiguration).getTraceMethodsDurationThreshold();
            }
        }

        tracer = mockInstrumentationSetup.getTracer();
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
    @Tag("span_min_duration=200ms")
    void testWithHighThreshold() {
        new TestAsyncTraceMethodsClass().invokeAsync();
        assertThat(reporter.getTransactions()).hasSize(1);
        assertThat(reporter.getSpans()).hasSize(2);
    }

    @Test
    @Tag("span_min_duration=50ms")
    @Tag("trace_methods_duration_threshold=200ms")
    void testWithHigherSpecificThreshold() {
        new TestAsyncTraceMethodsClass().invokeAsync();
        assertThat(reporter.getTransactions()).hasSize(1);
        assertThat(reporter.getSpans()).hasSize(2);
    }

    @Test
    @Tag("span_min_duration=50ms")
    void testWithCrossedThreshold_Generic() {
        new TestAsyncTraceMethodsClass().invokeAsync();
        assertThat(reporter.getTransactions()).hasSize(1);
        assertThat(reporter.getSpans()).hasSize(4);
    }

    @Test
    @Tag("trace_methods_duration_threshold=50ms")
    void testWithCrossedThreshold_Specific() {
        new TestAsyncTraceMethodsClass().invokeAsync();
        assertThat(reporter.getTransactions()).hasSize(1);
        assertThat(reporter.getSpans()).hasSize(4);
    }


    private static class TestAsyncTraceMethodsClass {

        /**
         * Calling this method results in this method call tree:
         * <pre>
         *
         *
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
         * </pre>
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
            return Executors.newFixedThreadPool(1).submit(TestAsyncTraceMethodsClass.this::methodOnWorkerThread);
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
