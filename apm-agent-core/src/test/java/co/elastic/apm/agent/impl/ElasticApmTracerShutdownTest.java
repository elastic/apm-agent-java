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
