/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2019 Elastic and contributors
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

        SamplingProfiler profiler = new SamplingProfiler(tracer, new SystemNanoClock());
        profiler.setProfilingSessionOngoing(true);
        TraceContext traceContext = TraceContext.with64BitId(tracer);

        assertThat(profiler.onActivation(traceContext, null)).isTrue();
        long timeAfterFirstEvent = System.nanoTime();
        Thread.sleep(1);

        for (int i = 0; i < SamplingProfiler.RING_BUFFER_SIZE -1; i++) {
            assertThat(profiler.onActivation(traceContext, null)).isTrue();
        }

        // no more free slots after adding RING_BUFFER_SIZE events
        assertThat(profiler.onActivation(traceContext, null)).isFalse();

        profiler.consumeActivationEventsFromRingBufferAndWriteToFile();

        // now there should be free slots
        assertThat(profiler.onActivation(traceContext, null)).isTrue();
    }
}
