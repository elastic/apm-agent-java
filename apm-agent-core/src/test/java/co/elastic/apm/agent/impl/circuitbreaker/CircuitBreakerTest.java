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
import org.awaitility.core.ConditionFactory;
import org.awaitility.core.ThrowingRunnable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.stagemonitor.configuration.ConfigurationRegistry;
import org.stagemonitor.configuration.source.SimpleSource;
import wiremock.com.google.common.util.concurrent.AtomicDouble;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static co.elastic.apm.agent.impl.ElasticApmTracer.TracerState.PAUSED;
import static co.elastic.apm.agent.impl.ElasticApmTracer.TracerState.RUNNING;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

public class CircuitBreakerTest {

    private static final String TEST_CONFIG_SOURCE_NAME = "CircuitBreakerTest config source";

    private ElasticApmTracer tracer;
    private CircuitBreaker circuitBreaker;
    private TestStressMonitor monitor;
    private ConfigurationRegistry config;
    private ConfigThreadSafeWrapper circuitBreakerConfiguration;

    @BeforeEach
    public void setup() {

        ConfigurationRegistry defaultConfig = SpyConfiguration.createSpyConfig(new SimpleSource(TEST_CONFIG_SOURCE_NAME));
        circuitBreakerConfiguration = new ConfigThreadSafeWrapper(defaultConfig.getConfig(CircuitBreakerConfiguration.class));

        // fast polling for testing
        circuitBreakerConfiguration.stressMonitoringPollingIntervalMillis.set(1L);

        // disable gc stress monitor
        circuitBreakerConfiguration.gcStressThreshold.set(1D);
        circuitBreakerConfiguration.gcReliefThreshold.set(0D);
        // disable cpu stress monitor
        circuitBreakerConfiguration.systemCpuStressThreshold.set(1D);
        circuitBreakerConfiguration.systemCpuReliefThreshold.set(0D);

        config = spy(defaultConfig);
        doReturn(circuitBreakerConfiguration).when(config).getConfig(CircuitBreakerConfiguration.class);

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
        circuitBreakerConfiguration.circuitBreakerEnabled.set(true);
        assertRunning();

        monitor.simulateStress();
        awaitPaused();

        monitor.simulateStressRelieved();
        awaitRunning();
    }

    @Test
    void testTwoMonitors() {
        circuitBreakerConfiguration.circuitBreakerEnabled.set(true);

        TestStressMonitor secondMonitor = new TestStressMonitor(tracer);
        circuitBreaker.registerStressMonitor(secondMonitor);

        assertRunning();

        // adding stress from a single monitor should pause tracer

        monitor.simulateStress();
        awaitPaused();

        // adding stress from a second monitor should not resume tracer
        secondMonitor.simulateStress();

        // tracer should still be in PAUSED mode
        assertPaused();

        simulateReliefAndWaitPoll(monitor);

        // tracer should still be in PAUSED mode, until ALL monitors allow resuming
        assertPaused();

        simulateReliefAndWaitPoll(secondMonitor);

        assertRunning();

        circuitBreaker.unregisterStressMonitor(secondMonitor);
    }

    private static void simulateReliefAndWaitPoll(TestStressMonitor monitor){
        awaitHasBeenPolled(monitor, monitor.simulateStressRelieved());
    }

    private static void awaitHasBeenPolled(TestStressMonitor monitor, final int pollCount) {
        awaitAssert(() -> assertThat(monitor.getPollCount()).isGreaterThan(pollCount + 1));
    }


    @Test
    void testPauseThroughConfigUnderStressThenResumeThroughConfig() throws IOException, InterruptedException {
        // stress pauses when recording enabled
        // timeline   1  2  3  4  5
        // stress     ---sssssss-----
        // recording  xxxxxx------xxx
        // state      rrr---------rrr

        // 1
        circuitBreakerConfiguration.circuitBreakerEnabled.set(true);
        assertRunning();

        // 2
        monitor.simulateStress();
        awaitPaused();

        // 3 recording = false under stress should not change state
        TracerInternalApiUtils.setRecordingConfig(config, false, TEST_CONFIG_SOURCE_NAME);
        assertState(this::assertPaused);

        // 4 stress ends, should still be paused due to recording = false
        monitor.simulateStressRelieved();
        assertState(this::assertPaused);

        // 5
        // configuration recording = true should make it run again
        TracerInternalApiUtils.setRecordingConfig(config, true, TEST_CONFIG_SOURCE_NAME);
        awaitRunning();
    }

    @Test
    void testPauseThroughConfigThenResumeOnlyWhenStressRelieved() throws IOException, InterruptedException {
        // enable recording while under stress does not trigger runnable state
        // timeline   1  2  3  4  5
        // stress     ------ssssss----
        // recording  xxx------xxxxxx
        // state      rrr---------rrr

        // 1
        circuitBreakerConfiguration.circuitBreakerEnabled.set(true);
        assertRunning();

        // 2 recording = false should pause
        TracerInternalApiUtils.setRecordingConfig(config, false, TEST_CONFIG_SOURCE_NAME);
        awaitPaused();

        // 3 stress should keep it paused
        monitor.simulateStress();
        assertState(this::assertPaused);

        // 4 should not resume tracer as we are under stress
        TracerInternalApiUtils.setRecordingConfig(config, true, TEST_CONFIG_SOURCE_NAME);
        assertState(this::assertPaused);

        // 5 stress relief now resumes tracer
        monitor.simulateStressRelieved();
        awaitRunning();
    }


    @Test
    void testCircuitBreakerDisabled() throws IOException, InterruptedException {
        assertThat(circuitBreakerConfiguration.isCircuitBreakerEnabled()).isFalse();

        assertRunning();

        monitor.simulateStress();
        assertState(this::assertRunning);

        TracerInternalApiUtils.setRecordingConfig(config, false, TEST_CONFIG_SOURCE_NAME);
        assertPaused();
        TracerInternalApiUtils.setRecordingConfig(config, true, TEST_CONFIG_SOURCE_NAME);
        assertRunning();
    }

    @Test
    void testResumeWhenDisabledUnderStress() {

        circuitBreakerConfiguration.circuitBreakerEnabled.set(true);
        assertRunning();

        monitor.simulateStress();
        awaitPaused();

        circuitBreakerConfiguration.circuitBreakerEnabled.set(false);
        awaitRunning();
    }

    private void awaitPaused() {
        awaitAssert(this::assertPaused);
    }

    private void assertRunning() {
        assertThat(tracer.getState()).isEqualTo(RUNNING);
    }

    private void awaitRunning() {
        awaitAssert(this::assertRunning);
    }

    private void assertPaused() {
        assertThat(tracer.getState()).isEqualTo(PAUSED);
    }

    private void assertState(Runnable assertion) throws InterruptedException {
        for (long i = 0; i < 10; i++) {
            assertion.run();
            Thread.sleep(1);
        }
        assertion.run();
    }

    private static void awaitAssert(ThrowingRunnable assertion) {
        doAwait().untilAsserted(assertion);
    }

    private static ConditionFactory doAwait() {
        return await()
            .pollInterval(1, TimeUnit.MILLISECONDS)
            .timeout(50, TimeUnit.MILLISECONDS);

    }


    /**
     * We have to use a thread-safe wrapper because sharing mocked/stubbed classes
     */
    private static class ConfigThreadSafeWrapper extends CircuitBreakerConfiguration {

        final AtomicBoolean circuitBreakerEnabled;
        final AtomicLong stressMonitoringPollingIntervalMillis;
        final AtomicDouble gcStressThreshold;
        final AtomicDouble gcReliefThreshold;
        final AtomicLong cpuStressDurationThresholdMillis;
        final AtomicDouble systemCpuStressThreshold;
        final AtomicDouble systemCpuReliefThreshold;

        public ConfigThreadSafeWrapper(CircuitBreakerConfiguration defaultConfig) {
            this.circuitBreakerEnabled = new AtomicBoolean(defaultConfig.isCircuitBreakerEnabled());
            this.stressMonitoringPollingIntervalMillis = new AtomicLong(defaultConfig.getStressMonitoringPollingIntervalMillis());
            this.gcStressThreshold = new AtomicDouble(defaultConfig.getGcStressThreshold());
            this.gcReliefThreshold = new AtomicDouble(defaultConfig.getGcReliefThreshold());
            this.cpuStressDurationThresholdMillis = new AtomicLong(defaultConfig.getCpuStressDurationThresholdMillis());
            this.systemCpuStressThreshold = new AtomicDouble(defaultConfig.getSystemCpuStressThreshold());
            this.systemCpuReliefThreshold = new AtomicDouble(defaultConfig.getSystemCpuReliefThreshold());
        }

        @Override
        public boolean isCircuitBreakerEnabled() {
            return circuitBreakerEnabled.get();
        }

        @Override
        public long getStressMonitoringPollingIntervalMillis() {
            return stressMonitoringPollingIntervalMillis.get();
        }

        @Override
        public double getGcStressThreshold() {
            return gcStressThreshold.get();
        }

        @Override
        public double getGcReliefThreshold() {
            return gcReliefThreshold.get();
        }

        @Override
        public long getCpuStressDurationThresholdMillis() {
            return cpuStressDurationThresholdMillis.get();
        }

        @Override
        public double getSystemCpuStressThreshold() {
            return systemCpuStressThreshold.get();
        }

        @Override
        public double getSystemCpuReliefThreshold() {
            return systemCpuReliefThreshold.get();
        }
    }
}
