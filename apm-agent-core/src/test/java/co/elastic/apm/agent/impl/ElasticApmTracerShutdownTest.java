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
package co.elastic.apm.agent.impl;

import co.elastic.apm.agent.MockReporter;
import co.elastic.apm.agent.configuration.SpyConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class ElasticApmTracerShutdownTest {

    private ElasticApmTracer tracerImpl;
    private MockReporter reporter;

    @BeforeEach
    void setUp() {
        reporter = new MockReporter();
        tracerImpl = new ElasticApmTracerBuilder()
            .configurationRegistry(SpyConfiguration.createSpyConfig())
            .reporter(reporter)
            .buildAndStart();
    }

    @Test
    void testUsingSharedPoolOnShutdown() {
        AtomicBoolean shutdownHookExecuted = new AtomicBoolean(false);
        tracerImpl.addShutdownHook(() -> tracerImpl.getSharedSingleThreadedPool().submit(() -> shutdownHookExecuted.set(true)));
        tracerImpl.stop();
        await().untilTrue(shutdownHookExecuted);
    }

    @Test
    void testTracerStateIsRunningInTaskSubmittedInShutdownHook() {
        AtomicReference<Tracer.TracerState> tracerStateInShutdownHook = new AtomicReference<>();
        tracerImpl.addShutdownHook(() -> tracerImpl.getSharedSingleThreadedPool().submit(() -> tracerStateInShutdownHook.set(tracerImpl.getState())));
        tracerImpl.stop();
        reporter.awaitUntilAsserted(() -> assertThat(tracerStateInShutdownHook.get()).isNotNull());
        assertThat(tracerStateInShutdownHook.get()).isEqualTo(Tracer.TracerState.RUNNING);
    }
}
