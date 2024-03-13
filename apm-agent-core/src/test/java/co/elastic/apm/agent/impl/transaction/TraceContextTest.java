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
import co.elastic.apm.agent.configuration.CoreConfigurationImpl;
import co.elastic.apm.agent.configuration.SpyConfiguration;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.ElasticApmTracerBuilder;
import co.elastic.apm.agent.impl.TextHeaderMapAccessor;
import co.elastic.apm.agent.impl.Utf8HeaderMapAccessor;
import co.elastic.apm.agent.impl.sampling.ConstantSampler;
import co.elastic.apm.agent.impl.sampling.Sampler;
import co.elastic.apm.agent.objectpool.TestObjectPoolFactory;
import co.elastic.apm.agent.tracer.dispatch.HeaderGetter;
import co.elastic.apm.agent.tracer.dispatch.HeaderSetter;
import co.elastic.apm.agent.tracer.metadata.PotentiallyMultiValuedMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.stagemonitor.configuration.ConfigurationRegistry;

import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

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
     * 2.  create a child based on the string header - test {@link TraceContextImpl#asChildOf(String)}
     * 3.  create a grandchild based on byte[] utf8 header - test {@link TraceContextImpl#propagateTraceContext(Object, HeaderSetter)}
     * and {@link TraceContextImpl#asChildOf(Object, HeaderGetter, boolean)} with utf8 encoded byte[]s
     * 4.  create a second grandchild based on text header - test both {@link TraceContextImpl#getOutgoingTraceParentTextHeader()}
     * and {@link TraceContextImpl#asChildOf(String)}
     *
     * @param flagsValue tested flags
     * @param isSampled  whether to test context propagation of sampled trace or not
     */
    private void mixTextAndBinaryParsingAndContextPropagation(String flagsValue, boolean isSampled) {
        Map<String, String> textHeaderMap = Map.of(TraceContextImpl.W3C_TRACE_PARENT_TEXTUAL_HEADER_NAME, "00-0af7651916cd43dd8448eb211c80319c-b9c7c989f97918e1-" + flagsValue);
        final TraceContextImpl child = TraceContextImpl.with64BitId(tracer);
        assertThat(child.asChildOf(textHeaderMap, TextHeaderMapAccessor.INSTANCE)).isTrue();
        assertThat(child.getTraceId().toString()).isEqualTo("0af7651916cd43dd8448eb211c80319c");
        assertThat(child.getParentId().toString()).isEqualTo("b9c7c989f97918e1");
        assertThat(child.getId()).isNotEqualTo(child.getParentId());
        assertThat(child.isSampled()).isEqualTo(isSampled);

        // create a grandchild to ensure proper regenerated trace context
        final TraceContextImpl grandchild1 = TraceContextImpl.with64BitId(tracer);
        final Map<String, String> binaryHeaderMap = new HashMap<>();
        child.propagateTraceContext(binaryHeaderMap, Utf8HeaderMapAccessor.INSTANCE);
        assertThat(grandchild1.asChildOf(binaryHeaderMap, Utf8HeaderMapAccessor.INSTANCE)).isTrue();
        assertThat(grandchild1.getTraceId().toString()).isEqualTo("0af7651916cd43dd8448eb211c80319c");
        assertThat(grandchild1.getParentId().toString()).isEqualTo(child.getId().toString());
        assertThat(grandchild1.getId()).isNotEqualTo(child.getId());
        assertThat(grandchild1.isSampled()).isEqualTo(isSampled);

        String childHeader = child.getOutgoingTraceParentTextHeader().toString();
        assertThat(childHeader).endsWith("-" + flagsValue);
        final TraceContextImpl grandchild2 = TraceContextImpl.with64BitId(tracer);
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
        Map<String, String> textHeaderMap = Map.of(TraceContextImpl.ELASTIC_TRACE_PARENT_TEXTUAL_HEADER_NAME, "00-0af7651916cd43dd8448eb211c80319c-b9c7c989f97918e1-01");
        final TraceContextImpl child = TraceContextImpl.with64BitId(tracer);
        assertThat(child.asChildOf(textHeaderMap, TextHeaderMapAccessor.INSTANCE)).isTrue();
        assertThat(child.getTraceId().toString()).isEqualTo("0af7651916cd43dd8448eb211c80319c");
        assertThat(child.getParentId().toString()).isEqualTo("b9c7c989f97918e1");
        assertThat(child.getId()).isNotEqualTo(child.getParentId());
        assertThat(child.isSampled()).isTrue();
    }

    @Test
    void testW3CTraceparentHeaderPrecedence() {
        Map<String, String> textHeaderMap = Map.of(
            TraceContextImpl.W3C_TRACE_PARENT_TEXTUAL_HEADER_NAME, "00-0af7651916cd43dd8448eb211c80319c-b9c7c989f97918e1-00",
            TraceContextImpl.ELASTIC_TRACE_PARENT_TEXTUAL_HEADER_NAME, "00-dd8448eb211c80319c0af7651916cd43-f97918e1b9c7c989-01"
        );
        final TraceContextImpl child = TraceContextImpl.with64BitId(tracer);
        assertThat(child.asChildOf(textHeaderMap, TextHeaderMapAccessor.INSTANCE)).isTrue();
        assertThat(child.getTraceId().toString()).isEqualTo("0af7651916cd43dd8448eb211c80319c");
        assertThat(child.getParentId().toString()).isEqualTo("b9c7c989f97918e1");
        assertThat(child.getId()).isNotEqualTo(child.getParentId());
        assertThat(child.isSampled()).isFalse();
    }

    @Test
    void testInvalidElasticTraceparentHeader() {
        Map<String, String> textHeaderMap = Map.of(
            TraceContextImpl.W3C_TRACE_PARENT_TEXTUAL_HEADER_NAME, "00-0af7651916cd43dd8448eb211c80319c-b9c7c989f97918e1-01",
            // one char too short trace ID
            TraceContextImpl.ELASTIC_TRACE_PARENT_TEXTUAL_HEADER_NAME, "00-d8448eb211c80319c0af7651916cd43-f97918e1b9c7c989-00"
        );
        final TraceContextImpl child = TraceContextImpl.with64BitId(tracer);
        assertThat(child.asChildOf(textHeaderMap, TextHeaderMapAccessor.INSTANCE)).isTrue();
        // we should fallback to try the W3C header
        assertThat(child.getTraceId().toString()).isEqualTo("0af7651916cd43dd8448eb211c80319c");
        assertThat(child.getParentId().toString()).isEqualTo("b9c7c989f97918e1");
        assertThat(child.getId()).isNotEqualTo(child.getParentId());
        assertThat(child.isSampled()).isTrue();
    }

    @Test
    void testElasticTraceparentHeaderDisabled() {
        Map<String, String> textHeaderMap = Map.of(TraceContextImpl.W3C_TRACE_PARENT_TEXTUAL_HEADER_NAME, "00-0af7651916cd43dd8448eb211c80319c-b9c7c989f97918e1-01");
        final TraceContextImpl child = TraceContextImpl.with64BitId(tracer);
        assertThat(child.asChildOf(textHeaderMap, TextHeaderMapAccessor.INSTANCE)).isTrue();
        Map<String, String> outgoingHeaders = new HashMap<>();
        doReturn(false).when(config.getConfig(CoreConfigurationImpl.class)).isElasticTraceparentHeaderEnabled();
        child.propagateTraceContext(outgoingHeaders, TextHeaderMapAccessor.INSTANCE);
        assertThat(outgoingHeaders.get(TraceContextImpl.W3C_TRACE_PARENT_TEXTUAL_HEADER_NAME)).isNotNull();
        assertThat(outgoingHeaders.get(TraceContextImpl.ELASTIC_TRACE_PARENT_TEXTUAL_HEADER_NAME)).isNull();
    }

    @Test
    void testTraceContextTextHeadersRemoval() {
        Map<String, String> headerMap = new HashMap<>();
        headerMap.put(TraceContextImpl.ELASTIC_TRACE_PARENT_TEXTUAL_HEADER_NAME, "00-0af7651916cd43dd8448eb211c80319c-b9c7c989f97918e1-01");
        headerMap.put(TraceContextImpl.W3C_TRACE_PARENT_TEXTUAL_HEADER_NAME, "00-0af7651916cd43dd8448eb211c80319c-b9c7c989f97918e1-01");
        headerMap.put(TraceContextImpl.TRACESTATE_HEADER_NAME, "foo=bar,baz=qux");
        TraceContextImpl.removeTraceContextHeaders(headerMap, TextHeaderMapAccessor.INSTANCE);
        assertThat(headerMap.get(TraceContextImpl.W3C_TRACE_PARENT_TEXTUAL_HEADER_NAME)).isNull();
        assertThat(headerMap.get(TraceContextImpl.ELASTIC_TRACE_PARENT_TEXTUAL_HEADER_NAME)).isNull();
        assertThat(headerMap.get(TraceContextImpl.TRACESTATE_HEADER_NAME)).isNull();
    }

    @Test
    void testTraceContextHeadersCopy() {
        Map<String, String> original = new HashMap<>();
        original.put(TraceContextImpl.ELASTIC_TRACE_PARENT_TEXTUAL_HEADER_NAME, "00-0af7651916cd43dd8448eb211c80319c-b9c7c989f97918e1-01");
        original.put(TraceContextImpl.W3C_TRACE_PARENT_TEXTUAL_HEADER_NAME, "00-0af7651916cd43dd8448eb211c80319c-b9c7c989f97918e1-01");
        original.put(TraceContextImpl.TRACESTATE_HEADER_NAME, "foo=bar,baz=qux");
        Map<String, String> copy = new HashMap<>();
        TraceContextImpl.copyTraceContextTextHeaders(original, TextHeaderMapAccessor.INSTANCE, copy, TextHeaderMapAccessor.INSTANCE);
        assertThat(copy.get(TraceContextImpl.W3C_TRACE_PARENT_TEXTUAL_HEADER_NAME)).isNotNull();
        assertThat(copy.get(TraceContextImpl.ELASTIC_TRACE_PARENT_TEXTUAL_HEADER_NAME)).isNotNull();
        assertThat(copy.get(TraceContextImpl.TRACESTATE_HEADER_NAME)).isNotNull();
    }

    @Test
    void testTracestateHeader() {
        PotentiallyMultiValuedMap incomingHeaders = new PotentiallyMultiValuedMap();
        incomingHeaders.add("traceparent", "00-0af7651916cd43dd8448eb211c80319c-b9c7c989f97918e1-01");
        incomingHeaders.add("tracestate", "foo=bar");
        incomingHeaders.add("tracestate", "baz=qux,quux=quuz");
        final TraceContextImpl child = TraceContextImpl.with64BitId(tracer);
        assertThat(child.asChildOf(incomingHeaders, MultiValueMapAccessor.INSTANCE)).isTrue();
        assertThat(child.getTraceId().toString()).isEqualTo("0af7651916cd43dd8448eb211c80319c");
        assertThat(child.getParentId().toString()).isEqualTo("b9c7c989f97918e1");
        assertThat(child.getId()).isNotEqualTo(child.getParentId());
        assertThat(child.isSampled()).isTrue();
        PotentiallyMultiValuedMap outgoingHeaders = new PotentiallyMultiValuedMap();
        child.propagateTraceContext(outgoingHeaders, MultiValueMapAccessor.INSTANCE);
        assertThat(outgoingHeaders.size()).isEqualTo(3);
        assertThat(outgoingHeaders.getFirst(TraceContextImpl.W3C_TRACE_PARENT_TEXTUAL_HEADER_NAME)).isNotNull();
        assertThat(outgoingHeaders.getAll(TraceContextImpl.TRACESTATE_HEADER_NAME)).hasSize(1);
        assertThat(outgoingHeaders.getFirst(TraceContextImpl.TRACESTATE_HEADER_NAME)).isEqualTo("foo=bar,baz=qux,quux=quuz");
    }

    @Test
    void testTracestateHeaderSizeLimit() {
        doReturn(20).when(config.getConfig(CoreConfigurationImpl.class)).getTracestateSizeLimit();
        PotentiallyMultiValuedMap incomingHeaders = new PotentiallyMultiValuedMap();
        incomingHeaders.add("traceparent", "00-0af7651916cd43dd8448eb211c80319c-b9c7c989f97918e1-01");
        incomingHeaders.add("tracestate", "foo=bar");
        incomingHeaders.add("tracestate", "baz=qux,quux=quuz");
        final TraceContextImpl child = TraceContextImpl.with64BitId(tracer);
        assertThat(child.asChildOf(incomingHeaders, MultiValueMapAccessor.INSTANCE)).isTrue();
        PotentiallyMultiValuedMap outgoingHeaders = new PotentiallyMultiValuedMap();
        child.propagateTraceContext(outgoingHeaders, MultiValueMapAccessor.INSTANCE);
        assertThat(outgoingHeaders.size()).isEqualTo(3);
        assertThat(outgoingHeaders.getFirst(TraceContextImpl.W3C_TRACE_PARENT_TEXTUAL_HEADER_NAME)).isNotNull();
        assertThat(outgoingHeaders.getAll(TraceContextImpl.TRACESTATE_HEADER_NAME)).hasSize(1);
        assertThat(outgoingHeaders.getFirst(TraceContextImpl.TRACESTATE_HEADER_NAME)).isEqualTo("foo=bar,baz=qux");
    }

    @Test
    void testNoTracestateWhenInvalidTraceparentHeader() {
        Map<String, String> textHeaderMap = Map.of(
            // one char too short trace ID
            TraceContextImpl.ELASTIC_TRACE_PARENT_TEXTUAL_HEADER_NAME, "00-d8448eb211c80319c0af7651916cd43-f97918e1b9c7c989-00",
            TraceContextImpl.TRACESTATE_HEADER_NAME, "foo=bar,baz=qux"
        );
        final TraceContextImpl child = TraceContextImpl.with64BitId(tracer);
        assertThat(child.asChildOf(textHeaderMap, TextHeaderMapAccessor.INSTANCE)).isFalse();

        assertThat(child.isRecorded()).isFalse();

        Map<String, String> outgoingHeaders = new HashMap<>();
        child.propagateTraceContext(outgoingHeaders, TextHeaderMapAccessor.INSTANCE);
        assertThat(outgoingHeaders.get(TraceContextImpl.TRACESTATE_HEADER_NAME)).isNull();
    }

    private void verifyTraceContextContents(String traceContext, String expectedTraceId, String expectedParentId,
                                            String expectedVersion, String expectedFlags) {
        String[] parts = traceContext.split("-");
        assertThat(parts[0]).isEqualTo(expectedVersion);
        assertThat(parts[1]).isEqualTo(expectedTraceId);
        assertThat(parts[2]).isEqualTo(expectedParentId);
        assertThat(parts[3]).isEqualTo(expectedFlags);
    }

    @Test
    void outgoingHeader() {
        final TraceContextImpl traceContext = TraceContextImpl.with64BitId(tracer);
        final String header = "00-0af7651916cd43dd8448eb211c80319c-b9c7c989f97918e1-03";
        assertThat(traceContext.asChildOf(header)).isTrue();
        String parentId = traceContext.getId().toString();
        verifyTraceContextContents(traceContext.getOutgoingTraceParentTextHeader().toString(),
            "0af7651916cd43dd8448eb211c80319c", parentId, "00", "03");
        Map<String, String> headerMap = new HashMap<>();
        traceContext.propagateTraceContext(headerMap, Utf8HeaderMapAccessor.INSTANCE);
        verifyTraceContextContents(headerMap.get(TraceContextImpl.ELASTIC_TRACE_PARENT_TEXTUAL_HEADER_NAME),
            "0af7651916cd43dd8448eb211c80319c", parentId, "00", "03");
    }

    @Test
    void outgoingHeaderRootSpan() {
        final TraceContextImpl traceContext = TraceContextImpl.with64BitId(tracer);
        traceContext.asRootSpan(ConstantSampler.of(true));
        assertThat(traceContext.isSampled()).isTrue();
        String outgoingStringHeader = traceContext.getOutgoingTraceParentTextHeader().toString();
        assertThat(outgoingStringHeader).hasSize(55);
        verifyTraceContextContents(outgoingStringHeader, traceContext.getTraceId().toString(),
            traceContext.getId().toString(), "00", "01");
        Map<String, String> headerMap = new HashMap<>();
        traceContext.propagateTraceContext(headerMap, Utf8HeaderMapAccessor.INSTANCE);
        verifyTraceContextContents(headerMap.get(TraceContextImpl.ELASTIC_TRACE_PARENT_TEXTUAL_HEADER_NAME), traceContext.getTraceId().toString(),
            traceContext.getId().toString(), "00", "01");
    }

    @Test
    void parseFromTraceParentHeader_notSampled() {
        final TraceContextImpl traceContext = TraceContextImpl.with64BitId(tracer);
        final String header = "00-0af7651916cd43dd8448eb211c80319c-b9c7c989f97918e1-00";
        assertThat(traceContext.asChildOf(header)).isTrue();
        assertThat(traceContext.isSampled()).isFalse();
        assertThat(traceContext.getIncomingTraceParentHeader()).isEqualTo(header);
    }

    @Test
    void testResetState() {
        final TraceContextImpl traceContext = TraceContextImpl.with64BitId(tracer);
        traceContext.asChildOf("00-0af7651916cd43dd8448eb211c80319c-b9c7c989f97918e1-00");
        traceContext.resetState();
        assertThat(traceContext.getIncomingTraceParentHeader()).isEqualTo("00-00000000000000000000000000000000-0000000000000000-00");
    }

    @Test
    void testResetOutgoingTextHeader() {
        final TraceContextImpl traceContext = TraceContextImpl.with64BitId(tracer);
        String traceParentHeader = traceContext.getOutgoingTraceParentTextHeader().toString();
        traceContext.asChildOf("00-0af7651916cd43dd8448eb211c80319c-b9c7c989f97918e1-00");
        assertThat(traceContext.getOutgoingTraceParentTextHeader().toString()).isNotEqualTo(traceParentHeader);
    }

    @Test
    void testCopyFrom() {
        Map<String, String> textHeaderMap = Map.of(
            TraceContextImpl.ELASTIC_TRACE_PARENT_TEXTUAL_HEADER_NAME, "00-0af7651916cd43dd8448eb211c80319c-b9c7c989f97918e1-01",
            TraceContextImpl.TRACESTATE_HEADER_NAME, "foo=bar,baz=qux"
        );
        final TraceContextImpl first = TraceContextImpl.with64BitId(tracer);
        assertThat(first.asChildOf(textHeaderMap, TextHeaderMapAccessor.INSTANCE)).isTrue();

        textHeaderMap = Map.of(TraceContextImpl.ELASTIC_TRACE_PARENT_TEXTUAL_HEADER_NAME, "00-8448ebb9c7c989f97918e11916cd43dd-211c80319c0af765-00");
        final TraceContextImpl second = TraceContextImpl.with64BitId(tracer);
        assertThat(second.asChildOf(textHeaderMap, TextHeaderMapAccessor.INSTANCE)).isTrue();

        assertThat(first.getTraceId()).isNotEqualTo(second.getTraceId());
        assertThat(first.getParentId()).isNotEqualTo(second.getParentId());
        assertThat(first.isSampled()).isNotEqualTo(second.isSampled());
        assertThat(first.getOutgoingTraceParentTextHeader()).isNotEqualTo(second.getOutgoingTraceParentTextHeader());
        Map<String, String> binaryHeaderMap = new HashMap<>();
        first.propagateTraceContext(binaryHeaderMap, Utf8HeaderMapAccessor.INSTANCE);
        String firstOutgoingHeader = binaryHeaderMap.get(TraceContextImpl.ELASTIC_TRACE_PARENT_TEXTUAL_HEADER_NAME);
        second.propagateTraceContext(binaryHeaderMap, Utf8HeaderMapAccessor.INSTANCE);
        assertThat(binaryHeaderMap.get(TraceContextImpl.ELASTIC_TRACE_PARENT_TEXTUAL_HEADER_NAME)).isNotEqualTo(firstOutgoingHeader);

        second.copyFrom(first);
        assertThat(first.getTraceId()).isEqualTo(second.getTraceId());
        assertThat(first.getParentId()).isEqualTo(second.getParentId());
        assertThat(first.isSampled()).isEqualTo(second.isSampled());
        assertThat(first.getOutgoingTraceParentTextHeader().toString()).isEqualTo(second.getOutgoingTraceParentTextHeader().toString());
        second.propagateTraceContext(binaryHeaderMap, Utf8HeaderMapAccessor.INSTANCE);
        assertThat(binaryHeaderMap.get(TraceContextImpl.ELASTIC_TRACE_PARENT_TEXTUAL_HEADER_NAME)).isEqualTo(firstOutgoingHeader);
    }

    @Test
    void testAsChildOfHeaders() {
        Map<String, String> textHeaderMap = Map.of(
            TraceContextImpl.ELASTIC_TRACE_PARENT_TEXTUAL_HEADER_NAME, "00-0af7651916cd43dd8448eb211c80319c-b9c7c989f97918e1-01",
            TraceContextImpl.TRACESTATE_HEADER_NAME, "foo=bar,baz=qux"
        );
        final TraceContextImpl first = TraceContextImpl.with64BitId(tracer);
        assertThat(first.asChildOf(textHeaderMap, TextHeaderMapAccessor.INSTANCE)).isTrue();

        final TraceContextImpl second = TraceContextImpl.with64BitId(tracer);
        second.asChildOf(first);

        HashMap<String, String> textHeaders = new HashMap<>();
        second.propagateTraceContext(textHeaders, TextHeaderMapAccessor.INSTANCE);
        assertThat(textHeaders.get(TraceContextImpl.TRACESTATE_HEADER_NAME)).isEqualTo("foo=bar,baz=qux");
        assertThat(textHeaders.get(TraceContextImpl.W3C_TRACE_PARENT_TEXTUAL_HEADER_NAME)).startsWith("00-0af7651916cd43dd8448eb211c80319c-");


        final TraceContextImpl firstUtf8 = TraceContextImpl.with64BitId(tracer);
        assertThat(firstUtf8.asChildOf(textHeaderMap, Utf8HeaderMapAccessor.INSTANCE)).isTrue();

        HashMap<String, String> utf8Headers = new HashMap<>();
        firstUtf8.propagateTraceContext(utf8Headers, Utf8HeaderMapAccessor.INSTANCE);
        assertThat(utf8Headers.get(TraceContextImpl.TRACESTATE_HEADER_NAME)).isEqualTo("foo=bar,baz=qux");
        assertThat(utf8Headers.get(TraceContextImpl.W3C_TRACE_PARENT_TEXTUAL_HEADER_NAME)).startsWith("00-0af7651916cd43dd8448eb211c80319c-");
    }

    @Test
    void testRandomValue() {
        final TraceContextImpl traceContext = TraceContextImpl.with64BitId(tracer);
        traceContext.asRootSpan(ConstantSampler.of(true));
        assertThat(traceContext.getTraceId().isEmpty()).isFalse();
        assertThat(traceContext.getParentId().isEmpty()).isTrue();
        assertThat(traceContext.getId().isEmpty()).isFalse();
    }

    @Test
    void testSetSampled() {
        final TraceContextImpl traceContext = TraceContextImpl.with64BitId(tracer);
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
        final TraceContextImpl traceContext = createRootSpan(0.42d);
        String traceState = traceContext.getTraceState().toTextHeader();
        assertThat(traceState).isEqualTo("es=s:0.42");
    }

    private TraceContextImpl createRootSpan(double sampleRate) {
        final TraceContextImpl traceContext = TraceContextImpl.with64BitId(tracer);

        Sampler sampler = mock(Sampler.class);
        doReturn(true).when(sampler).isSampled(any(IdImpl.class));
        doReturn(sampleRate).when(sampler).getSampleRate();
        doReturn(TraceState.getHeaderValue(sampleRate)).when(sampler).getTraceStateHeader();

        traceContext.asRootSpan(sampler);
        return traceContext;
    }

    @Test
    void testTracedChildSpanWithoutTraceState() {
        Map<String, String> headers = Map.of(
            TraceContextImpl.W3C_TRACE_PARENT_TEXTUAL_HEADER_NAME, "00-0af7651916cd43dd8448eb211c80319c-b9c7c989f97918e1-01"
        );
        TraceContextImpl child = createChildSpanFromHeaders(headers);

        assertThat(child.isSampled()).isTrue();
        assertThat(child.getSampleRate()).isNaN();
    }

    @Test
    void testNonTracedChildSpanWithoutTraceState() {
        Map<String, String> headers = Map.of(
            TraceContextImpl.W3C_TRACE_PARENT_TEXTUAL_HEADER_NAME, "00-0af7651916cd43dd8448eb211c80319c-b9c7c989f97918e1-00"
        );
        TraceContextImpl child = createChildSpanFromHeaders(headers);

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
        headers.put(TraceContextImpl.W3C_TRACE_PARENT_TEXTUAL_HEADER_NAME, "00-0af7651916cd43dd8448eb211c80319c-b9c7c989f97918e1-01");
        if (null != traceState) {
            headers.put(TraceContextImpl.TRACESTATE_HEADER_NAME, traceState);
        }

        TraceContextImpl child = createChildSpanFromHeaders(headers);

        assertThat(child.isSampled()).isTrue();

        assertThat(child.getSampleRate())
            .describedAs("tracestate = '%s' should have sample rate = %s", traceState, expectedRate)
            // Casting to Double is required so that comparison of two Double#NaN will be correct (see Double#equals javadoc for info)
            .isEqualTo(Double.valueOf(expectedRate));

        assertThat(child.getTraceState().toTextHeader())
            .isEqualTo(expectedHeader);

    }

    private TraceContextImpl createChildSpanFromHeaders(Map<String, String> inHeaders) {
        TraceContextImpl child = TraceContextImpl.with64BitId(tracer);
        assertThat(child.asChildOf(inHeaders, TextHeaderMapAccessor.INSTANCE)).isTrue();
        return child;
    }

    @Test
    void testPropagateTransactionIdForUnsampledSpan() {
        final TraceContextImpl rootContext = TraceContextImpl.with64BitId(tracer);
        rootContext.asRootSpan(ConstantSampler.of(false));

        final TraceContextImpl childContext = TraceContextImpl.with64BitId(tracer);
        childContext.asChildOf(rootContext);

        verifyTraceContextContents(childContext.getOutgoingTraceParentTextHeader().toString(),
            childContext.getTraceId().toString(), rootContext.getId().toString(), "00", "00");
        Map<String, String> binaryHeaderMap = new HashMap<>();
        childContext.propagateTraceContext(binaryHeaderMap, Utf8HeaderMapAccessor.INSTANCE);
        verifyTraceContextContents(binaryHeaderMap.get(TraceContextImpl.ELASTIC_TRACE_PARENT_TEXTUAL_HEADER_NAME),
            childContext.getTraceId().toString(), rootContext.getId().toString(), "00", "00");
    }

    @Test
    void testPropagateSpanIdForSampledSpan() {
        final TraceContextImpl rootContext = TraceContextImpl.with64BitId(tracer);
        rootContext.asRootSpan(ConstantSampler.of(true));

        final TraceContextImpl childContext = TraceContextImpl.with64BitId(tracer);
        childContext.asChildOf(rootContext);

        verifyTraceContextContents(childContext.getOutgoingTraceParentTextHeader().toString(),
            childContext.getTraceId().toString(), childContext.getId().toString(), "00", "01");
        Map<String, String> binaryHeaderMap = new HashMap<>();
        childContext.propagateTraceContext(binaryHeaderMap, Utf8HeaderMapAccessor.INSTANCE);
        verifyTraceContextContents(binaryHeaderMap.get(TraceContextImpl.ELASTIC_TRACE_PARENT_TEXTUAL_HEADER_NAME),
            childContext.getTraceId().toString(), childContext.getId().toString(), "00", "01");
    }

    @Test
    void testRootContextSampleRateFromSampler() {
        Sampler sampler = mock(Sampler.class);
        doReturn(true).when(sampler).isSampled(any(IdImpl.class));
        doReturn(0.42d).when(sampler).getSampleRate();

        final TraceContextImpl rootContext = TraceContextImpl.with64BitId(tracer);
        rootContext.asRootSpan(sampler);

        assertThat(rootContext.isRecorded()).isTrue();
        assertThat(rootContext.getSampleRate()).isEqualTo(0.42d);
    }

    @Test
    void testUnknownVersion() {
        String testTextHeader = "42-0af7651916cd43dd8448eb211c80319c-b9c7c989f97918e1-01";
        assertValid(testTextHeader);
    }

    @Test
    void testUnknownExtraStuff() {
        String testTextHeader = "42-0af7651916cd43dd8448eb211c80319c-b9c7c989f97918e1-01-unknown-extra-stuff";
        assertValid(testTextHeader);
    }

    // If a traceparent header is invalid, ignore it and create a new root context

    @Test
    void testInvalidHeader_traceIdAllZeroes() {
        String testTextHeader = "00-00000000000000000000000000000000-b9c7c989f97918e1-00";
        assertInvalid(testTextHeader);
    }

    @Test
    void testInvalidHeader_spanIdAllZeroes() {
        String testTextHeader = "00-0af7651916cd43dd8448eb211c80319c-0000000000000000-00";
        assertInvalid(testTextHeader);
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
        final TraceContextImpl traceContext = TraceContextImpl.with64BitId(tracer);
        assertThat(traceContext.asChildOf(s)).isFalse();
        assertThat(traceContext.asChildOf(s.getBytes(StandardCharsets.UTF_8), CharAccessor.forAsciiBytes())).isFalse();
    }

    private void assertValid(String s) {
        TraceContextImpl textTraceContext = TraceContextImpl.with64BitId(tracer);
        assertThat(textTraceContext.asChildOf(s)).isTrue();
        verifyTraceContextContents(textTraceContext.getOutgoingTraceParentTextHeader().toString(),
            textTraceContext.getTraceId().toString(), textTraceContext.getId().toString(), "00", s.substring(53, 55));

        TraceContextImpl utf8TraceContext = TraceContextImpl.with64BitId(tracer);
        assertThat(utf8TraceContext.asChildOf(s.getBytes(StandardCharsets.UTF_8), CharAccessor.forAsciiBytes())).isTrue();
        verifyTraceContextContents(utf8TraceContext.getOutgoingTraceParentTextHeader().toString(),
            utf8TraceContext.getTraceId().toString(), utf8TraceContext.getId().toString(), "00", s.substring(53, 55));
    }

    @Test
    void testDeserialization() {
        ElasticApmTracer tracer = MockTracer.create();
        CoreConfigurationImpl configuration = tracer.getConfig(CoreConfigurationImpl.class);
        doReturn(Integer.MAX_VALUE).when(configuration).getTracestateSizeLimit();

        final TraceContextImpl traceContext = TraceContextImpl.with64BitId(tracer);
        traceContext.asRootSpan(ConstantSampler.of(true));

        byte[] serializedContext = new byte[TraceContextImpl.SERIALIZED_LENGTH];
        traceContext.serialize(serializedContext);

        TraceContextImpl deserialized = TraceContextImpl.with64BitId(tracer);
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
        TraceContextImpl traceContext = TraceContextImpl.with64BitId(tracer);

        traceContext.setServiceInfo(null, "My Version");
        assertThat(traceContext.getServiceName()).isNull();
        assertThat(traceContext.getServiceVersion()).isNull();
        traceContext.setServiceInfo("", "My Version");
        assertThat(traceContext.getServiceName()).isNull();
        assertThat(traceContext.getServiceVersion()).isNull();
    }

    @Test
    void testSetServiceInfoWithNonEmptyServiceName() {
        TraceContextImpl traceContext = TraceContextImpl.with64BitId(tracer);

        traceContext.setServiceInfo("My Service", null);
        assertThat(traceContext.getServiceName()).isEqualTo("My Service");
        assertThat(traceContext.getServiceVersion()).isNull();
        traceContext.setServiceInfo("My Service", "My Version");
        assertThat(traceContext.getServiceName()).isEqualTo("My Service");
        assertThat(traceContext.getServiceVersion()).isEqualTo("My Version");
    }
}
