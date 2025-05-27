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
package co.elastic.apm.agent.profiler;

import co.elastic.apm.agent.MockTracer;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.transaction.TraceContextImpl;
import co.elastic.apm.agent.objectpool.ObjectPoolFactoryImpl;
import co.elastic.apm.agent.testutils.DisabledOnAppleSilicon;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledForJreRange;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.JRE;
import org.junit.jupiter.api.condition.OS;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

public class SamplingProfilerQueueTest {

    @Test
    @DisabledOnOs(OS.WINDOWS)
    @DisabledOnAppleSilicon
    @DisabledForJreRange(min = JRE.JAVA_24)
    void testFillQueue() throws Exception {
        System.out.println(System.getProperty("os.name"));

        ElasticApmTracer tracer = MockTracer.create();
        when(tracer.getObjectPoolFactory()).thenReturn(new ObjectPoolFactoryImpl());

        SamplingProfiler profiler = new SamplingProfiler(tracer, new SystemNanoClock());

        profiler.setProfilingSessionOngoing(true);
        TraceContextImpl traceContext = TraceContextImpl.with64BitId(tracer);

        assertThat(profiler.onActivation(traceContext, null)).isTrue();
        long timeAfterFirstEvent = System.nanoTime();
        Thread.sleep(1);

        for (int i = 0; i < SamplingProfiler.RING_BUFFER_SIZE - 1; i++) {
            assertThat(profiler.onActivation(traceContext, null)).isTrue();
        }

        // no more free slots after adding RING_BUFFER_SIZE events
        assertThat(profiler.onActivation(traceContext, null)).isFalse();

        profiler.consumeActivationEventsFromRingBufferAndWriteToFile();

        // now there should be free slots
        assertThat(profiler.onActivation(traceContext, null)).isTrue();
    }
}
