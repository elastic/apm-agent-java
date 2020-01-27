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
package co.elastic.apm.agent.impl.transaction;

import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.sampling.ConstantSampler;
import co.elastic.apm.agent.util.HexUtils;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class TraceContextTest {

    /**
     * Test flow:
     * 1.  create a parent context from a fixed string
     * 2.  create a child based on the string header - test {@link TraceContext#asChildOf(String)}
     * 3.  create a grandchild based on binary header - test both {@link TraceContext#getOutgoingTraceParentBinaryHeader()}
     * and {@link TraceContext#asChildOf(byte[])}
     * 4.  create a second grandchild based on text header - test both {@link TraceContext#getOutgoingTraceParentTextHeader()}
     * and {@link TraceContext#asChildOf(String)}
     *
     * @param flagsValue tested flags
     * @param isSampled  whether to test context propagation of sampled trace or not
     */
    private void mixTextAndBinaryParsingAndContextPropagation(String flagsValue, boolean isSampled) {
        final String parentHeader = "00-0af7651916cd43dd8448eb211c80319c-b9c7c989f97918e1-" + flagsValue;
        final TraceContext child = TraceContext.with64BitId(mock(ElasticApmTracer.class));
        assertThat(TraceContext.fromTraceparentHeader().asChildOf(child, parentHeader)).isTrue();
        assertThat(child.getTraceContext().getTraceId().toString()).isEqualTo("0af7651916cd43dd8448eb211c80319c");
        assertThat(child.getTraceContext().getParentId().toString()).isEqualTo("b9c7c989f97918e1");
        assertThat(child.getTraceContext().getId()).isNotEqualTo(child.getTraceContext().getParentId());
        assertThat(child.isSampled()).isEqualTo(isSampled);

        // create a grandchild to ensure proper regenerated trace context
        final TraceContext grandchild1 = TraceContext.with64BitId(mock(ElasticApmTracer.class));
        assertThat(TraceContext.fromTraceparentBinaryHeader().asChildOf(grandchild1, child.getOutgoingTraceParentBinaryHeader())).isTrue();
        assertThat(grandchild1.getTraceContext().getTraceId().toString()).isEqualTo("0af7651916cd43dd8448eb211c80319c");
        assertThat(grandchild1.getTraceContext().getParentId().toString()).isEqualTo(child.getTraceContext().getId().toString());
        assertThat(grandchild1.getTraceContext().getId()).isNotEqualTo(child.getTraceContext().getId());
        assertThat(grandchild1.isSampled()).isEqualTo(isSampled);

        String childHeader = child.getOutgoingTraceParentTextHeader().toString();
        assertThat(childHeader).endsWith("-" + flagsValue);
        final TraceContext grandchild2 = TraceContext.with64BitId(mock(ElasticApmTracer.class));
        assertThat(grandchild2.asChildOf(childHeader)).isTrue();
        assertThat(grandchild2.getTraceContext().getTraceId().toString()).isEqualTo("0af7651916cd43dd8448eb211c80319c");
        assertThat(grandchild2.getTraceContext().getParentId().toString()).isEqualTo(child.getTraceContext().getId().toString());
        assertThat(grandchild2.getTraceContext().getId()).isNotEqualTo(child.getTraceContext().getId());
        assertThat(grandchild2.isSampled()).isEqualTo(isSampled);
    }

    @Test
    void parseFromTraceParentHeaderNotRecorded() {
        mixTextAndBinaryParsingAndContextPropagation("00", false);
    }

    @Test
    void parseFromTraceParentHeaderRecorded() {
        mixTextAndBinaryParsingAndContextPropagation("01", true);
    }

    @Test
    void parseFromTraceParentHeaderUnsupportedFlag() {
        mixTextAndBinaryParsingAndContextPropagation("03", true);
    }

    private void verifyTraceContextContents(String traceContext, String expectedTraceId, String expectedParentId,
                                            String expectedVersion, String expectedFlags) {
        String[] parts = traceContext.split("-");
        assertThat(parts[0]).isEqualTo(expectedVersion);
        assertThat(parts[1]).isEqualTo(expectedTraceId);
        assertThat(parts[2]).isEqualTo(expectedParentId);
        assertThat(parts[3]).isEqualTo(expectedFlags);
    }

    private void verifyTraceContextContents(byte[] traceContext, String expectedTraceId, String expectedParentId,
                                            byte expectedVersion, byte expectedFlags) {
        assertThat(traceContext[0]).isEqualTo(expectedVersion);
        assertThat(traceContext[1]).isEqualTo((byte) 0b0000_0000);
        StringBuilder sb = new StringBuilder();
        HexUtils.writeBytesAsHex(traceContext, 2, 16, sb);
        assertThat(sb.toString()).isEqualTo(expectedTraceId);
        assertThat(traceContext[18]).isEqualTo((byte) 0b0000_0001);
        sb.setLength(0);
        HexUtils.writeBytesAsHex(traceContext, 19, 8, sb);
        assertThat(sb.toString()).isEqualTo(expectedParentId);
        assertThat(traceContext[27]).isEqualTo((byte) 0b0000_0010);
        assertThat(traceContext[28]).isEqualTo(expectedFlags);
    }

    @Test
    void outgoingHeader() {
        final TraceContext traceContext = TraceContext.with64BitId(mock(ElasticApmTracer.class));
        final String header = "00-0af7651916cd43dd8448eb211c80319c-b9c7c989f97918e1-03";
        assertThat(traceContext.asChildOf(header)).isTrue();
        String parentId = traceContext.getId().toString();
        verifyTraceContextContents(traceContext.getOutgoingTraceParentTextHeader().toString(),
            "0af7651916cd43dd8448eb211c80319c", parentId, "00", "03");
        verifyTraceContextContents(traceContext.getOutgoingTraceParentBinaryHeader(),
            "0af7651916cd43dd8448eb211c80319c", parentId, (byte) 0x00, (byte) 0x03);
    }

    @Test
    void outgoingHeaderRootSpan() {
        final TraceContext traceContext = TraceContext.with64BitId(mock(ElasticApmTracer.class));
        traceContext.asRootSpan(ConstantSampler.of(true));
        assertThat(traceContext.isSampled()).isTrue();
        String outgoingStringHeader = traceContext.getOutgoingTraceParentTextHeader().toString();
        assertThat(outgoingStringHeader).hasSize(55);
        verifyTraceContextContents(outgoingStringHeader, traceContext.getTraceId().toString(),
            traceContext.getId().toString(), "00", "01");
        byte[] outgoingBinaryHeader = traceContext.getOutgoingTraceParentBinaryHeader();
        assertThat(outgoingBinaryHeader.length).isEqualTo(29);
        verifyTraceContextContents(outgoingBinaryHeader, traceContext.getTraceId().toString(),
            traceContext.getId().toString(), (byte) 0x00, (byte) 0x01);
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
    void testResetOutgoingTextHeader() {
        final TraceContext traceContext = TraceContext.with64BitId(mock(ElasticApmTracer.class));
        String traceParentHeader = traceContext.getOutgoingTraceParentTextHeader().toString();
        traceContext.asChildOf("00-0af7651916cd43dd8448eb211c80319c-b9c7c989f97918e1-00");
        assertThat(traceContext.getOutgoingTraceParentTextHeader().toString()).isNotEqualTo(traceParentHeader);
    }

    @Test
    void testResetOutgoingBinaryHeader() {
        final TraceContext traceContext = TraceContext.with64BitId(mock(ElasticApmTracer.class));
        byte[] traceParentHeader = new byte[29];
        System.arraycopy(traceContext.getOutgoingTraceParentBinaryHeader(), 0, traceParentHeader, 0, 29);
        traceContext.asChildOf("00-0af7651916cd43dd8448eb211c80319c-b9c7c989f97918e1-00");
        assertThat(traceContext.getOutgoingTraceParentBinaryHeader()).isNotEqualTo(traceParentHeader);
        traceContext.resetState();
        assertThat(traceContext.getOutgoingTraceParentBinaryHeader()).isEqualTo(traceParentHeader);
    }

    @Test
    void testCopyFrom() {
        final TraceContext traceContext = TraceContext.with64BitId(mock(ElasticApmTracer.class));
        traceContext.asChildOf("00-0af7651916cd43dd8448eb211c80319c-b9c7c989f97918e1-01");

        final TraceContext other = TraceContext.with64BitId(mock(ElasticApmTracer.class));
        other.asChildOf("00-8448ebb9c7c989f97918e11916cd43dd-211c80319c0af765-00");

        assertThat(traceContext.getTraceId()).isNotEqualTo(other.getTraceId());
        assertThat(traceContext.getParentId()).isNotEqualTo(other.getParentId());
        assertThat(traceContext.isSampled()).isNotEqualTo(other.isSampled());
        assertThat(traceContext.getOutgoingTraceParentTextHeader()).isNotEqualTo(other.getOutgoingTraceParentTextHeader());
        assertThat(traceContext.getOutgoingTraceParentBinaryHeader()).isNotEqualTo(other.getOutgoingTraceParentBinaryHeader());

        other.copyFrom(traceContext);
        assertThat(traceContext.getTraceId()).isEqualTo(other.getTraceId());
        assertThat(traceContext.getParentId()).isEqualTo(other.getParentId());
        assertThat(traceContext.isSampled()).isEqualTo(other.isSampled());
        assertThat(traceContext.getOutgoingTraceParentTextHeader().toString()).isEqualTo(other.getOutgoingTraceParentTextHeader().toString());
        assertThat(traceContext.getOutgoingTraceParentBinaryHeader()).isEqualTo(other.getOutgoingTraceParentBinaryHeader());
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
    void testPropagateTransactionIdForUnsampledSpan_TextFormat() {
        final TraceContext rootContext = TraceContext.with64BitId(mock(ElasticApmTracer.class));
        rootContext.asRootSpan(ConstantSampler.of(false));

        final TraceContext childContext = TraceContext.with64BitId(mock(ElasticApmTracer.class));
        childContext.asChildOf(rootContext);

        verifyTraceContextContents(childContext.getOutgoingTraceParentTextHeader().toString(),
            childContext.getTraceId().toString(), rootContext.getId().toString(), "00", "00");
        verifyTraceContextContents(childContext.getOutgoingTraceParentBinaryHeader(),
            childContext.getTraceId().toString(), rootContext.getId().toString(), (byte) 0x00, (byte) 0x00);
    }

    @Test
    void testPropagateSpanIdForSampledSpan_TextFormat() {
        final TraceContext rootContext = TraceContext.with64BitId(mock(ElasticApmTracer.class));
        rootContext.asRootSpan(ConstantSampler.of(true));

        final TraceContext childContext = TraceContext.with64BitId(mock(ElasticApmTracer.class));
        childContext.asChildOf(rootContext);

        verifyTraceContextContents(childContext.getOutgoingTraceParentTextHeader().toString(),
            childContext.getTraceId().toString(), childContext.getId().toString(), "00", "01");
        verifyTraceContextContents(childContext.getOutgoingTraceParentBinaryHeader(),
            childContext.getTraceId().toString(), childContext.getId().toString(), (byte) 0x00, (byte) 0x01);
    }

    @Test
    void testUnknownVersion() {
        String testTextHeader = "42-0af7651916cd43dd8448eb211c80319c-b9c7c989f97918e1-01";
        assertValid(testTextHeader);
        assertValid(convertToBinary(testTextHeader));
    }

    @Test
    void testUnknownExtraStuff() {
        String testTextHeader = "42-0af7651916cd43dd8448eb211c80319c-b9c7c989f97918e1-01-unknown-extra-stuff";
        assertValid(testTextHeader);

        byte[] header = convertToBinary(testTextHeader);
        byte[] withExtra = new byte[40];
        for (int i = header.length; i < withExtra.length; i++) {
            new Random().nextBytes(withExtra);
        }
        System.arraycopy(header, 0, withExtra, 0, header.length);
        assertValid(withExtra);
    }

    // If a traceparent header is invalid, ignore it and create a new root context

    @Test
    void testInvalidHeader_traceIdAllZeroes() {
        String testTextHeader = "00-00000000000000000000000000000000-b9c7c989f97918e1-00";
        assertInvalid(testTextHeader);
        assertInvalid(convertToBinary(testTextHeader));
    }

    @Test
    void testInvalidHeader_spanIdAllZeroes() {
        String testTextHeader = "00-0af7651916cd43dd8448eb211c80319c-0000000000000000-00";
        assertInvalid(testTextHeader);
        assertInvalid(convertToBinary(testTextHeader));
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

    private void assertInvalid(byte[] binaryHeader) {
        final TraceContext traceContext = TraceContext.with64BitId(mock(ElasticApmTracer.class));
        assertThat(traceContext.asChildOf(binaryHeader)).isFalse();
    }

    private void assertValid(String s) {
        final TraceContext traceContext = TraceContext.with64BitId(mock(ElasticApmTracer.class));
        assertThat(traceContext.asChildOf(s)).isTrue();
        verifyTraceContextContents(traceContext.getOutgoingTraceParentTextHeader().toString(),
            traceContext.getTraceId().toString(), traceContext.getId().toString(), "00", s.substring(53, 55));
    }

    private void assertValid(byte[] binaryHeader) {
        final TraceContext traceContext = TraceContext.with64BitId(mock(ElasticApmTracer.class));
        assertThat(traceContext.asChildOf(binaryHeader)).isTrue();
        verifyTraceContextContents(traceContext.getOutgoingTraceParentBinaryHeader(),
            traceContext.getTraceId().toString(), traceContext.getId().toString(), (byte) 0x00, binaryHeader[28]);
    }

    private byte[] convertToBinary(String textHeader) {
        final TraceContext traceContext = TraceContext.with64BitId(mock(ElasticApmTracer.class));
        traceContext.asChildOf(textHeader);
        byte[] binaryHeader = traceContext.getOutgoingTraceParentBinaryHeader();
        // replace the version and parent ID
        HexUtils.decode(textHeader, 0, 2, binaryHeader, 0);
        HexUtils.decode(textHeader, 36, 16, binaryHeader, 19);
        return binaryHeader;
    }
}
