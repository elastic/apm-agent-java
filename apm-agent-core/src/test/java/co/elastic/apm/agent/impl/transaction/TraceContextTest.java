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
package co.elastic.apm.agent.impl.transaction;

import co.elastic.apm.agent.MockReporter;
import co.elastic.apm.agent.MockTracer;
import co.elastic.apm.agent.configuration.CoreConfiguration;
import co.elastic.apm.agent.configuration.SpyConfiguration;
import co.elastic.apm.agent.impl.BinaryHeaderMapAccessor;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.ElasticApmTracerBuilder;
import co.elastic.apm.agent.impl.TextHeaderMapAccessor;
import co.elastic.apm.agent.impl.sampling.ConstantSampler;
import co.elastic.apm.agent.impl.sampling.Sampler;
import co.elastic.apm.agent.objectpool.TestObjectPoolFactory;
import co.elastic.apm.agent.util.HexUtils;
import co.elastic.apm.agent.util.PotentiallyMultiValuedMap;
import co.elastic.apm.agent.tracer.dispatch.BinaryHeaderSetter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.stagemonitor.configuration.ConfigurationRegistry;

import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

class TraceContextTest {

    private ElasticApmTracer tracer;
    private ConfigurationRegistry config;

    @BeforeEach
    public void setup() {
        config = SpyConfiguration.createSpyConfig();
        tracer = new ElasticApmTracerBuilder()
            .configurationRegistry(config)
            .reporter(new MockReporter())
            .withObjectPoolFactory(new TestObjectPoolFactory())
            .buildAndStart();
    }

    /**
     * Test flow:
     * 1.  create a parent context from a fixed string
     * 2.  create a child based on the string header - test {@link TraceContext#asChildOf(String)}
     * 3.  create a grandchild based on binary header - test {@link TraceContext#propagateTraceContext(Object, BinaryHeaderSetter)}
     * and {@link TraceContext#asChildOf(byte[])}
     * 4.  create a second grandchild based on text header - test both {@link TraceContext#getOutgoingTraceParentTextHeader()}
     * and {@link TraceContext#asChildOf(String)}
     *
     * @param flagsValue tested flags
     * @param isSampled  whether to test context propagation of sampled trace or not
     */
    private void mixTextAndBinaryParsingAndContextPropagation(String flagsValue, boolean isSampled) {
        Map<String, String> textHeaderMap = Map.of(TraceContext.W3C_TRACE_PARENT_TEXTUAL_HEADER_NAME, "00-0af7651916cd43dd8448eb211c80319c-b9c7c989f97918e1-" + flagsValue);
        final TraceContext child = TraceContext.with64BitId(tracer);
        assertThat(TraceContext.<Map<String, String>>getFromTraceContextTextHeaders().asChildOf(child, textHeaderMap, TextHeaderMapAccessor.INSTANCE)).isTrue();
        assertThat(child.getTraceId().toString()).isEqualTo("0af7651916cd43dd8448eb211c80319c");
        assertThat(child.getParentId().toString()).isEqualTo("b9c7c989f97918e1");
        assertThat(child.getId()).isNotEqualTo(child.getParentId());
        assertThat(child.isSampled()).isEqualTo(isSampled);

        // create a grandchild to ensure proper regenerated trace context
        final TraceContext grandchild1 = TraceContext.with64BitId(tracer);
        final Map<String, byte[]> binaryHeaderMap = new HashMap<>();
        assertThat(child.propagateTraceContext(binaryHeaderMap, BinaryHeaderMapAccessor.INSTANCE)).isTrue();
        assertThat(TraceContext.<Map<String, byte[]>>getFromTraceContextBinaryHeaders().asChildOf(grandchild1, binaryHeaderMap, BinaryHeaderMapAccessor.INSTANCE)).isTrue();
        assertThat(grandchild1.getTraceId().toString()).isEqualTo("0af7651916cd43dd8448eb211c80319c");
        assertThat(grandchild1.getParentId().toString()).isEqualTo(child.getId().toString());
        assertThat(grandchild1.getId()).isNotEqualTo(child.getId());
        assertThat(grandchild1.isSampled()).isEqualTo(isSampled);

        String childHeader = child.getOutgoingTraceParentTextHeader().toString();
        assertThat(childHeader).endsWith("-" + flagsValue);
        final TraceContext grandchild2 = TraceContext.with64BitId(tracer);
        assertThat(grandchild2.asChildOf(childHeader)).isTrue();
        assertThat(grandchild2.getTraceId().toString()).isEqualTo("0af7651916cd43dd8448eb211c80319c");
        assertThat(grandchild2.getParentId().toString()).isEqualTo(child.getId().toString());
        assertThat(grandchild2.getId()).isNotEqualTo(child.getId());
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
    void testChildOfElasticTraceparentHeader() {
        Map<String, String> textHeaderMap = Map.of(TraceContext.ELASTIC_TRACE_PARENT_TEXTUAL_HEADER_NAME, "00-0af7651916cd43dd8448eb211c80319c-b9c7c989f97918e1-01");
        final TraceContext child = TraceContext.with64BitId(tracer);
        assertThat(TraceContext.<Map<String, String>>getFromTraceContextTextHeaders().asChildOf(child, textHeaderMap, TextHeaderMapAccessor.INSTANCE)).isTrue();
        assertThat(child.getTraceId().toString()).isEqualTo("0af7651916cd43dd8448eb211c80319c");
        assertThat(child.getParentId().toString()).isEqualTo("b9c7c989f97918e1");
        assertThat(child.getId()).isNotEqualTo(child.getParentId());
        assertThat(child.isSampled()).isTrue();
    }

    @Test
    void testW3CTraceparentHeaderPrecedence() {
        Map<String, String> textHeaderMap = Map.of(
            TraceContext.W3C_TRACE_PARENT_TEXTUAL_HEADER_NAME, "00-0af7651916cd43dd8448eb211c80319c-b9c7c989f97918e1-00",
            TraceContext.ELASTIC_TRACE_PARENT_TEXTUAL_HEADER_NAME, "00-dd8448eb211c80319c0af7651916cd43-f97918e1b9c7c989-01"
        );
        final TraceContext child = TraceContext.with64BitId(tracer);
        assertThat(TraceContext.<Map<String, String>>getFromTraceContextTextHeaders().asChildOf(child, textHeaderMap, TextHeaderMapAccessor.INSTANCE)).isTrue();
        assertThat(child.getTraceId().toString()).isEqualTo("0af7651916cd43dd8448eb211c80319c");
        assertThat(child.getParentId().toString()).isEqualTo("b9c7c989f97918e1");
        assertThat(child.getId()).isNotEqualTo(child.getParentId());
        assertThat(child.isSampled()).isFalse();
    }

    @Test
    void testInvalidElasticTraceparentHeader() {
        Map<String, String> textHeaderMap = Map.of(
            TraceContext.W3C_TRACE_PARENT_TEXTUAL_HEADER_NAME, "00-0af7651916cd43dd8448eb211c80319c-b9c7c989f97918e1-01",
            // one char too short trace ID
            TraceContext.ELASTIC_TRACE_PARENT_TEXTUAL_HEADER_NAME, "00-d8448eb211c80319c0af7651916cd43-f97918e1b9c7c989-00"
        );
        final TraceContext child = TraceContext.with64BitId(tracer);
        assertThat(TraceContext.<Map<String, String>>getFromTraceContextTextHeaders().asChildOf(child, textHeaderMap, TextHeaderMapAccessor.INSTANCE)).isTrue();
        // we should fallback to try the W3C header
        assertThat(child.getTraceId().toString()).isEqualTo("0af7651916cd43dd8448eb211c80319c");
        assertThat(child.getParentId().toString()).isEqualTo("b9c7c989f97918e1");
        assertThat(child.getId()).isNotEqualTo(child.getParentId());
        assertThat(child.isSampled()).isTrue();
    }

    @Test
    void testElasticTraceparentHeaderDisabled() {
        Map<String, String> textHeaderMap = Map.of(TraceContext.W3C_TRACE_PARENT_TEXTUAL_HEADER_NAME, "00-0af7651916cd43dd8448eb211c80319c-b9c7c989f97918e1-01");
        final TraceContext child = TraceContext.with64BitId(tracer);
        assertThat(TraceContext.<Map<String, String>>getFromTraceContextTextHeaders().asChildOf(child, textHeaderMap, TextHeaderMapAccessor.INSTANCE)).isTrue();
        Map<String, String> outgoingHeaders = new HashMap<>();
        doReturn(false).when(config.getConfig(CoreConfiguration.class)).isElasticTraceparentHeaderEnabled();
        child.propagateTraceContext(outgoingHeaders, TextHeaderMapAccessor.INSTANCE);
        assertThat(outgoingHeaders.get(TraceContext.W3C_TRACE_PARENT_TEXTUAL_HEADER_NAME)).isNotNull();
        assertThat(outgoingHeaders.get(TraceContext.ELASTIC_TRACE_PARENT_TEXTUAL_HEADER_NAME)).isNull();
    }

    @Test
    void testTraceContextTextHeadersRemoval() {
        Map<String, String> headerMap = new HashMap<>();
        headerMap.put(TraceContext.ELASTIC_TRACE_PARENT_TEXTUAL_HEADER_NAME, "00-0af7651916cd43dd8448eb211c80319c-b9c7c989f97918e1-01");
        headerMap.put(TraceContext.W3C_TRACE_PARENT_TEXTUAL_HEADER_NAME, "00-0af7651916cd43dd8448eb211c80319c-b9c7c989f97918e1-01");
        headerMap.put(TraceContext.TRACESTATE_HEADER_NAME, "foo=bar,baz=qux");
        TraceContext.removeTraceContextHeaders(headerMap, TextHeaderMapAccessor.INSTANCE);
        assertThat(headerMap.get(TraceContext.W3C_TRACE_PARENT_TEXTUAL_HEADER_NAME)).isNull();
        assertThat(headerMap.get(TraceContext.ELASTIC_TRACE_PARENT_TEXTUAL_HEADER_NAME)).isNull();
        assertThat(headerMap.get(TraceContext.TRACESTATE_HEADER_NAME)).isNull();
    }

    @Test
    void testTraceContextHeadersCopy() {
        Map<String, String> original = new HashMap<>();
        original.put(TraceContext.ELASTIC_TRACE_PARENT_TEXTUAL_HEADER_NAME, "00-0af7651916cd43dd8448eb211c80319c-b9c7c989f97918e1-01");
        original.put(TraceContext.W3C_TRACE_PARENT_TEXTUAL_HEADER_NAME, "00-0af7651916cd43dd8448eb211c80319c-b9c7c989f97918e1-01");
        original.put(TraceContext.TRACESTATE_HEADER_NAME, "foo=bar,baz=qux");
        Map<String, String> copy = new HashMap<>();
        TraceContext.copyTraceContextTextHeaders(original, TextHeaderMapAccessor.INSTANCE, copy, TextHeaderMapAccessor.INSTANCE);
        assertThat(copy.get(TraceContext.W3C_TRACE_PARENT_TEXTUAL_HEADER_NAME)).isNotNull();
        assertThat(copy.get(TraceContext.ELASTIC_TRACE_PARENT_TEXTUAL_HEADER_NAME)).isNotNull();
        assertThat(copy.get(TraceContext.TRACESTATE_HEADER_NAME)).isNotNull();
    }

    @Test
    void testTracestateHeader() {
        PotentiallyMultiValuedMap incomingHeaders = new PotentiallyMultiValuedMap();
        incomingHeaders.add("traceparent", "00-0af7651916cd43dd8448eb211c80319c-b9c7c989f97918e1-01");
        incomingHeaders.add("tracestate", "foo=bar");
        incomingHeaders.add("tracestate", "baz=qux,quux=quuz");
        final TraceContext child = TraceContext.with64BitId(tracer);
        assertThat(TraceContext.<PotentiallyMultiValuedMap>getFromTraceContextTextHeaders().asChildOf(child, incomingHeaders, MultiValueMapAccessor.INSTANCE)).isTrue();
        assertThat(child.getTraceId().toString()).isEqualTo("0af7651916cd43dd8448eb211c80319c");
        assertThat(child.getParentId().toString()).isEqualTo("b9c7c989f97918e1");
        assertThat(child.getId()).isNotEqualTo(child.getParentId());
        assertThat(child.isSampled()).isTrue();
        PotentiallyMultiValuedMap outgoingHeaders = new PotentiallyMultiValuedMap();
        child.propagateTraceContext(outgoingHeaders, MultiValueMapAccessor.INSTANCE);
        assertThat(outgoingHeaders.size()).isEqualTo(3);
        assertThat(outgoingHeaders.getFirst(TraceContext.W3C_TRACE_PARENT_TEXTUAL_HEADER_NAME)).isNotNull();
        assertThat(outgoingHeaders.getAll(TraceContext.TRACESTATE_HEADER_NAME)).hasSize(1);
        assertThat(outgoingHeaders.getFirst(TraceContext.TRACESTATE_HEADER_NAME)).isEqualTo("foo=bar,baz=qux,quux=quuz");
    }

    @Test
    void testTracestateHeaderSizeLimit() {
        doReturn(20).when(config.getConfig(CoreConfiguration.class)).getTracestateSizeLimit();
        PotentiallyMultiValuedMap incomingHeaders = new PotentiallyMultiValuedMap();
        incomingHeaders.add("traceparent", "00-0af7651916cd43dd8448eb211c80319c-b9c7c989f97918e1-01");
        incomingHeaders.add("tracestate", "foo=bar");
        incomingHeaders.add("tracestate", "baz=qux,quux=quuz");
        final TraceContext child = TraceContext.with64BitId(tracer);
        assertThat(TraceContext.<PotentiallyMultiValuedMap>getFromTraceContextTextHeaders().asChildOf(child, incomingHeaders, MultiValueMapAccessor.INSTANCE)).isTrue();
        PotentiallyMultiValuedMap outgoingHeaders = new PotentiallyMultiValuedMap();
        child.propagateTraceContext(outgoingHeaders, MultiValueMapAccessor.INSTANCE);
        assertThat(outgoingHeaders.size()).isEqualTo(3);
        assertThat(outgoingHeaders.getFirst(TraceContext.W3C_TRACE_PARENT_TEXTUAL_HEADER_NAME)).isNotNull();
        assertThat(outgoingHeaders.getAll(TraceContext.TRACESTATE_HEADER_NAME)).hasSize(1);
        assertThat(outgoingHeaders.getFirst(TraceContext.TRACESTATE_HEADER_NAME)).isEqualTo("foo=bar,baz=qux");
    }

    @Test
    void testNoTracestateWhenInvalidTraceparentHeader() {
        Map<String, String> textHeaderMap = Map.of(
            // one char too short trace ID
            TraceContext.ELASTIC_TRACE_PARENT_TEXTUAL_HEADER_NAME, "00-d8448eb211c80319c0af7651916cd43-f97918e1b9c7c989-00",
            TraceContext.TRACESTATE_HEADER_NAME, "foo=bar,baz=qux"
        );
        final TraceContext child = TraceContext.with64BitId(tracer);
        assertThat(TraceContext.<Map<String, String>>getFromTraceContextTextHeaders().asChildOf(child, textHeaderMap, TextHeaderMapAccessor.INSTANCE)).isFalse();

        assertThat(child.isRecorded()).isFalse();

        Map<String, String> outgoingHeaders = new HashMap<>();
        child.propagateTraceContext(outgoingHeaders, TextHeaderMapAccessor.INSTANCE);
        assertThat(outgoingHeaders.get(TraceContext.TRACESTATE_HEADER_NAME)).isNull();
    }

    @Test
    void testBinaryHeaderSizeEnforcement() {
        final Map<String, String> headerMap = Map.of(TraceContext.W3C_TRACE_PARENT_TEXTUAL_HEADER_NAME, "00-0af7651916cd43dd8448eb211c80319c-b9c7c989f97918e1-01");
        final TraceContext child = TraceContext.with64BitId(tracer);
        assertThat(TraceContext.<Map<String, String>>getFromTraceContextTextHeaders().asChildOf(child, headerMap, TextHeaderMapAccessor.INSTANCE)).isTrue();
        final byte[] outgoingBinaryHeader = new byte[TraceContext.BINARY_FORMAT_EXPECTED_LENGTH - 1];
        assertThat(child.propagateTraceContext(new HashMap<>(), new BinaryHeaderSetter<Map<String, byte[]>>() {
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
        final Map<String, String> headerMap = Map.of(TraceContext.W3C_TRACE_PARENT_TEXTUAL_HEADER_NAME, "00-0af7651916cd43dd8448eb211c80319c-b9c7c989f97918e1-01");
        final TraceContext child = TraceContext.with64BitId(tracer);
        assertThat(TraceContext.<Map<String, String>>getFromTraceContextTextHeaders().asChildOf(child, headerMap, TextHeaderMapAccessor.INSTANCE)).isTrue();
        HashMap<String, byte[]> binaryHeaderMap = new HashMap<>();
        assertThat(child.propagateTraceContext(binaryHeaderMap, BinaryHeaderMapAccessor.INSTANCE)).isTrue();
        byte[] outgoingHeader = binaryHeaderMap.get(TraceContext.ELASTIC_TRACE_PARENT_TEXTUAL_HEADER_NAME);
        assertThat(child.propagateTraceContext(binaryHeaderMap, BinaryHeaderMapAccessor.INSTANCE)).isTrue();
        assertThat(binaryHeaderMap.get(TraceContext.ELASTIC_TRACE_PARENT_TEXTUAL_HEADER_NAME)).isSameAs(outgoingHeader);
    }

    @Test
    void testBinaryHeader_CachingDisabled() {
        final Map<String, String> headerMap = Map.of(TraceContext.W3C_TRACE_PARENT_TEXTUAL_HEADER_NAME, "00-0af7651916cd43dd8448eb211c80319c-b9c7c989f97918e1-01");
        final TraceContext child = TraceContext.with64BitId(tracer);
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
        assertThat(child.propagateTraceContext(binaryHeaderMap, headerSetter)).isTrue();
        byte[] outgoingHeader = binaryHeaderMap.get(TraceContext.ELASTIC_TRACE_PARENT_TEXTUAL_HEADER_NAME);
        assertThat(child.propagateTraceContext(binaryHeaderMap, headerSetter)).isTrue();
        assertThat(binaryHeaderMap.get(TraceContext.ELASTIC_TRACE_PARENT_TEXTUAL_HEADER_NAME)).isNotSameAs(outgoingHeader);
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
        final TraceContext traceContext = TraceContext.with64BitId(tracer);
        final String header = "00-0af7651916cd43dd8448eb211c80319c-b9c7c989f97918e1-03";
        assertThat(traceContext.asChildOf(header)).isTrue();
        String parentId = traceContext.getId().toString();
        verifyTraceContextContents(traceContext.getOutgoingTraceParentTextHeader().toString(),
            "0af7651916cd43dd8448eb211c80319c", parentId, "00", "03");
        Map<String, byte[]> headerMap = new HashMap<>();
        assertThat(traceContext.propagateTraceContext(headerMap, BinaryHeaderMapAccessor.INSTANCE)).isTrue();
        verifyTraceContextContents(headerMap.get(TraceContext.ELASTIC_TRACE_PARENT_TEXTUAL_HEADER_NAME),
            "0af7651916cd43dd8448eb211c80319c", parentId, (byte) 0x00, (byte) 0x03);
    }

    @Test
    void outgoingHeaderRootSpan() {
        final TraceContext traceContext = TraceContext.with64BitId(tracer);
        traceContext.asRootSpan(ConstantSampler.of(true));
        assertThat(traceContext.isSampled()).isTrue();
        String outgoingStringHeader = traceContext.getOutgoingTraceParentTextHeader().toString();
        assertThat(outgoingStringHeader).hasSize(55);
        verifyTraceContextContents(outgoingStringHeader, traceContext.getTraceId().toString(),
            traceContext.getId().toString(), "00", "01");
        Map<String, byte[]> headerMap = new HashMap<>();
        assertThat(traceContext.propagateTraceContext(headerMap, BinaryHeaderMapAccessor.INSTANCE)).isTrue();
        verifyTraceContextContents(headerMap.get(TraceContext.ELASTIC_TRACE_PARENT_TEXTUAL_HEADER_NAME), traceContext.getTraceId().toString(),
            traceContext.getId().toString(), (byte) 0x00, (byte) 0x01);
    }

    @Test
    void parseFromTraceParentHeader_notSampled() {
        final TraceContext traceContext = TraceContext.with64BitId(tracer);
        final String header = "00-0af7651916cd43dd8448eb211c80319c-b9c7c989f97918e1-00";
        assertThat(traceContext.asChildOf(header)).isTrue();
        assertThat(traceContext.isSampled()).isFalse();
        assertThat(traceContext.getIncomingTraceParentHeader()).isEqualTo(header);
    }

    @Test
    void testResetState() {
        final TraceContext traceContext = TraceContext.with64BitId(tracer);
        traceContext.asChildOf("00-0af7651916cd43dd8448eb211c80319c-b9c7c989f97918e1-00");
        traceContext.resetState();
        assertThat(traceContext.getIncomingTraceParentHeader()).isEqualTo("00-00000000000000000000000000000000-0000000000000000-00");
    }

    @Test
    void testResetOutgoingTextHeader() {
        final TraceContext traceContext = TraceContext.with64BitId(tracer);
        String traceParentHeader = traceContext.getOutgoingTraceParentTextHeader().toString();
        traceContext.asChildOf("00-0af7651916cd43dd8448eb211c80319c-b9c7c989f97918e1-00");
        assertThat(traceContext.getOutgoingTraceParentTextHeader().toString()).isNotEqualTo(traceParentHeader);
    }

    @Test
    void testResetOutgoingBinaryHeader() {
        final TraceContext traceContext = TraceContext.with64BitId(tracer);
        Map<String, byte[]> headerMap = new HashMap<>();
        assertThat(traceContext.propagateTraceContext(headerMap, BinaryHeaderMapAccessor.INSTANCE)).isTrue();
        byte[] outgoingByteHeader = headerMap.get(TraceContext.ELASTIC_TRACE_PARENT_TEXTUAL_HEADER_NAME);
        byte[] tmp = new byte[outgoingByteHeader.length];
        System.arraycopy(outgoingByteHeader, 0, tmp, 0, outgoingByteHeader.length);
        traceContext.asChildOf("00-0af7651916cd43dd8448eb211c80319c-b9c7c989f97918e1-00");
        assertThat(traceContext.propagateTraceContext(headerMap, BinaryHeaderMapAccessor.INSTANCE)).isTrue();
        // relies on the byte array caching in BinaryHeaderMapAccessor
        assertThat(outgoingByteHeader).isNotEqualTo(tmp);
        traceContext.resetState();
        assertThat(traceContext.propagateTraceContext(headerMap, BinaryHeaderMapAccessor.INSTANCE)).isTrue();
        assertThat(outgoingByteHeader).isEqualTo(tmp);
    }

    @Test
    void testCopyFrom() {
        Map<String, String> textHeaderMap = Map.of(
            TraceContext.ELASTIC_TRACE_PARENT_TEXTUAL_HEADER_NAME, "00-0af7651916cd43dd8448eb211c80319c-b9c7c989f97918e1-01",
            TraceContext.TRACESTATE_HEADER_NAME, "foo=bar,baz=qux"
        );
        final TraceContext first = TraceContext.with64BitId(tracer);
        assertThat(TraceContext.<Map<String, String>>getFromTraceContextTextHeaders().asChildOf(first, textHeaderMap, TextHeaderMapAccessor.INSTANCE)).isTrue();

        textHeaderMap = Map.of(TraceContext.ELASTIC_TRACE_PARENT_TEXTUAL_HEADER_NAME, "00-8448ebb9c7c989f97918e11916cd43dd-211c80319c0af765-00");
        final TraceContext second = TraceContext.with64BitId(tracer);
        assertThat(TraceContext.<Map<String, String>>getFromTraceContextTextHeaders().asChildOf(second, textHeaderMap, TextHeaderMapAccessor.INSTANCE)).isTrue();

        assertThat(first.getTraceId()).isNotEqualTo(second.getTraceId());
        assertThat(first.getParentId()).isNotEqualTo(second.getParentId());
        assertThat(first.isSampled()).isNotEqualTo(second.isSampled());
        assertThat(first.getOutgoingTraceParentTextHeader()).isNotEqualTo(second.getOutgoingTraceParentTextHeader());
        Map<String, byte[]> binaryHeaderMap = new HashMap<>();
        first.propagateTraceContext(binaryHeaderMap, BinaryHeaderMapAccessor.INSTANCE);
        byte[] outgoingHeader = binaryHeaderMap.get(TraceContext.ELASTIC_TRACE_PARENT_TEXTUAL_HEADER_NAME);
        // We must copy because of the byte array caching in BinaryHeaderMapAccessor
        byte[] firstOutgoingHeader = new byte[outgoingHeader.length];
        System.arraycopy(outgoingHeader, 0, firstOutgoingHeader, 0, outgoingHeader.length);
        second.propagateTraceContext(binaryHeaderMap, BinaryHeaderMapAccessor.INSTANCE);
        assertThat(binaryHeaderMap.get(TraceContext.ELASTIC_TRACE_PARENT_TEXTUAL_HEADER_NAME)).isNotEqualTo(firstOutgoingHeader);

        second.copyFrom(first);
        assertThat(first.getTraceId()).isEqualTo(second.getTraceId());
        assertThat(first.getParentId()).isEqualTo(second.getParentId());
        assertThat(first.isSampled()).isEqualTo(second.isSampled());
        assertThat(first.getOutgoingTraceParentTextHeader().toString()).isEqualTo(second.getOutgoingTraceParentTextHeader().toString());
        second.propagateTraceContext(binaryHeaderMap, BinaryHeaderMapAccessor.INSTANCE);
        assertThat(binaryHeaderMap.get(TraceContext.ELASTIC_TRACE_PARENT_TEXTUAL_HEADER_NAME)).isEqualTo(firstOutgoingHeader);
    }

    @Test
    void testAsChildOfHeaders() {
        Map<String, String> textHeaderMap = Map.of(
            TraceContext.ELASTIC_TRACE_PARENT_TEXTUAL_HEADER_NAME, "00-0af7651916cd43dd8448eb211c80319c-b9c7c989f97918e1-01",
            TraceContext.TRACESTATE_HEADER_NAME, "foo=bar,baz=qux"
        );
        final TraceContext first = TraceContext.with64BitId(tracer);
        assertThat(TraceContext.<Map<String, String>>getFromTraceContextTextHeaders().asChildOf(first, textHeaderMap, TextHeaderMapAccessor.INSTANCE)).isTrue();

        final TraceContext second = TraceContext.with64BitId(tracer);
        second.asChildOf(first);

        HashMap<String, String> textHeaders = new HashMap<>();
        second.propagateTraceContext(textHeaders, TextHeaderMapAccessor.INSTANCE);
        assertThat(textHeaders.get(TraceContext.TRACESTATE_HEADER_NAME)).isEqualTo("foo=bar,baz=qux");
        assertThat(textHeaders.get(TraceContext.W3C_TRACE_PARENT_TEXTUAL_HEADER_NAME)).startsWith("00-0af7651916cd43dd8448eb211c80319c-");
    }

    @Test
    void testRandomValue() {
        final TraceContext traceContext = TraceContext.with64BitId(tracer);
        traceContext.asRootSpan(ConstantSampler.of(true));
        assertThat(traceContext.getTraceId().isEmpty()).isFalse();
        assertThat(traceContext.getParentId().isEmpty()).isTrue();
        assertThat(traceContext.getId().isEmpty()).isFalse();
    }

    @Test
    void testSetSampled() {
        final TraceContext traceContext = TraceContext.with64BitId(tracer);
        traceContext.asRootSpan(ConstantSampler.of(false));

        // not sampled means zero sample rate
        assertThat(traceContext.isSampled()).isFalse();
        assertThat(traceContext.getSampleRate()).isEqualTo(0d);

        // sampled without sample rate
        traceContext.setRecorded(true);

        assertThat(traceContext.isSampled()).isTrue();
        assertThat(traceContext.getSampleRate()).isNaN();

        // sampled with sample rate
        traceContext.getTraceState().set(0.5d, TraceState.getHeaderValue(0.5d));

        assertThat(traceContext.isSampled()).isTrue();
        assertThat(traceContext.getSampleRate()).isEqualTo(0.5d);

        // not sampled, sample rate should be unset
        traceContext.setRecorded(false);
        assertThat(traceContext.isSampled()).isFalse();
        assertThat(traceContext.getSampleRate()).isEqualTo(0.0d);
    }

    @Test
    void testRootSpanShouldAddsSampleRateToTraceState() {
        final TraceContext traceContext = createRootSpan(0.42d);
        String traceState = traceContext.getTraceState().toTextHeader();
        assertThat(traceState).isEqualTo("es=s:0.42");
    }

    private TraceContext createRootSpan(double sampleRate) {
        final TraceContext traceContext = TraceContext.with64BitId(tracer);

        Sampler sampler = mock(Sampler.class);
        doReturn(true).when(sampler).isSampled(any(Id.class));
        doReturn(sampleRate).when(sampler).getSampleRate();
        doReturn(TraceState.getHeaderValue(sampleRate)).when(sampler).getTraceStateHeader();

        traceContext.asRootSpan(sampler);
        return traceContext;
    }

    @Test
    void testTracedChildSpanWithoutTraceState() {
        Map<String, String> headers = Map.of(
            TraceContext.W3C_TRACE_PARENT_TEXTUAL_HEADER_NAME, "00-0af7651916cd43dd8448eb211c80319c-b9c7c989f97918e1-01"
        );
        TraceContext child = createChildSpanFromHeaders(headers);

        assertThat(child.isSampled()).isTrue();
        assertThat(child.getSampleRate()).isNaN();
    }

    @Test
    void testNonTracedChildSpanWithoutTraceState() {
        Map<String, String> headers = Map.of(
            TraceContext.W3C_TRACE_PARENT_TEXTUAL_HEADER_NAME, "00-0af7651916cd43dd8448eb211c80319c-b9c7c989f97918e1-00"
        );
        TraceContext child = createChildSpanFromHeaders(headers);

        assertThat(child.isSampled()).isFalse();
        assertThat(child.getSampleRate()).isEqualTo(0.0d);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        // invalid tracestate values: no sample rate and header fixed
        "|NaN|",
        "es=|NaN|",
        "es=s|NaN|",
        "es=s:|NaN|",
        "es=s:a|NaN|",
        // valid tracestate values with sample rate
        "es=s:1|1|es=s:1",
        "es=s:0.42|0.42|es=s:0.42",
        // other vendors entries
        "a=123,es=s:0.42|0.42|a=123,es=s:0.42",
    })
    void checkExpectedSampleRate(@Nullable String traceState, double expectedRate, @Nullable String expectedHeader) {
        Map<String, String> headers = new HashMap<>();
        headers.put(TraceContext.W3C_TRACE_PARENT_TEXTUAL_HEADER_NAME, "00-0af7651916cd43dd8448eb211c80319c-b9c7c989f97918e1-01");
        if (null != traceState) {
            headers.put(TraceContext.TRACESTATE_HEADER_NAME, traceState);
        }

        TraceContext child = createChildSpanFromHeaders(headers);

        assertThat(child.isSampled()).isTrue();

        assertThat(child.getSampleRate())
            .describedAs("tracestate = '%s' should have sample rate = %s", traceState, expectedRate)
            // Casting to Double is required so that comparison of two Double#NaN will be correct (see Double#equals javadoc for info)
            .isEqualTo(Double.valueOf(expectedRate));

        assertThat(child.getTraceState().toTextHeader())
            .isEqualTo(expectedHeader);

    }

    private TraceContext createChildSpanFromHeaders(Map<String, String> inHeaders) {
        TraceContext child = TraceContext.with64BitId(tracer);
        assertThat(TraceContext.<Map<String, String>>getFromTraceContextTextHeaders().asChildOf(child, inHeaders, TextHeaderMapAccessor.INSTANCE)).isTrue();
        return child;
    }

    @Test
    void testPropagateTransactionIdForUnsampledSpan() {
        final TraceContext rootContext = TraceContext.with64BitId(tracer);
        rootContext.asRootSpan(ConstantSampler.of(false));

        final TraceContext childContext = TraceContext.with64BitId(tracer);
        childContext.asChildOf(rootContext);

        verifyTraceContextContents(childContext.getOutgoingTraceParentTextHeader().toString(),
            childContext.getTraceId().toString(), rootContext.getId().toString(), "00", "00");
        Map<String, byte[]> binaryHeaderMap = new HashMap<>();
        childContext.propagateTraceContext(binaryHeaderMap, BinaryHeaderMapAccessor.INSTANCE);
        verifyTraceContextContents(binaryHeaderMap.get(TraceContext.ELASTIC_TRACE_PARENT_TEXTUAL_HEADER_NAME),
            childContext.getTraceId().toString(), rootContext.getId().toString(), (byte) 0x00, (byte) 0x00);
    }

    @Test
    void testPropagateSpanIdForSampledSpan() {
        final TraceContext rootContext = TraceContext.with64BitId(tracer);
        rootContext.asRootSpan(ConstantSampler.of(true));

        final TraceContext childContext = TraceContext.with64BitId(tracer);
        childContext.asChildOf(rootContext);

        verifyTraceContextContents(childContext.getOutgoingTraceParentTextHeader().toString(),
            childContext.getTraceId().toString(), childContext.getId().toString(), "00", "01");
        Map<String, byte[]> binaryHeaderMap = new HashMap<>();
        childContext.propagateTraceContext(binaryHeaderMap, BinaryHeaderMapAccessor.INSTANCE);
        verifyTraceContextContents(binaryHeaderMap.get(TraceContext.ELASTIC_TRACE_PARENT_TEXTUAL_HEADER_NAME),
            childContext.getTraceId().toString(), childContext.getId().toString(), (byte) 0x00, (byte) 0x01);
    }

    @Test
    void testRootContextSampleRateFromSampler() {
        Sampler sampler = mock(Sampler.class);
        doReturn(true).when(sampler).isSampled(any(Id.class));
        doReturn(0.42d).when(sampler).getSampleRate();

        final TraceContext rootContext = TraceContext.with64BitId(tracer);
        rootContext.asRootSpan(sampler);

        assertThat(rootContext.isRecorded()).isTrue();
        assertThat(rootContext.getSampleRate()).isEqualTo(0.42d);
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
        final TraceContext traceContext = TraceContext.with64BitId(tracer);
        assertThat(traceContext.asChildOf(s)).isFalse();
    }

    private void assertInvalid(byte[] binaryHeader) {
        final TraceContext traceContext = TraceContext.with64BitId(tracer);
        assertThat(traceContext.asChildOf(binaryHeader)).isFalse();
    }

    private void assertValid(String s) {
        final TraceContext traceContext = TraceContext.with64BitId(tracer);
        assertThat(traceContext.asChildOf(s)).isTrue();
        verifyTraceContextContents(traceContext.getOutgoingTraceParentTextHeader().toString(),
            traceContext.getTraceId().toString(), traceContext.getId().toString(), "00", s.substring(53, 55));
    }

    private void assertValid(byte[] binaryHeader) {
        final TraceContext traceContext = TraceContext.with64BitId(tracer);
        assertThat(traceContext.asChildOf(binaryHeader)).isTrue();
        Map<String, byte[]> binaryHeaderMap = new HashMap<>();
        traceContext.propagateTraceContext(binaryHeaderMap, BinaryHeaderMapAccessor.INSTANCE);
        verifyTraceContextContents(binaryHeaderMap.get(TraceContext.ELASTIC_TRACE_PARENT_TEXTUAL_HEADER_NAME),
            traceContext.getTraceId().toString(), traceContext.getId().toString(), (byte) 0x00, binaryHeader[28]);
    }

    private byte[] convertToBinary(String textHeader) {
        final TraceContext traceContext = TraceContext.with64BitId(tracer);
        traceContext.asChildOf(textHeader);
        Map<String, byte[]> binaryHeaderMap = new HashMap<>();
        traceContext.propagateTraceContext(binaryHeaderMap, BinaryHeaderMapAccessor.INSTANCE);
        byte[] binaryHeader = binaryHeaderMap.get(TraceContext.ELASTIC_TRACE_PARENT_TEXTUAL_HEADER_NAME);
        // replace the version and parent ID
        HexUtils.decode(textHeader, 0, 2, binaryHeader, 0);
        HexUtils.decode(textHeader, 36, 16, binaryHeader, 19);
        return binaryHeader;
    }

    @Test
    void testDeserialization() {
        ElasticApmTracer tracer = MockTracer.create();
        CoreConfiguration configuration = tracer.getConfig(CoreConfiguration.class);
        doReturn(Integer.MAX_VALUE).when(configuration).getTracestateSizeLimit();

        final TraceContext traceContext = TraceContext.with64BitId(tracer);
        traceContext.asRootSpan(ConstantSampler.of(true));

        byte[] serializedContext = new byte[TraceContext.SERIALIZED_LENGTH];
        traceContext.serialize(serializedContext);

        TraceContext deserialized = TraceContext.with64BitId(tracer);
        deserialized.deserialize(serializedContext, null, null);

        assertThat(deserialized.traceIdAndIdEquals(serializedContext)).isTrue();
        assertThat(deserialized.getTraceId()).isEqualTo(traceContext.getTraceId());
        assertThat(deserialized.getTransactionId()).isEqualTo(traceContext.getTransactionId());
        assertThat(deserialized.getId()).isEqualTo(traceContext.getId());
        assertThat(deserialized.isSampled()).isEqualTo(traceContext.isSampled());
        assertThat(deserialized.isDiscardable()).isEqualTo(traceContext.isDiscardable());
        assertThat(deserialized.getClock().getOffset()).isEqualTo(traceContext.getClock().getOffset());
    }

    @Test
    void testSetServiceInfoWithEmptyServiceName() {
        TraceContext traceContext = TraceContext.with64BitId(tracer);

        traceContext.setServiceInfo(null, "My Version");
        assertThat(traceContext.getServiceName()).isNull();
        assertThat(traceContext.getServiceVersion()).isNull();
        traceContext.setServiceInfo("", "My Version");
        assertThat(traceContext.getServiceName()).isNull();
        assertThat(traceContext.getServiceVersion()).isNull();
    }

    @Test
    void testSetServiceInfoWithNonEmptyServiceName() {
        TraceContext traceContext = TraceContext.with64BitId(tracer);

        traceContext.setServiceInfo("My Service", null);
        assertThat(traceContext.getServiceName()).isEqualTo("My Service");
        assertThat(traceContext.getServiceVersion()).isNull();
        traceContext.setServiceInfo("My Service", "My Version");
        assertThat(traceContext.getServiceName()).isEqualTo("My Service");
        assertThat(traceContext.getServiceVersion()).isEqualTo("My Version");
    }
}
