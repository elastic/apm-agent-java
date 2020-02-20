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
import co.elastic.apm.agent.configuration.SpyConfiguration;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.ElasticApmTracerBuilder;
import co.elastic.apm.agent.impl.TracerInternalApiUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.stagemonitor.configuration.ConfigurationRegistry;
import org.stagemonitor.configuration.source.SimpleSource;

import java.io.IOException;

import static co.elastic.apm.agent.impl.ElasticApmTracer.TracerState.PAUSED;
import static co.elastic.apm.agent.impl.ElasticApmTracer.TracerState.RUNNING;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.doReturn;

public class CircuitBreakerTest {

    private static final String TEST_CONFIG_SOURCE_NAME = "CircuitBreakerTest config source";

    private ElasticApmTracer tracer;
    private CircuitBreaker circuitBreaker;
    private TestStressMonitor monitor;
    private ConfigurationRegistry config;
    private CircuitBreakerConfiguration circuitBreakerConfiguration;

    @BeforeEach
    public void setup() {
        config = SpyConfiguration.createSpyConfig(new SimpleSource(TEST_CONFIG_SOURCE_NAME));
        circuitBreakerConfiguration = config.getConfig(CircuitBreakerConfiguration.class);
        doReturn(1L).when(circuitBreakerConfiguration).getStressMonitoringPollingInterval();
        tracer = new ElasticApmTracerBuilder()
            .configurationRegistry(config)
            .reporter(new MockReporter())
            .build();
        circuitBreaker = tracer.getLifecycleListener(CircuitBreaker.class);
        monitor = new TestStressMonitor(tracer);
        circuitBreaker.registerStressMonitor(monitor);
    }

    @AfterEach
    public void tearDown() {
        circuitBreaker.unregisterStressMonitor(monitor);
        tracer.stop();
    }

    @Test
    void testStressSimulation() {
        doReturn(true).when(circuitBreakerConfiguration).isCircuitBreakerEnabled();
        assertThat(tracer.getState()).isEqualTo(RUNNING);
        int pollCount = monitor.simulateStress();
        monitor.waitUntilPollCounterIsGreaterThan(pollCount + 1);
        assertThat(tracer.getState()).isEqualTo(PAUSED);
        // see that the tracer remains inactive for another couple of polls
        pollCount = monitor.getPollCount();
        monitor.waitUntilPollCounterIsGreaterThan(pollCount + 1);
        assertThat(tracer.getState()).isEqualTo(PAUSED);
        pollCount = monitor.simulateStressRelieved();
        monitor.waitUntilPollCounterIsGreaterThan(pollCount + 1);
        assertThat(tracer.getState()).isEqualTo(RUNNING);
    }

    @Test
    void testTwoMonitors() {
        doReturn(true).when(circuitBreakerConfiguration).isCircuitBreakerEnabled();

        TestStressMonitor secondMonitor = new TestStressMonitor(tracer);
        circuitBreaker.registerStressMonitor(secondMonitor);

        assertThat(tracer.getState()).isEqualTo(RUNNING);
        int pollCount = monitor.simulateStress();
        monitor.waitUntilPollCounterIsGreaterThan(pollCount + 1);
        assertThat(tracer.getState()).isEqualTo(PAUSED);

        pollCount = secondMonitor.simulateStress();
        secondMonitor.waitUntilPollCounterIsGreaterThan(pollCount + 1);
        // tracer should still be in PAUSED mode
        assertThat(tracer.getState()).isEqualTo(PAUSED);

        pollCount = monitor.simulateStressRelieved();
        monitor.waitUntilPollCounterIsGreaterThan(pollCount + 1);
        // tracer should still be in PAUSED mode, until ALL monitors allow resuming
        assertThat(tracer.getState()).isEqualTo(PAUSED);

        pollCount = secondMonitor.simulateStressRelieved();
        secondMonitor.waitUntilPollCounterIsGreaterThan(pollCount + 1);
        assertThat(tracer.getState()).isEqualTo(RUNNING);

        circuitBreaker.unregisterStressMonitor(secondMonitor);
    }

    @Test
    void testStressReliefThenReactivate() throws IOException {
        doReturn(true).when(circuitBreakerConfiguration).isCircuitBreakerEnabled();
        assertThat(tracer.getState()).isEqualTo(RUNNING);
        int pollCount = monitor.simulateStress();
        monitor.waitUntilPollCounterIsGreaterThan(pollCount + 1);
        assertThat(tracer.getState()).isEqualTo(PAUSED);
        TracerInternalApiUtils.setActiveConfig(config, false, TEST_CONFIG_SOURCE_NAME);
        pollCount = monitor.simulateStressRelieved();
        monitor.waitUntilPollCounterIsGreaterThan(pollCount + 1);
        // should still be PAUSED as the state is inactive
        assertThat(tracer.getState()).isEqualTo(PAUSED);
        TracerInternalApiUtils.setActiveConfig(config, true, TEST_CONFIG_SOURCE_NAME);
        assertThat(tracer.getState()).isEqualTo(RUNNING);
    }

    @Test
    void testReactivateThenStressRelief() throws IOException {
        doReturn(true).when(circuitBreakerConfiguration).isCircuitBreakerEnabled();
        assertThat(tracer.getState()).isEqualTo(RUNNING);
        TracerInternalApiUtils.setActiveConfig(config, false, TEST_CONFIG_SOURCE_NAME);
        assertThat(tracer.getState()).isEqualTo(PAUSED);
        monitor.simulateStress();
        TracerInternalApiUtils.setActiveConfig(config, true, TEST_CONFIG_SOURCE_NAME);
        // check that reactivation now has no effect even after waiting for the next resume poll
        int pollCount = monitor.getPollCount();
        monitor.waitUntilPollCounterIsGreaterThan(pollCount + 1);
        assertThat(tracer.getState()).isEqualTo(PAUSED);
        // check that stress relief now reactivates
        pollCount = monitor.simulateStressRelieved();
        monitor.waitUntilPollCounterIsGreaterThan(pollCount + 1);
        assertThat(tracer.getState()).isEqualTo(RUNNING);
    }

    @Test
    void testCircuitBreakerDisabled() throws IOException, InterruptedException {
        assertThat(tracer.getState()).isEqualTo(RUNNING);
        monitor.simulateStress();
        Thread.sleep(50);
        assertThat(tracer.getState()).isEqualTo(RUNNING);
        TracerInternalApiUtils.setActiveConfig(config, false, TEST_CONFIG_SOURCE_NAME);
        assertThat(tracer.getState()).isEqualTo(PAUSED);
        TracerInternalApiUtils.setActiveConfig(config, true, TEST_CONFIG_SOURCE_NAME);
        assertThat(tracer.getState()).isEqualTo(RUNNING);
    }

    @Test
    void testResumeWhenDisabledUnderStress() throws InterruptedException {
        doReturn(true).when(circuitBreakerConfiguration).isCircuitBreakerEnabled();
        assertThat(tracer.getState()).isEqualTo(RUNNING);
        int pollCount = monitor.simulateStress();
        monitor.waitUntilPollCounterIsGreaterThan(pollCount + 1);
        assertThat(tracer.getState()).isEqualTo(PAUSED);
        doReturn(false).when(circuitBreakerConfiguration).isCircuitBreakerEnabled();
        Thread.sleep(50);
        assertThat(tracer.getState()).isEqualTo(RUNNING);
    }
}
