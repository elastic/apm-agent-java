package co.elastic.apm.agent.profiler;

import co.elastic.apm.agent.MockTracer;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.transaction.TraceContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SamplingProfilerQueueTest {

    @Test
    void testFillQueue() throws Exception {
        ElasticApmTracer tracer = MockTracer.create();

        SamplingProfiler profiler = new SamplingProfiler(tracer);
        profiler.setProfilingSessionOngoing(true);
        TraceContext traceContext = TraceContext.with64BitId(tracer);

        assertThat(profiler.onActivation(Thread.currentThread(), traceContext, null)).isTrue();
        long timeAfterFirstEvent = System.nanoTime();
        Thread.sleep(1);

        for (int i = 0; i < SamplingProfiler.RING_BUFFER_SIZE -1; i++) {
            assertThat(profiler.onActivation(Thread.currentThread(), traceContext, null)).isTrue();
        }

        // no more free slots after adding RING_BUFFER_SIZE events
        assertThat(profiler.onActivation(Thread.currentThread(), traceContext, null)).isFalse();

        // processing first event
        profiler.processActivationEventsUpTo(timeAfterFirstEvent);

        // now there should be one free slot
        assertThat(profiler.onActivation(Thread.currentThread(), traceContext, null)).isTrue();
        assertThat(profiler.onActivation(Thread.currentThread(), traceContext, null)).isFalse();
    }
}
