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

import co.elastic.apm.agent.impl.BinaryHeaderMapAccessor;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.TextHeaderMapAccessor;
import co.elastic.apm.agent.impl.sampling.ConstantSampler;
import co.elastic.apm.agent.util.HexUtils;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class TraceContextTest {

    /**
     * Test flow:
     * 1.  create a parent context from a fixed string
     * 2.  create a child based on the string header - test {@link TraceContext#asChildOf(String)}
     * 3.  create a grandchild based on binary header - test {@link TraceContext#setOutgoingTraceContextHeaders(Object, BinaryHeaderSetter)}
     * and {@link TraceContext#asChildOf(byte[])}
     * 4.  create a second grandchild based on text header - test both {@link TraceContext#getOutgoingTraceParentTextHeader()}
     * and {@link TraceContext#asChildOf(String)}
     *
     * @param flagsValue tested flags
     * @param isSampled  whether to test context propagation of sampled trace or not
     */
    private void mixTextAndBinaryParsingAndContextPropagation(String flagsValue, boolean isSampled) {
        Map<String, String> textHeaderMap = Map.of(TraceContext.TRACE_PARENT_TEXTUAL_HEADER_NAME, "00-0af7651916cd43dd8448eb211c80319c-b9c7c989f97918e1-" + flagsValue);
        final TraceContext child = TraceContext.with64BitId(mock(ElasticApmTracer.class));
        assertThat(TraceContext.<Map<String, String>>getFromTraceContextTextHeaders().asChildOf(child, textHeaderMap, TextHeaderMapAccessor.INSTANCE)).isTrue();
        assertThat(child.getTraceContext().getTraceId().toString()).isEqualTo("0af7651916cd43dd8448eb211c80319c");
        assertThat(child.getTraceContext().getParentId().toString()).isEqualTo("b9c7c989f97918e1");
        assertThat(child.getTraceContext().getId()).isNotEqualTo(child.getTraceContext().getParentId());
        assertThat(child.isSampled()).isEqualTo(isSampled);

        // create a grandchild to ensure proper regenerated trace context
        final TraceContext grandchild1 = TraceContext.with64BitId(mock(ElasticApmTracer.class));
        final Map<String, byte[]> binaryHeaderMap = new HashMap<>();
        assertThat(child.setOutgoingTraceContextHeaders(binaryHeaderMap, BinaryHeaderMapAccessor.INSTANCE)).isTrue();
        assertThat(TraceContext.<Map<String, byte[]>>getFromTraceContextBinaryHeaders().asChildOf(grandchild1, binaryHeaderMap, BinaryHeaderMapAccessor.INSTANCE)).isTrue();
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

    @Test
    void testBinaryHeaderSizeEnforcement() {
        final Map<String, String> headerMap = Map.of(TraceContext.TRACE_PARENT_TEXTUAL_HEADER_NAME, "00-0af7651916cd43dd8448eb211c80319c-b9c7c989f97918e1-01");
        final TraceContext child = TraceContext.with64BitId(mock(ElasticApmTracer.class));
        assertThat(TraceContext.<Map<String, String>>getFromTraceContextTextHeaders().asChildOf(child, headerMap, TextHeaderMapAccessor.INSTANCE)).isTrue();
        final byte[] outgoingBinaryHeader = new byte[TraceContext.BINARY_FORMAT_EXPECTED_LENGTH - 1];
        assertThat(child.setOutgoingTraceContextHeaders(new HashMap<>(), new BinaryHeaderSetter<Map<String, byte[]>>() {
            @Override
            public byte[] getFixedLengthByteArray(String headerName, int length) {
                return outgoingBinaryHeader;
            }

            @Override
            public void setHeader(String headerName, byte[] headerValue, Map<String, byte[]> headerMap) {
                // assert that the original byte array was not used due to its size limitation
                assertThat(headerValue).isNotEqualTo(outgoingBinaryHeader);
            }
        })).isTrue();
    }

    @Test
    void testBinaryHeaderCaching() {
        final Map<String, String> headerMap = Map.of(TraceContext.TRACE_PARENT_TEXTUAL_HEADER_NAME, "00-0af7651916cd43dd8448eb211c80319c-b9c7c989f97918e1-01");
        final TraceContext child = TraceContext.with64BitId(mock(ElasticApmTracer.class));
        assertThat(TraceContext.<Map<String, String>>getFromTraceContextTextHeaders().asChildOf(child, headerMap, TextHeaderMapAccessor.INSTANCE)).isTrue();
        HashMap<String, byte[]> binaryHeaderMap = new HashMap<>();
        assertThat(child.setOutgoingTraceContextHeaders(binaryHeaderMap, BinaryHeaderMapAccessor.INSTANCE)).isTrue();
        byte[] outgoingHeader = binaryHeaderMap.get(TraceContext.TRACE_PARENT_BINARY_HEADER_NAME);
        assertThat(child.setOutgoingTraceContextHeaders(binaryHeaderMap, BinaryHeaderMapAccessor.INSTANCE)).isTrue();
        assertThat(binaryHeaderMap.get(TraceContext.TRACE_PARENT_BINARY_HEADER_NAME)).isSameAs(outgoingHeader);
    }

    @Test
    void testBinaryHeader_CachingDisabled() {
        final Map<String, String> headerMap = Map.of(TraceContext.TRACE_PARENT_TEXTUAL_HEADER_NAME, "00-0af7651916cd43dd8448eb211c80319c-b9c7c989f97918e1-01");
        final TraceContext child = TraceContext.with64BitId(mock(ElasticApmTracer.class));
        assertThat(TraceContext.<Map<String, String>>getFromTraceContextTextHeaders().asChildOf(child, headerMap, TextHeaderMapAccessor.INSTANCE)).isTrue();
        BinaryHeaderSetter<Map<String, byte[]>> headerSetter = new BinaryHeaderSetter<>() {
            @Override
            public byte[] getFixedLengthByteArray(String headerName, int length) {
                return null;
            }

            @Override
            public void setHeader(String headerName, byte[] headerValue, Map<String, byte[]> headerMap) {
                headerMap.put(headerName, headerValue);
            }
        };
        HashMap<String, byte[]> binaryHeaderMap = new HashMap<>();
        assertThat(child.setOutgoingTraceContextHeaders(binaryHeaderMap, headerSetter)).isTrue();
        byte[] outgoingHeader = binaryHeaderMap.get(TraceContext.TRACE_PARENT_BINARY_HEADER_NAME);
        assertThat(child.setOutgoingTraceContextHeaders(binaryHeaderMap, headerSetter)).isTrue();
        assertThat(binaryHeaderMap.get(TraceContext.TRACE_PARENT_BINARY_HEADER_NAME)).isNotSameAs(outgoingHeader);
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
        Map<String, byte[]> headerMap = new HashMap<>();
        assertThat(traceContext.setOutgoingTraceContextHeaders(headerMap, BinaryHeaderMapAccessor.INSTANCE)).isTrue();
        verifyTraceContextContents(headerMap.get(TraceContext.TRACE_PARENT_BINARY_HEADER_NAME),
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
        Map<String, byte[]> headerMap = new HashMap<>();
        assertThat(traceContext.setOutgoingTraceContextHeaders(headerMap, BinaryHeaderMapAccessor.INSTANCE)).isTrue();
        verifyTraceContextContents(headerMap.get(TraceContext.TRACE_PARENT_BINARY_HEADER_NAME), traceContext.getTraceId().toString(),
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
        Map<String, byte[]> headerMap = new HashMap<>();
        assertThat(traceContext.setOutgoingTraceContextHeaders(headerMap, BinaryHeaderMapAccessor.INSTANCE)).isTrue();
        byte[] outgoingByteHeader = headerMap.get(TraceContext.TRACE_PARENT_BINARY_HEADER_NAME);
        byte[] tmp = new byte[outgoingByteHeader.length];
        System.arraycopy(outgoingByteHeader, 0, tmp, 0, outgoingByteHeader.length);
        traceContext.asChildOf("00-0af7651916cd43dd8448eb211c80319c-b9c7c989f97918e1-00");
        assertThat(traceContext.setOutgoingTraceContextHeaders(headerMap, BinaryHeaderMapAccessor.INSTANCE)).isTrue();
        // relies on the byte array caching in BinaryHeaderMapAccessor
        assertThat(outgoingByteHeader).isNotEqualTo(tmp);
        traceContext.resetState();
        assertThat(traceContext.setOutgoingTraceContextHeaders(headerMap, BinaryHeaderMapAccessor.INSTANCE)).isTrue();
        assertThat(outgoingByteHeader).isEqualTo(tmp);
    }

    @Test
    void testCopyFrom() {
        final TraceContext first = TraceContext.with64BitId(mock(ElasticApmTracer.class));
        first.asChildOf("00-0af7651916cd43dd8448eb211c80319c-b9c7c989f97918e1-01");

        final TraceContext second = TraceContext.with64BitId(mock(ElasticApmTracer.class));
        second.asChildOf("00-8448ebb9c7c989f97918e11916cd43dd-211c80319c0af765-00");

        assertThat(first.getTraceId()).isNotEqualTo(second.getTraceId());
        assertThat(first.getParentId()).isNotEqualTo(second.getParentId());
        assertThat(first.isSampled()).isNotEqualTo(second.isSampled());
        assertThat(first.getOutgoingTraceParentTextHeader()).isNotEqualTo(second.getOutgoingTraceParentTextHeader());
        Map<String, byte[]> binaryHeaderMap = new HashMap<>();
        first.setOutgoingTraceContextHeaders(binaryHeaderMap, BinaryHeaderMapAccessor.INSTANCE);
        byte[] outgoingHeader = binaryHeaderMap.get(TraceContext.TRACE_PARENT_BINARY_HEADER_NAME);
        // We must copy because of the byte array caching in BinaryHeaderMapAccessor
        byte[] firstOutgoingHeader = new byte[outgoingHeader.length];
        System.arraycopy(outgoingHeader, 0, firstOutgoingHeader, 0, outgoingHeader.length);
        second.setOutgoingTraceContextHeaders(binaryHeaderMap, BinaryHeaderMapAccessor.INSTANCE);
        assertThat(binaryHeaderMap.get(TraceContext.TRACE_PARENT_BINARY_HEADER_NAME)).isNotEqualTo(firstOutgoingHeader);

        second.copyFrom(first);
        assertThat(first.getTraceId()).isEqualTo(second.getTraceId());
        assertThat(first.getParentId()).isEqualTo(second.getParentId());
        assertThat(first.isSampled()).isEqualTo(second.isSampled());
        assertThat(first.getOutgoingTraceParentTextHeader().toString()).isEqualTo(second.getOutgoingTraceParentTextHeader().toString());
        second.setOutgoingTraceContextHeaders(binaryHeaderMap, BinaryHeaderMapAccessor.INSTANCE);
        assertThat(binaryHeaderMap.get(TraceContext.TRACE_PARENT_BINARY_HEADER_NAME)).isEqualTo(firstOutgoingHeader);
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

        verifyTraceContextContents(childContext.getOutgoingTraceParentTextHeader().toString(),
            childContext.getTraceId().toString(), rootContext.getId().toString(), "00", "00");
        Map<String, byte[]> binaryHeaderMap = new HashMap<>();
        childContext.setOutgoingTraceContextHeaders(binaryHeaderMap, BinaryHeaderMapAccessor.INSTANCE);
        verifyTraceContextContents(binaryHeaderMap.get(TraceContext.TRACE_PARENT_BINARY_HEADER_NAME),
            childContext.getTraceId().toString(), rootContext.getId().toString(), (byte) 0x00, (byte) 0x00);
    }

    @Test
    void testPropagateSpanIdForSampledSpan() {
        final TraceContext rootContext = TraceContext.with64BitId(mock(ElasticApmTracer.class));
        rootContext.asRootSpan(ConstantSampler.of(true));

        final TraceContext childContext = TraceContext.with64BitId(mock(ElasticApmTracer.class));
        childContext.asChildOf(rootContext);

        verifyTraceContextContents(childContext.getOutgoingTraceParentTextHeader().toString(),
            childContext.getTraceId().toString(), childContext.getId().toString(), "00", "01");
        Map<String, byte[]> binaryHeaderMap = new HashMap<>();
        childContext.setOutgoingTraceContextHeaders(binaryHeaderMap, BinaryHeaderMapAccessor.INSTANCE);
        verifyTraceContextContents(binaryHeaderMap.get(TraceContext.TRACE_PARENT_BINARY_HEADER_NAME),
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
        Map<String, byte[]> binaryHeaderMap = new HashMap<>();
        traceContext.setOutgoingTraceContextHeaders(binaryHeaderMap, BinaryHeaderMapAccessor.INSTANCE);
        verifyTraceContextContents(binaryHeaderMap.get(TraceContext.TRACE_PARENT_BINARY_HEADER_NAME),
            traceContext.getTraceId().toString(), traceContext.getId().toString(), (byte) 0x00, binaryHeader[28]);
    }

    private byte[] convertToBinary(String textHeader) {
        final TraceContext traceContext = TraceContext.with64BitId(mock(ElasticApmTracer.class));
        traceContext.asChildOf(textHeader);
        Map<String, byte[]> binaryHeaderMap = new HashMap<>();
        traceContext.setOutgoingTraceContextHeaders(binaryHeaderMap, BinaryHeaderMapAccessor.INSTANCE);
        byte[] binaryHeader = binaryHeaderMap.get(TraceContext.TRACE_PARENT_BINARY_HEADER_NAME);
        // replace the version and parent ID
        HexUtils.decode(textHeader, 0, 2, binaryHeader, 0);
        HexUtils.decode(textHeader, 36, 16, binaryHeader, 19);
        return binaryHeader;
    }
}
