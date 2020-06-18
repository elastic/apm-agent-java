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
package co.elastic.apm.agent.impl;

import co.elastic.apm.agent.MockReporter;
import co.elastic.apm.agent.bci.ElasticApmAgent;
import co.elastic.apm.agent.configuration.SpyConfiguration;
import co.elastic.apm.agent.context.AbstractLifecycleListener;
import co.elastic.apm.agent.objectpool.TestObjectPoolFactory;
import net.bytebuddy.agent.ByteBuddyAgent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.stagemonitor.configuration.ConfigurationRegistry;
import org.stagemonitor.configuration.source.SimpleSource;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static co.elastic.apm.agent.impl.ElasticApmTracer.TracerState.PAUSED;
import static co.elastic.apm.agent.impl.ElasticApmTracer.TracerState.RUNNING;
import static co.elastic.apm.agent.impl.ElasticApmTracer.TracerState.STOPPED;
import static org.assertj.core.api.Assertions.assertThat;

public class LifecycleTest {

    private static final String TEST_CONFIG_SOURCE_NAME = "LifecycleListenerTest config source";

    private ElasticApmTracer tracerImpl;
    private MockReporter reporter;
    private ConfigurationRegistry config;

    private TestObjectPoolFactory objectPoolFactory;

    @BeforeEach
    void setUp() {
        objectPoolFactory = new TestObjectPoolFactory();
        reporter = new MockReporter();
        config = SpyConfiguration.createSpyConfig(new SimpleSource(TEST_CONFIG_SOURCE_NAME));
        int initBefore = TestLifecycleListener.init.get();
        int startBefore = TestLifecycleListener.start.get();
        tracerImpl = new ElasticApmTracerBuilder()
            .configurationRegistry(config)
            .reporter(reporter)
            .withObjectPoolFactory(objectPoolFactory)
            .buildAndStart();
        assertThat(TestLifecycleListener.init.get()).isEqualTo(initBefore + 1);
        assertThat(TestLifecycleListener.start.get()).isEqualTo(startBefore + 1);
    }

    @AfterEach
    void cleanupAndCheck() {
        reporter.assertRecycledAfterDecrementingReferences();
        objectPoolFactory.checkAllPooledObjectsHaveBeenRecycled();
    }

    @Test
    void testLifecycleListener() {
        int pauseBefore = TestLifecycleListener.pause.get();
        int resumeBefore = TestLifecycleListener.resume.get();
        int stopBefore = TestLifecycleListener.stop.get();
        assertThat(tracerImpl.getState()).isEqualTo(RUNNING);
        assertThat(tracerImpl.isRunning()).isTrue();
        assertThat(TestLifecycleListener.pause.get()).isEqualTo(pauseBefore);
        assertThat(TestLifecycleListener.resume.get()).isEqualTo(resumeBefore);
        assertThat(TestLifecycleListener.stop.get()).isEqualTo(stopBefore);

        tracerImpl.pause();
        assertThat(tracerImpl.getState()).isEqualTo(PAUSED);
        assertThat(tracerImpl.isRunning()).isFalse();
        assertThat(TestLifecycleListener.pause.get()).isEqualTo(pauseBefore + 1);

        tracerImpl.resume();
        assertThat(tracerImpl.getState()).isEqualTo(RUNNING);
        assertThat(tracerImpl.isRunning()).isTrue();
        assertThat(TestLifecycleListener.resume.get()).isEqualTo(resumeBefore + 1);

        tracerImpl.stop();
        assertThat(tracerImpl.getState()).isEqualTo(STOPPED);
        assertThat(tracerImpl.isRunning()).isFalse();
        assertThat(TestLifecycleListener.stop.get()).isEqualTo(stopBefore + 1);
    }

    @Test
    void testActiveConfigurationChange() throws IOException {
        int pauseBefore = TestLifecycleListener.pause.get();
        int resumeBefore = TestLifecycleListener.resume.get();
        assertThat(tracerImpl.getState()).isEqualTo(RUNNING);

        // checking no effect changing to true when RUNNING
        TracerInternalApiUtils.setRecordingConfig(config, true, TEST_CONFIG_SOURCE_NAME);
        assertThat(tracerImpl.getState()).isEqualTo(RUNNING);
        assertThat(TestLifecycleListener.pause.get()).isEqualTo(pauseBefore);
        TracerInternalApiUtils.setRecordingConfig(config, false, TEST_CONFIG_SOURCE_NAME);
        assertThat(tracerImpl.getState()).isEqualTo(PAUSED);
        assertThat(TestLifecycleListener.pause.get()).isEqualTo(pauseBefore + 1);

        // checking no effect changing to false when PAUSED
        TracerInternalApiUtils.setRecordingConfig(config, false, TEST_CONFIG_SOURCE_NAME);
        assertThat(tracerImpl.getState()).isEqualTo(PAUSED);
        assertThat(TestLifecycleListener.resume.get()).isEqualTo(resumeBefore);
        TracerInternalApiUtils.setRecordingConfig(config, true, TEST_CONFIG_SOURCE_NAME);
        assertThat(tracerImpl.getState()).isEqualTo(RUNNING);
        assertThat(TestLifecycleListener.resume.get()).isEqualTo(resumeBefore + 1);
    }

    @Test
    void testStartInactive() throws IOException {
        ConfigurationRegistry configRegistry = SpyConfiguration.createSpyConfig(new SimpleSource(TEST_CONFIG_SOURCE_NAME));
        TracerConfiguration localTracerConfiguration = configRegistry.getConfig(TracerConfiguration.class);
        localTracerConfiguration.getRecordingConfig().update(Boolean.FALSE, TEST_CONFIG_SOURCE_NAME);
        ElasticApmTracer tracer = new ElasticApmTracerBuilder()
            .configurationRegistry(configRegistry)
            .reporter(new MockReporter())
            .buildAndStart();
        assertThat(tracer.isRunning()).isFalse();
    }

    @Test
    void testStartDisabled() throws Exception {
        ConfigurationRegistry configRegistry = SpyConfiguration.createSpyConfig(SimpleSource.forTest("enabled", "false"));
        AtomicBoolean initialized = new AtomicBoolean();
        AtomicBoolean started = new AtomicBoolean();
        ElasticApmTracer tracer = new ElasticApmTracerBuilder()
            .configurationRegistry(configRegistry)
            .reporter(new MockReporter())
            .withLifecycleListener(new AbstractLifecycleListener() {
                @Override
                public void init(ElasticApmTracer tracer) {
                    initialized.set(true);
                }

                @Override
                public void start(ElasticApmTracer tracer) throws Exception {
                    started.set(true);
                }
            })
            .build();
        ElasticApmAgent.initInstrumentation(tracer, ByteBuddyAgent.install());
        assertThat(tracer.isRunning()).isFalse();
        assertThat(initialized).isTrue();
        assertThat(started).isFalse();
    }

    /*
     * Has an entry in
     * src/test/resources/META-INF/services/co.elastic.apm.agent.context.LifecycleListener
     */
    public static class TestLifecycleListener extends AbstractLifecycleListener {

        public static final AtomicInteger init = new AtomicInteger();
        public static final AtomicInteger start = new AtomicInteger();
        public static final AtomicInteger pause = new AtomicInteger();
        public static final AtomicInteger resume = new AtomicInteger();
        public static final AtomicInteger stop = new AtomicInteger();

        public TestLifecycleListener() {
            init.incrementAndGet();
        }

        @Override
        public void start(ElasticApmTracer tracer) {
            start.incrementAndGet();
        }

        @Override
        public void pause() throws Exception {
            pause.incrementAndGet();
        }

        @Override
        public void resume() throws Exception {
            resume.incrementAndGet();
        }

        @Override
        public void stop() {
            stop.incrementAndGet();
        }
    }
}
