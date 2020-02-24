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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.stagemonitor.configuration.ConfigurationRegistry;

import javax.annotation.Nullable;
import javax.management.ObjectName;
import java.lang.management.OperatingSystemMXBean;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

class SystemCpuStressMonitorTest {
    private static ElasticApmTracer tracer;
    private static SystemCpuStressMonitor systemCpuStressMonitor;
    @Nullable
    private static Method getNextValueMethod;

    private MBeanMock mbeanMock;

    @Nullable
    private List<Double> testValues;

    @BeforeAll
    static void setup() throws NoSuchMethodException {
        ConfigurationRegistry configurationRegistry = SpyConfiguration.createSpyConfig();
        CircuitBreakerConfiguration circuitBreakerConfiguration = configurationRegistry.getConfig(CircuitBreakerConfiguration.class);
        when(circuitBreakerConfiguration.getCpuConsecutiveMeasurements()).thenReturn(3);
        tracer = new ElasticApmTracerBuilder()
            .configurationRegistry(configurationRegistry)
            .reporter(new MockReporter())
            .build();
        getNextValueMethod = MBeanMock.class.getDeclaredMethod("getNextValue");
    }

    @AfterAll
    static void tearDown() {
        tracer.stop();
    }

    @BeforeEach
    void createMock() {
        mbeanMock = new MBeanMock();
        systemCpuStressMonitor = spy(new SystemCpuStressMonitor(tracer));
        when(systemCpuStressMonitor.getGetSystemCpuLoadMethod()).thenReturn(getNextValueMethod);
        when(systemCpuStressMonitor.getOperatingSystemBean()).thenReturn(mbeanMock);
    }

    @AfterEach
    void reset() {
        mbeanMock.reset();
        testValues = null;
    }

    @Test
    void testStressApi() throws Exception {
        testValues = List.of(0.4, 0.5, 0.99, 1.0, 1.0, 1.0, 0.6, 0.7, 0.98, 0.6, 0.7, 0.7, 0.99);
        for (int i = 0; i < 4; i++) {
            assertThat(systemCpuStressMonitor.isUnderStress()).isFalse();
        }
        for (int i = 0; i < 7; i++) {
            assertThat(systemCpuStressMonitor.isUnderStress()).isTrue();
        }
        assertThat(systemCpuStressMonitor.isUnderStress()).isFalse();
        assertThat(systemCpuStressMonitor.isUnderStress()).isFalse();
    }

    @Test
    void testReliefApi() throws Exception {
        testValues = List.of(0.4, 0.5, 0.99, 1.0, 1.0, 1.0, 0.6, 0.7, 0.98, 0.6, 0.7, 0.7, 0.99);
        for (int i = 0; i < 4; i++) {
            assertThat(systemCpuStressMonitor.isStressRelieved()).isTrue();
        }
        for (int i = 0; i < 7; i++) {
            assertThat(systemCpuStressMonitor.isStressRelieved()).isFalse();
        }
        assertThat(systemCpuStressMonitor.isStressRelieved()).isTrue();
        assertThat(systemCpuStressMonitor.isStressRelieved()).isTrue();
    }

    @Test
    void testWithInvalidValues() throws Exception {
        testValues = List.of(Double.NaN, 0.5, Double.POSITIVE_INFINITY, 0.99, 1.0, Double.NEGATIVE_INFINITY, Double.NaN,
            1.0, 1.0, 0.6, Double.NaN, 0.7, 0.98, 0.6, Double.NaN, 0.7, 0.7, 0.99);
        for (int i = 0; i < 7; i++) {
            assertThat(systemCpuStressMonitor.isUnderStress()).isFalse();
        }
        for (int i = 0; i < 9; i++) {
            assertThat(systemCpuStressMonitor.isUnderStress()).isTrue();
        }
        assertThat(systemCpuStressMonitor.isUnderStress()).isFalse();
        assertThat(systemCpuStressMonitor.isUnderStress()).isFalse();
    }

    @Test
    void testMidRangeThenStress() throws Exception {
        testValues = List.of(0.4, 0.5, 0.89, 0.88, 0.92, 0.96, 0.89, 0.97, 0.96, 1.0, 0.97, 0.91, 0.5);
        for (int i = 0; i < 9; i++) {
            assertThat(systemCpuStressMonitor.isUnderStress()).isFalse();
        }
        for (int i = 0; i < 4; i++) {
            assertThat(systemCpuStressMonitor.isUnderStress()).isTrue();
        }
    }

    @Test
    void testStressThenMidRange() throws Exception {
        testValues = List.of(0.4, 0.5, 0.99, 1.0, 1.0, 1.0, 0.89, 0.81, 0.87, 0.91, 0.87, 0.91, 0.5);
        for (int i = 0; i < 4; i++) {
            assertThat(systemCpuStressMonitor.isUnderStress()).isFalse();
        }
        for (int i = 0; i < 9; i++) {
            assertThat(systemCpuStressMonitor.isUnderStress()).isTrue();
        }
    }

    private class MBeanMock implements OperatingSystemMXBean {

        @Nullable
        private Iterator<Double> valueIterator;

        @Override
        public String getName() {
            return null;
        }

        @Override
        public String getArch() {
            return null;
        }

        @Override
        public String getVersion() {
            return null;
        }

        @Override
        public int getAvailableProcessors() {
            return 0;
        }

        @Override
        public double getSystemLoadAverage() {
            return 0;
        }

        @Override
        public ObjectName getObjectName() {
            return null;
        }

        void reset() {
            valueIterator = null;
        }

        double getNextValue() {
            if (valueIterator == null) {
                assertThat(testValues).isNotNull();
                valueIterator = testValues.iterator();
            }
            return valueIterator.next();
        }
    }
}
