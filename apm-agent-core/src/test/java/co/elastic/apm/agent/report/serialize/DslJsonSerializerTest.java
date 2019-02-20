/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2019 Elastic and contributors
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
package co.elastic.apm.agent.report.serialize;

import co.elastic.apm.agent.MockTracer;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.context.AbstractContext;
import co.elastic.apm.agent.impl.error.ErrorCapture;
import co.elastic.apm.agent.impl.sampling.ConstantSampler;
import co.elastic.apm.agent.impl.stacktrace.StacktraceConfiguration;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.Transaction;
import com.dslplatform.json.JsonWriter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Enumeration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.Mockito.mock;


class DslJsonSerializerTest {

    private DslJsonSerializer serializer;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        serializer = new DslJsonSerializer(mock(StacktraceConfiguration.class));
        objectMapper = new ObjectMapper();
    }

    @Test
    void serializeTags() {
        assertSoftly(softly -> {
            softly.assertThat(serializeTags(Map.of(".**", "foo.bar"))).isEqualTo(toJson(Map.of("___", "foo.bar")));
            softly.assertThat(serializeTags(Map.of("foo.bar", "baz"))).isEqualTo(toJson(Map.of("foo_bar", "baz")));
            softly.assertThat(serializeTags(Map.of("foo.bar.baz", "qux"))).isEqualTo(toJson(Map.of("foo_bar_baz", "qux")));
            softly.assertThat(serializeTags(Map.of("foo*bar*baz", "qux"))).isEqualTo(toJson(Map.of("foo_bar_baz", "qux")));
            softly.assertThat(serializeTags(Map.of("foo\"bar\"baz", "qux"))).isEqualTo(toJson(Map.of("foo_bar_baz", "qux")));
            final String longRandomString = RandomStringUtils.randomAlphanumeric(1025);
            final String truncatedLongRandomString = longRandomString.substring(0, 1023) + "…";
            softly.assertThat(serializeTags(Map.of(longRandomString, longRandomString))).isEqualTo(toJson(Map.of(truncatedLongRandomString, truncatedLongRandomString)));
        });
    }

    @Test
    void testErrorSerialization() throws IOException {
        ElasticApmTracer tracer = MockTracer.create();
        Transaction transaction = new Transaction(tracer);
        ErrorCapture error = new ErrorCapture(tracer).asChildOf(transaction.getTraceContext()).withTimestamp(5000);
        error.setTransactionSampled(true);
        error.setTransactionType("test-type");
        error.setException(new Exception("test"));
        error.getContext().addTag("foo", "bar");
        String errorJson = serializer.toJsonString(error);
        System.out.println("errorJson = " + errorJson);
        JsonNode errorTree = objectMapper.readTree(errorJson);
        assertThat(errorTree.get("timestamp").longValue()).isEqualTo(5000);
        assertThat(errorTree.get("culprit").textValue()).startsWith(this.getClass().getName());
        JsonNode context = errorTree.get("context");
        assertThat(context.get("tags").get("foo").textValue()).isEqualTo("bar");
        JsonNode exception = errorTree.get("exception");
        assertThat(exception.get("message").textValue()).isEqualTo("test");
        assertThat(exception.get("stacktrace")).isNotNull();
        assertThat(exception.get("type").textValue()).isEqualTo(Exception.class.getName());
        assertThat(errorTree.get("transaction").get("sampled").booleanValue()).isTrue();
        assertThat(errorTree.get("transaction").get("type").textValue()).isEqualTo("test-type");
    }

    @Test
    void testLimitStringValueLength() throws IOException {
        StringBuilder longValue = new StringBuilder(DslJsonSerializer.MAX_VALUE_LENGTH + 1);
        for (int i = 0; i < DslJsonSerializer.MAX_VALUE_LENGTH + 1; i++) {
            longValue.append('0');
        }

        StringBuilder longStringValue = new StringBuilder(DslJsonSerializer.MAX_LONG_STRING_VALUE_LENGTH + 1);
        for (int i = 0; i < DslJsonSerializer.MAX_LONG_STRING_VALUE_LENGTH + 1; i++) {
            longStringValue.append('0');
        }
        serializer.jw.writeByte(JsonWriter.OBJECT_START);
        serializer.writeField("string", longValue.toString());
        serializer.writeField("stringBuilder", longValue);
        serializer.writeLongStringField("longString", longStringValue.toString());
        serializer.writeLastField("lastString", longValue.toString());
        serializer.jw.writeByte(JsonWriter.OBJECT_END);
        final JsonNode jsonNode = objectMapper.readTree(serializer.jw.toString());
        assertThat(jsonNode.get("stringBuilder").textValue()).hasSize(DslJsonSerializer.MAX_VALUE_LENGTH).endsWith("…");
        assertThat(jsonNode.get("string").textValue()).hasSize(DslJsonSerializer.MAX_VALUE_LENGTH).endsWith("…");
        assertThat(jsonNode.get("longString").textValue()).hasSize(DslJsonSerializer.MAX_LONG_STRING_VALUE_LENGTH).endsWith("…");
        assertThat(jsonNode.get("lastString").textValue()).hasSize(DslJsonSerializer.MAX_VALUE_LENGTH).endsWith("…");
    }

    @Test
    void testNullHeaders() throws IOException {
        Transaction transaction = new Transaction(mock(ElasticApmTracer.class));
        transaction.getContext().getRequest().addHeader("foo", (String) null);
        transaction.getContext().getRequest().addHeader("baz", (Enumeration<String>) null);
        transaction.getContext().getRequest().getHeaders().add("bar", null);
        JsonNode jsonNode = objectMapper.readTree(serializer.toJsonString(transaction));
        System.out.println(jsonNode);
        // calling addHeader with a null value ignores the header
        assertThat(jsonNode.get("context").get("request").get("headers").get("foo")).isNull();
        assertThat(jsonNode.get("context").get("request").get("headers").get("baz")).isNull();
        // should a null value sneak in, it should not break
        assertThat(jsonNode.get("context").get("request").get("headers").get("bar").isNull()).isTrue();
    }

    @Test
    void testSpanTypeSerialization() throws IOException {
        Span span = new Span(mock(ElasticApmTracer.class));
        span.getTraceContext().asRootSpan(ConstantSampler.of(true));
        span.withType("template.jsf.render.view");
        JsonNode spanJson = objectMapper.readTree(serializer.toJsonString(span));
        assertThat(spanJson.get("type").textValue()).isEqualTo("template_jsf_render_view");

        span.withType("template").withSubtype("jsf.lifecycle").withAction("render.view");
        spanJson = objectMapper.readTree(serializer.toJsonString(span));
        assertThat(spanJson.get("type").textValue()).isEqualTo("template.jsf_lifecycle.render_view");

        span = new Span(mock(ElasticApmTracer.class));
        span.getTraceContext().asRootSpan(ConstantSampler.of(true));
        span.withType("template").withAction("jsf.render");
        spanJson = objectMapper.readTree(serializer.toJsonString(span));
        assertThat(spanJson.get("type").textValue()).isEqualTo("template..jsf_render");

        span = new Span(mock(ElasticApmTracer.class));
        span.getTraceContext().asRootSpan(ConstantSampler.of(true));
        span.withType("template").withSubtype("jsf.render");
        spanJson = objectMapper.readTree(serializer.toJsonString(span));
        assertThat(spanJson.get("type").textValue()).isEqualTo("template.jsf_render");

        span = new Span(mock(ElasticApmTracer.class));
        span.getTraceContext().asRootSpan(ConstantSampler.of(true));
        span.withSubtype("jsf").withAction("render");
        spanJson = objectMapper.readTree(serializer.toJsonString(span));
        assertThat(spanJson.get("type").isNull()).isTrue();
        System.out.println(spanJson);
    }

    @Test
    void testInlineReplacement() {
        StringBuilder sb = new StringBuilder("this.is.a.string");
        DslJsonSerializer.replace(sb, ".", "_DOT_", 6);
        assertThat(sb.toString()).isEqualTo("this.is_DOT_a_DOT_string");
    }

    private String toJson(Map<String, String> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private String serializeTags(Map<String, String> tags) {
        final AbstractContext context = new AbstractContext() {};
        tags.forEach(context::addTag);
        serializer.serializeTags(context);
        final String jsonString = serializer.jw.toString();
        serializer.jw.reset();
        return jsonString;
    }
}
