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
package co.elastic.apm.agent.impl.transaction;

import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.sampling.ConstantSampler;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class TraceContextTest {

    @Test
    void parseFromTraceParentHeaderNotRecorded() {
        final TraceContext traceContext = TraceContext.with64BitId(mock(ElasticApmTracer.class));
        final String header = "00-0af7651916cd43dd8448eb211c80319c-b9c7c989f97918e1-00";
        assertThat(traceContext.asChildOf(header)).isTrue();
        assertThat(traceContext.isSampled()).isFalse();
        assertThat(traceContext.getOutgoingTraceParentHeader().toString()).endsWith("-00");
    }

    @Test
    void parseFromTraceParentHeaderRecorded() {
        final TraceContext traceContext = TraceContext.with64BitId(mock(ElasticApmTracer.class));
        final String header = "00-0af7651916cd43dd8448eb211c80319c-b9c7c989f97918e1-01";
        assertThat(traceContext.asChildOf(header)).isTrue();
        assertThat(traceContext.isSampled()).isTrue();
        assertThat(traceContext.getOutgoingTraceParentHeader().toString()).endsWith("-01");
    }

    @Test
    void parseFromTraceParentHeaderUnsupportedFlag() {
        final TraceContext traceContext = TraceContext.with64BitId(mock(ElasticApmTracer.class));
        final String header = "00-0af7651916cd43dd8448eb211c80319c-b9c7c989f97918e1-03";
        assertThat(traceContext.asChildOf(header)).isTrue();
        assertThat(traceContext.isSampled()).isTrue();
        assertThat(traceContext.getOutgoingTraceParentHeader().toString()).endsWith("-03");
    }

    @Test
    void outgoingHeader() {
        final TraceContext traceContext = TraceContext.with64BitId(mock(ElasticApmTracer.class));
        final String header = "00-0af7651916cd43dd8448eb211c80319c-b9c7c989f97918e1-03";
        assertThat(traceContext.asChildOf(header)).isTrue();
        assertThat(traceContext.getOutgoingTraceParentHeader().toString())
            .isEqualTo("00-0af7651916cd43dd8448eb211c80319c-" + traceContext.getId().toString() + "-03");
    }

    @Test
    void outgoingHeaderRootSpan() {
        final TraceContext traceContext = TraceContext.with64BitId(mock(ElasticApmTracer.class));
        traceContext.asRootSpan(ConstantSampler.of(true));
        assertThat(traceContext.isSampled()).isTrue();
        assertThat(traceContext.getOutgoingTraceParentHeader().toString()).hasSize(55);
        assertThat(traceContext.getOutgoingTraceParentHeader().toString()).startsWith("00-");
        assertThat(traceContext.getOutgoingTraceParentHeader().toString()).endsWith("-01");
    }

    @Test
    void parseFromTraceParentHeader_notSampled() {
        final TraceContext traceContext = TraceContext.with64BitId(mock(ElasticApmTracer.class));
        final String header = "00-0af7651916cd43dd8448eb211c80319c-b9c7c989f97918e1-00";
        assertThat(traceContext.asChildOf(header)).isTrue();
        assertThat(traceContext.isSampled()).isFalse();
        assertThat(traceContext.getIncomingTraceParentHeader()).isEqualTo(header);
    }

    @Test
    void testResetState() {
        final TraceContext traceContext = TraceContext.with64BitId(mock(ElasticApmTracer.class));
        traceContext.asChildOf("00-0af7651916cd43dd8448eb211c80319c-b9c7c989f97918e1-00");
        traceContext.resetState();
        assertThat(traceContext.getIncomingTraceParentHeader()).isEqualTo("00-00000000000000000000000000000000-0000000000000000-00");
    }

    @Test
    void testResetOutgoingHeader() {
        final TraceContext traceContext = TraceContext.with64BitId(mock(ElasticApmTracer.class));
        String traceParentHeader = traceContext.getOutgoingTraceParentHeader().toString();
        traceContext.asChildOf("00-0af7651916cd43dd8448eb211c80319c-b9c7c989f97918e1-00");
        assertThat(traceContext.getOutgoingTraceParentHeader().toString()).isNotEqualTo(traceParentHeader);
    }

    @Test
    void testRandomValue() {
        final TraceContext traceContext = TraceContext.with64BitId(mock(ElasticApmTracer.class));
        traceContext.asRootSpan(ConstantSampler.of(true));
        assertThat(traceContext.getTraceId().isEmpty()).isFalse();
        assertThat(traceContext.getParentId().isEmpty()).isTrue();
        assertThat(traceContext.getId().isEmpty()).isFalse();
    }

    @Test
    void testSetSampled() {
        final TraceContext traceContext = TraceContext.with64BitId(mock(ElasticApmTracer.class));
        traceContext.asRootSpan(ConstantSampler.of(false));
        assertThat(traceContext.isSampled()).isFalse();
        traceContext.setRecorded(true);
        assertThat(traceContext.isSampled()).isTrue();
        traceContext.setRecorded(false);
        assertThat(traceContext.isSampled()).isFalse();
    }

    @Test
    void testPropagateTransactionIdForUnsampledSpan() {
        final TraceContext rootContext = TraceContext.with64BitId(mock(ElasticApmTracer.class));
        rootContext.asRootSpan(ConstantSampler.of(false));

        final TraceContext childContext = TraceContext.with64BitId(mock(ElasticApmTracer.class));
        childContext.asChildOf(rootContext);

        assertThat(childContext.getOutgoingTraceParentHeader().toString()).doesNotContain(childContext.getId().toString());
        assertThat(childContext.getOutgoingTraceParentHeader().toString()).contains(rootContext.getId().toString());
    }

    @Test
    void testUnknownVersion() {
        assertValid("42-0af7651916cd43dd8448eb211c80319c-b9c7c989f97918e1-01");
    }

    @Test
    void testUnknownExtraStuff() {
        assertValid("42-0af7651916cd43dd8448eb211c80319c-b9c7c989f97918e1-01-unknown-extra-stuff");
    }

    // If a traceparent header is invalid, ignore it and create a new root context

    @Test
    void testInvalidHeader_traceIdAllZeroes() {
        assertInvalid("00-00000000000000000000000000000000-b9c7c989f97918e1-00");
    }

    @Test
    void testInvalidHeader_spanIdAllZeroes() {
        assertInvalid("00-0af7651916cd43dd8448eb211c80319-0000000000000000-00");
    }

    @Test
    void testInvalidHeader_nonHexChars() {
        assertInvalid("00-$af7651916cd43dd8448eb211c80319c-b9c7c989f97918e1-03");
    }

    @Test
    void testInvalidHeader_traceIdTooLong() {
        assertInvalid("00-00af7651916cd43dd8448eb211c80319c-9c7c989f97918e1-03");
    }

    @Test
    void testInvalidHeader_traceIdTooShort() {
        assertInvalid("00-af7651916cd43dd8448eb211c80319c-0b9c7c989f97918e1-03");
    }

    @Test
    void testInvalidHeader_invalidTotalLength() {
        assertInvalid("00-0af7651916cd43dd8448eb211c80319-b9c7c989f97918e1-00");
    }

    private void assertInvalid(String s) {
        final TraceContext traceContext = TraceContext.with64BitId(mock(ElasticApmTracer.class));
        assertThat(traceContext.asChildOf(s)).isFalse();
    }

    private void assertValid(String s) {
        final TraceContext traceContext = TraceContext.with64BitId(mock(ElasticApmTracer.class));
        assertThat(traceContext.asChildOf(s)).isTrue();
    }
}
