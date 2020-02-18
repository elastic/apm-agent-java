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
package co.elastic.apm.agent.impl.circuitbreaker;

import co.elastic.apm.agent.MockReporter;
import co.elastic.apm.agent.bci.ElasticApmAgent;
import co.elastic.apm.agent.configuration.SpyConfiguration;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.ElasticApmTracerBuilder;
import net.bytebuddy.agent.ByteBuddyAgent;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.stagemonitor.configuration.ConfigurationRegistry;

import static co.elastic.apm.agent.impl.ElasticApmTracer.TracerState.PAUSED;
import static co.elastic.apm.agent.impl.ElasticApmTracer.TracerState.RUNNING;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@SuppressWarnings("NotNullFieldNotInitialized")
public class CircuitBreakerTest {

    private static ElasticApmTracer tracer;
    private static CircuitBreaker circuitBreaker;
    private static TestStressMonitor monitor;

    @BeforeAll
    public static void setup() {
        ConfigurationRegistry config = SpyConfiguration.createSpyConfig();
        tracer = new ElasticApmTracerBuilder()
            .configurationRegistry(SpyConfiguration.createSpyConfig())
            .reporter(new MockReporter())
            .build();
        ElasticApmAgent.initInstrumentation(tracer, ByteBuddyAgent.install());
        circuitBreaker = tracer.getLifecycleListener(CircuitBreaker.class);
        monitor = new TestStressMonitor(tracer);
        circuitBreaker.registerStressMonitor(monitor);
    }

    @AfterAll
    public static void tearDown() {
        circuitBreaker.unregisterStressMonitor(monitor);
    }

    @Test
    void testStateChange() {
        assertThat(tracer.getState()).isEqualTo(RUNNING);
        int pausePollCount = monitor.simulateStress();
        monitor.waitUntilPausePollCounterIsGreaterThan(pausePollCount);
        assertThat(tracer.getState()).isEqualTo(PAUSED);
        int resumePollCount = monitor.getResumePollCount();
        monitor.waitUntilResumePollCounterIsGreaterThan(resumePollCount);
        assertThat(tracer.getState()).isEqualTo(PAUSED);
        resumePollCount = monitor.simulateStressRelieved();
        monitor.waitUntilResumePollCounterIsGreaterThan(resumePollCount);
        assertThat(tracer.getState()).isEqualTo(RUNNING);
    }

    @Test
    void testTwoMonitors() {
        TestStressMonitor secondMonitor = new TestStressMonitor(tracer);
        circuitBreaker.registerStressMonitor(secondMonitor);

        assertThat(tracer.getState()).isEqualTo(RUNNING);
        int pausePollCount = monitor.simulateStress();
        monitor.waitUntilPausePollCounterIsGreaterThan(pausePollCount);
        assertThat(tracer.getState()).isEqualTo(PAUSED);

        pausePollCount = secondMonitor.simulateStress();
        // Since the tracer is in PAUSED state, it shouldn't poll the shouldPause method, only the shouldResume method
        int resumePollCount = secondMonitor.getResumePollCount();
        secondMonitor.waitUntilResumePollCounterIsGreaterThan(resumePollCount);
        assertThat(secondMonitor.getPausePollCount()).isEqualTo(pausePollCount);
        // tracer should still be in PAUSED mode
        assertThat(tracer.getState()).isEqualTo(PAUSED);

        resumePollCount = monitor.simulateStressRelieved();
        monitor.waitUntilResumePollCounterIsGreaterThan(resumePollCount);
        // tracer should still be in PAUSED mode, until ALL monitors allow resuming
        assertThat(tracer.getState()).isEqualTo(PAUSED);

        resumePollCount = secondMonitor.simulateStressRelieved();
        secondMonitor.waitUntilResumePollCounterIsGreaterThan(resumePollCount);
        assertThat(tracer.getState()).isEqualTo(RUNNING);

        circuitBreaker.unregisterStressMonitor(secondMonitor);
    }
}
