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
package co.elastic.apm.agent.report.serialize;

import co.elastic.apm.agent.MockReporter;
import co.elastic.apm.agent.MockTracer;
import co.elastic.apm.agent.configuration.CoreConfiguration;
import co.elastic.apm.agent.configuration.SpyConfiguration;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.MetaData;
import co.elastic.apm.agent.impl.context.AbstractContext;
import co.elastic.apm.agent.impl.error.ErrorCapture;
import co.elastic.apm.agent.impl.payload.Agent;
import co.elastic.apm.agent.impl.payload.ProcessInfo;
import co.elastic.apm.agent.impl.payload.Service;
import co.elastic.apm.agent.impl.payload.SystemInfo;
import co.elastic.apm.agent.impl.sampling.ConstantSampler;
import co.elastic.apm.agent.impl.stacktrace.StacktraceConfiguration;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.report.ApmServerClient;
import com.dslplatform.json.JsonWriter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.stagemonitor.configuration.ConfigurationRegistry;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


class DslJsonSerializerTest {

    private DslJsonSerializer serializer;
    private ObjectMapper objectMapper;
    private ApmServerClient apmServerClient;

    @BeforeEach
    void setUp() {
        StacktraceConfiguration stacktraceConfiguration = mock(StacktraceConfiguration.class);
        when(stacktraceConfiguration.getStackTraceLimit()).thenReturn(15);
        apmServerClient = mock(ApmServerClient.class);
        serializer = new DslJsonSerializer(stacktraceConfiguration, apmServerClient);
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
    void testSerializeNonStringLabels() {
        when(apmServerClient.supportsNonStringLabels()).thenReturn(true);
        assertThat(serializeTags(Map.of("foo", true))).isEqualTo(toJson(Map.of("foo", true)));

        when(apmServerClient.supportsNonStringLabels()).thenReturn(false);
        assertThat(serializeTags(Map.of("foo", true))).isEqualTo(toJson(Collections.singletonMap("foo", null)));
    }

    @Test
    void testErrorSerialization() throws IOException {
        ElasticApmTracer tracer = MockTracer.create();
        Transaction transaction = new Transaction(tracer);
        ErrorCapture error = new ErrorCapture(tracer).asChildOf(transaction.getTraceContext()).withTimestamp(5000);
        error.setTransactionSampled(true);
        error.setTransactionType("test-type");
        error.setException(new Exception("test"));
        error.getContext().addLabel("foo", "bar");

        String errorJson = serializer.toJsonString(error);
        System.out.println("errorJson = " + errorJson);
        JsonNode errorTree = objectMapper.readTree(errorJson);

        assertThat(errorTree.get("timestamp").longValue()).isEqualTo(5000);
        assertThat(errorTree.get("culprit").textValue()).startsWith(this.getClass().getName());
        JsonNode context = errorTree.get("context");
        assertThat(context.get("tags").get("foo").textValue()).isEqualTo("bar");

        JsonNode exception = checkException(errorTree.get("exception"), Exception.class, "test"); ;
        JsonNode stacktrace = exception.get("stacktrace");
        assertThat(stacktrace).hasSize(15);

        assertThat(errorTree.get("transaction").get("sampled").booleanValue()).isTrue();
        assertThat(errorTree.get("transaction").get("type").textValue()).isEqualTo("test-type");
    }

    @Test
    void testErrorSerializationOutsideTrace() throws IOException {
        MockReporter reporter = new MockReporter();
        ElasticApmTracer tracer = MockTracer.createRealTracer(reporter);
        tracer.captureException(new Exception("test"), getClass().getClassLoader());

        String errorJson = serializer.toJsonString(reporter.getFirstError());
        System.out.println("errorJson = " + errorJson);
        JsonNode errorTree = objectMapper.readTree(errorJson);

        assertThat(errorTree.get("id")).isNotNull();
        assertThat(errorTree.get("culprit").textValue()).startsWith(this.getClass().getName());

        JsonNode exception = checkException(errorTree.get("exception"), Exception.class, "test");
        assertThat(exception.get("cause"))
            .describedAs("no cause field expected when there is no chained cause")
            .isNull();

        assertThat(errorTree.get("transaction").get("sampled").booleanValue()).isFalse();
    }

    @Test
    void testErrorSerializationWithExceptionCause() throws JsonProcessingException {
        // testing outside trace is enough to test exception serialization logic
        MockReporter reporter = new MockReporter();
        ElasticApmTracer tracer = MockTracer.createRealTracer(reporter);

        Exception cause2 = new IllegalStateException("second cause");
        Exception cause1 = new RuntimeException("first cause", cause2);
        Exception mainException = new Exception("main exception", cause1);

        tracer.captureException(mainException, getClass().getClassLoader());

        String errorJson = serializer.toJsonString(reporter.getFirstError());
        System.out.println("errorJson = " + errorJson);
        JsonNode errorTree = objectMapper.readTree(errorJson);

        JsonNode exception = checkException(errorTree.get("exception"), Exception.class, "main exception");

        JsonNode firstCause = checkExceptionCause(exception, RuntimeException.class, "first cause");
        checkExceptionCause(firstCause, IllegalStateException.class, "second cause");

    }

    private static JsonNode checkExceptionCause(JsonNode exception, Class<?> expectedType, String expectedMessage){
        JsonNode causeArray = exception.get("cause");
        assertThat(causeArray.getNodeType())
            .describedAs("cause should be an array")
            .isEqualTo(JsonNodeType.ARRAY);
        assertThat(causeArray).hasSize(1);

        return checkException( causeArray.get(0), expectedType, expectedMessage);
    }

    private static JsonNode checkException(JsonNode jsonException, Class<?> expectedType, String expectedMessage){
        assertThat(jsonException.get("type").textValue()).isEqualTo(expectedType.getName());
        assertThat(jsonException.get("message").textValue()).isEqualTo(expectedMessage);

        JsonNode jsonStackTrace = jsonException.get("stacktrace");
        assertThat(jsonStackTrace.getNodeType()).isEqualTo(JsonNodeType.ARRAY);
        assertThat(jsonStackTrace).isNotNull();

        for (JsonNode stackTraceElement : jsonStackTrace) {
            assertThat(stackTraceElement.get("filename")).isNotNull();
            assertThat(stackTraceElement.get("function")).isNotNull();
            assertThat(stackTraceElement.get("library_frame")).isNotNull();
            assertThat(stackTraceElement.get("lineno")).isNotNull();
            assertThat(stackTraceElement.get("module")).isNotNull();
        }

        return jsonException;
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
        Transaction transaction = new Transaction(MockTracer.create());
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
        Span span = new Span(MockTracer.create());
        span.getTraceContext().asRootSpan(ConstantSampler.of(true));
        span.withType("template.jsf.render.view");
        JsonNode spanJson = objectMapper.readTree(serializer.toJsonString(span));
        assertThat(spanJson.get("type").textValue()).isEqualTo("template_jsf_render_view");

        span.withType("template").withSubtype("jsf.lifecycle").withAction("render.view");
        spanJson = objectMapper.readTree(serializer.toJsonString(span));
        assertThat(spanJson.get("type").textValue()).isEqualTo("template.jsf_lifecycle.render_view");

        span = new Span(MockTracer.create());
        span.getTraceContext().asRootSpan(ConstantSampler.of(true));
        span.withType("template").withAction("jsf.render");
        spanJson = objectMapper.readTree(serializer.toJsonString(span));
        assertThat(spanJson.get("type").textValue()).isEqualTo("template..jsf_render");

        span = new Span(MockTracer.create());
        span.getTraceContext().asRootSpan(ConstantSampler.of(true));
        span.withType("template").withSubtype("jsf.render");
        spanJson = objectMapper.readTree(serializer.toJsonString(span));
        assertThat(spanJson.get("type").textValue()).isEqualTo("template.jsf_render");

        span = new Span(MockTracer.create());
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

    @Test
    void testSerializeMetadata() throws IOException {
        Service service = new Service().withAgent(new Agent("MyAgent", "1.11.1")).withName("MyService").withVersion("service-version");
        SystemInfo system = SystemInfo.create();
        ProcessInfo processInfo = new ProcessInfo("title").withPid(1234);
        processInfo.getArgv().add("test");
        serializer.serializeMetaDataNdJson(new MetaData(processInfo, service, system, Map.of("foo", "bar", "baz", "qux")));
        JsonNode metaDataJson = objectMapper.readTree(serializer.toString()).get("metadata");
        System.out.println(metaDataJson);
        JsonNode serviceJson = metaDataJson.get("service");
        assertThat(service).isNotNull();
        assertThat(serviceJson.get("name").textValue()).isEqualTo("MyService");
        assertThat(serviceJson.get("version").textValue()).isEqualTo("service-version");
        JsonNode agentJson = serviceJson.get("agent");
        assertThat(agentJson).isNotNull();
        assertThat(agentJson.get("name").textValue()).isEqualTo("MyAgent");
        assertThat(agentJson.get("version").textValue()).isEqualTo("1.11.1");
        assertThat(agentJson.get("ephemeral_id").textValue()).hasSize(36);
        assertThat(serviceJson.get("node")).isNull();
        JsonNode process = metaDataJson.get("process");
        assertThat(process).isNotNull();
        assertThat(process.get("pid").longValue()).isEqualTo(1234);
        assertThat(process.get("title").textValue()).isEqualTo("title");
        JsonNode argvJson = process.get("argv");
        assertThat(argvJson).isInstanceOf(ArrayNode.class);
        ArrayNode argvArray = (ArrayNode) argvJson;
        assertThat(argvArray).hasSize(1);
        assertThat(process.get("argv").get(0).textValue()).isEqualTo("test");
        assertThat(metaDataJson.get("labels").get("foo").textValue()).isEqualTo("bar");
        assertThat(metaDataJson.get("labels").get("baz").textValue()).isEqualTo("qux");
        JsonNode systemJson = metaDataJson.get("system");
        assertThat(systemJson).isNotNull();
        assertThat(systemJson.get("architecture").textValue()).isEqualTo(system.getArchitecture());
        assertThat(systemJson.get("hostname").textValue()).isEqualTo(system.getHostname());
        assertThat(systemJson.get("platform").textValue()).isEqualTo(system.getPlatform());
    }

    @Test
    void testConfiguredServiceNodeName() throws IOException {
        ConfigurationRegistry configRegistry = SpyConfiguration.createSpyConfig();
        when(configRegistry.getConfig(CoreConfiguration.class).getServiceNodeName()).thenReturn("Custom-Node-Name");
        MetaData metaData = MetaData.create(configRegistry, null, null);
        serializer.serializeMetaDataNdJson(metaData);
        JsonNode metaDataJson = objectMapper.readTree(serializer.toString()).get("metadata");
        System.out.println(metaDataJson);
        JsonNode serviceJson = metaDataJson.get("service");
        assertThat(serviceJson).isNotNull();
        JsonNode nodeJson = serviceJson.get("node");
        assertThat(nodeJson).isNotNull();
        assertThat(nodeJson.get("configured_name").textValue()).isEqualTo("Custom-Node-Name");
    }

    private String toJson(Map<String, Object> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private String serializeTags(Map<String, Object> tags) {
        final AbstractContext context = new AbstractContext() {};
        for (Map.Entry<String, Object> entry : tags.entrySet()) {
            if (entry.getValue() instanceof String) {
                context.addLabel(entry.getKey(), (String) entry.getValue());
            } else if (entry.getValue() instanceof Boolean) {
                context.addLabel(entry.getKey(), (Boolean) entry.getValue());
            } else if (entry.getValue() instanceof Number) {
                context.addLabel(entry.getKey(), (Number) entry.getValue());
            }
        }
        serializer.serializeLabels(context);
        final String jsonString = serializer.jw.toString();
        serializer.jw.reset();
        return jsonString;
    }
}
