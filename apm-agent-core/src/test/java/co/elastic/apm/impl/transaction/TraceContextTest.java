/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 the original author or authors
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package co.elastic.apm.impl.transaction;

import co.elastic.apm.impl.sampling.ConstantSampler;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TraceContextTest {

    @Test
    void parseFromTraceParentHeader_sampled() {
        final TraceContext traceContext = new TraceContext();
        final String header = "00-0af7651916cd43dd8448eb211c80319c-b9c7c989f97918e1-01";
        traceContext.asChildOf(header);
        assertThat(traceContext.isSampled()).isTrue();
        assertThat(traceContext.getIncomingTraceParentHeader()).isEqualTo(header);
    }

    @Test
    void outgoingHeader() {
        final TraceContext traceContext = new TraceContext();
        final String header = "00-0af7651916cd43dd8448eb211c80319c-b9c7c989f97918e1-01";
        traceContext.asChildOf(header);
        assertThat(traceContext.getOutgoingTraceParentHeader().toString())
            .isEqualTo("00-0af7651916cd43dd8448eb211c80319c-"+ traceContext.getId().toString() + "-01");
    }

    @Test
    void parseFromTraceParentHeader_notSampled() {
        final TraceContext traceContext = new TraceContext();
        final String header = "00-0af7651916cd43dd8448eb211c80319c-b9c7c989f97918e1-00";
        traceContext.asChildOf(header);
        assertThat(traceContext.isSampled()).isFalse();
        assertThat(traceContext.getIncomingTraceParentHeader()).isEqualTo(header);
    }

    @Test
    void testResetState() {
        final TraceContext traceContext = new TraceContext();
        traceContext.asChildOf("00-0af7651916cd43dd8448eb211c80319c-b9c7c989f97918e1-00");
        traceContext.resetState();
        assertThat(traceContext.getIncomingTraceParentHeader()).isEqualTo("00-00000000000000000000000000000000-0000000000000000-00");
    }

    @Test
    void testRandomValue() {
        final TraceContext traceContext = new TraceContext();
        traceContext.asRootSpan(ConstantSampler.of(true));
        assertThat(traceContext.getTraceId().isEmpty()).isFalse();
        assertThat(traceContext.getParentId().asLong()).isZero();
        assertThat(traceContext.getId().asLong()).isNotZero();
        assertThat(traceContext.isSampled()).isTrue();
    }

    @Test
    void testSetSampled() {
        final TraceContext traceContext = new TraceContext();
        traceContext.asRootSpan(ConstantSampler.of(false));
        assertThat(traceContext.isSampled()).isFalse();
        traceContext.setSampled(true);
        assertThat(traceContext.isSampled()).isTrue();
        traceContext.setSampled(false);
        assertThat(traceContext.isSampled()).isFalse();
    }
}
