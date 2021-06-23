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
package co.elastic.apm.agent.report.serialize;

import co.elastic.apm.agent.MockReporter;
import co.elastic.apm.agent.MockTracer;
import co.elastic.apm.agent.collections.LongList;
import co.elastic.apm.agent.configuration.CoreConfiguration;
import co.elastic.apm.agent.configuration.SpyConfiguration;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.MetaData;
import co.elastic.apm.agent.impl.MetaDataMock;
import co.elastic.apm.agent.impl.Tracer;
import co.elastic.apm.agent.impl.context.AbstractContext;
import co.elastic.apm.agent.impl.context.Headers;
import co.elastic.apm.agent.impl.context.Request;
import co.elastic.apm.agent.impl.error.ErrorCapture;
import co.elastic.apm.agent.impl.payload.Agent;
import co.elastic.apm.agent.impl.payload.CloudProviderInfo;
import co.elastic.apm.agent.impl.payload.Language;
import co.elastic.apm.agent.impl.payload.ProcessInfo;
import co.elastic.apm.agent.impl.payload.Service;
import co.elastic.apm.agent.impl.payload.SystemInfo;
import co.elastic.apm.agent.impl.sampling.ConstantSampler;
import co.elastic.apm.agent.impl.sampling.Sampler;
import co.elastic.apm.agent.impl.stacktrace.StacktraceConfiguration;
import co.elastic.apm.agent.impl.transaction.Id;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.StackFrame;
import co.elastic.apm.agent.impl.transaction.TraceContext;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.report.ApmServerClient;
import co.elastic.apm.agent.util.IOUtils;
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

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


class DslJsonSerializerTest {

    private DslJsonSerializer serializer;
    private ObjectMapper objectMapper;
    private ApmServerClient apmServerClient;
    private Future<MetaData> metaData;

    @BeforeEach
    void setUp() throws Exception {
        StacktraceConfiguration stacktraceConfiguration = mock(StacktraceConfiguration.class);
        when(stacktraceConfiguration.getStackTraceLimit()).thenReturn(15);
        apmServerClient = mock(ApmServerClient.class);
        metaData = MetaData.create(SpyConfiguration.createSpyConfig(), null);
        serializer = new DslJsonSerializer(stacktraceConfiguration, apmServerClient, metaData);
        serializer.blockUntilReady();
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
    void testErrorSerialization() {
        ElasticApmTracer tracer = MockTracer.create();
        Transaction transaction = new Transaction(tracer);
        transaction.start(TraceContext.asRoot(), null, -1, ConstantSampler.of(true), null);
        ErrorCapture error = new ErrorCapture(tracer).asChildOf(transaction).withTimestamp(5000);
        error.setTransactionSampled(true);
        error.setTransactionType("test-type");
        error.setException(new Exception("test"));
        error.getContext().addLabel("foo", "bar");

        JsonNode errorTree = readJsonString(serializer.toJsonString(error));

        assertThat(errorTree.get("id")).isNotNull();
        assertThat(errorTree.get("trace_id")).isNotNull();
        assertThat(errorTree.get("parent_id")).isNotNull();
        assertThat(errorTree.get("transaction_id")).isNotNull();

        assertThat(errorTree.get("timestamp").longValue()).isEqualTo(5000);
        assertThat(errorTree.get("culprit").textValue()).startsWith(this.getClass().getName());
        JsonNode context = errorTree.get("context");
        assertThat(context.get("tags").get("foo").textValue()).isEqualTo("bar");

        JsonNode exception = checkException(errorTree.get("exception"), Exception.class, "test");
        JsonNode stacktrace = exception.get("stacktrace");
        assertThat(stacktrace).hasSize(15);

        assertThat(errorTree.get("transaction").get("sampled").booleanValue()).isTrue();
        assertThat(errorTree.get("transaction").get("type").textValue()).isEqualTo("test-type");
    }

    @Test
    void testErrorSerializationAllFrames() {
        StacktraceConfiguration stacktraceConfiguration = mock(StacktraceConfiguration.class);
        when(stacktraceConfiguration.getStackTraceLimit()).thenReturn(-1);
        serializer = new DslJsonSerializer(stacktraceConfiguration, apmServerClient, metaData);

        ErrorCapture error = new ErrorCapture(MockTracer.create()).withTimestamp(5000);
        Exception exception = new Exception("test");
        error.setException(exception);

        JsonNode errorTree = readJsonString(serializer.toJsonString(error));
        JsonNode stacktrace = checkException(errorTree.get("exception"), Exception.class, "test").get("stacktrace");
        assertThat(stacktrace).hasSizeGreaterThan(15);
    }

    @Test
    void testErrorSerializationWithEmptyTraceId() {
        ElasticApmTracer tracer = MockTracer.create();
        Transaction transaction = new Transaction(tracer);
        transaction.start(TraceContext.asRoot(), null, -1, ConstantSampler.of(true), null);
        transaction.getTraceContext().getTraceId().resetState();
        ErrorCapture error = new ErrorCapture(tracer).asChildOf(transaction).withTimestamp(5000);

        JsonNode errorTree = readJsonString(serializer.toJsonString(error));

        assertThat(errorTree.get("id")).isNotNull();
        assertThat(errorTree.get("timestamp").longValue()).isEqualTo(5000);

        // Verify the limitation of not sending an Error event with parent_id and/or transaction_id without trace_id
        assertThat(errorTree.get("trace_id")).isNull();
        assertThat(errorTree.get("parent_id")).isNull();
        assertThat(errorTree.get("transaction_id")).isNull();
    }

    @Test
    void testErrorSerializationOutsideTrace() {
        MockReporter reporter = new MockReporter();
        Tracer tracer = MockTracer.createRealTracer(reporter);
        tracer.captureAndReportException(new Exception("test"), getClass().getClassLoader());

        String errorJson = serializer.toJsonString(reporter.getFirstError());
        JsonNode errorTree = readJsonString(errorJson);

        assertThat(errorTree.get("id")).isNotNull();
        assertThat(errorTree.get("culprit").textValue()).startsWith(this.getClass().getName());

        JsonNode exception = checkException(errorTree.get("exception"), Exception.class, "test");
        assertThat(exception.get("cause"))
            .describedAs("no cause field expected when there is no chained cause")
            .isNull();

        assertThat(errorTree.get("transaction").get("sampled").booleanValue()).isFalse();
    }

    @Test
    void testErrorSerializationWithExceptionCause() {
        // testing outside trace is enough to test exception serialization logic
        MockReporter reporter = new MockReporter();
        Tracer tracer = MockTracer.createRealTracer(reporter);

        Exception cause2 = new IllegalStateException("second cause");
        Exception cause1 = new RuntimeException("first cause", cause2);
        Exception mainException = new Exception("main exception", cause1);

        tracer.captureAndReportException(mainException, getClass().getClassLoader());

        JsonNode errorTree = readJsonString(serializer.toJsonString(reporter.getFirstError()));

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
            assertThat(stackTraceElement.get("classname")).isNotNull();
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
    void testNullTransactionHeaders() {
        Transaction transaction = new Transaction(MockTracer.create());
        transaction.getContext().getRequest().addHeader("foo", (String) null);
        transaction.getContext().getRequest().addHeader("baz", (Enumeration<String>) null);
        transaction.getContext().getRequest().getHeaders().add("bar", null);
        JsonNode jsonNode = readJsonString(serializer.toJsonString(transaction));
        // calling addHeader with a null value ignores the header
        assertThat(jsonNode.get("context").get("request").get("headers").get("foo")).isNull();
        assertThat(jsonNode.get("context").get("request").get("headers").get("baz")).isNull();
        // should a null value sneak in, it should not break
        assertThat(jsonNode.get("context").get("request").get("headers").get("bar").isNull()).isTrue();
    }

    @Test
    void testMessageHeaders() {
        Span span = new Span(MockTracer.create());
        span.getTraceContext().asRootSpan(ConstantSampler.of(true));
        span.withType("messaging").withSubtype("kafka");

        Headers headers = span.getContext().getMessage().getHeaders();

        headers.add("null-string-value", (String) null);
        headers.add("string-value", "as-is");

        headers.add("null-binary-value", (byte[]) null);
        headers.add("binary-value", "binary-value".getBytes(StandardCharsets.UTF_8));

        JsonNode jsonNode = readJsonString(serializer.toJsonString(span));
        JsonNode jsonHeaders = jsonNode.get("context").get("message").get("headers");
        assertThat(jsonHeaders.get("null-string-value"))
            .describedAs("null value string header should be serialized")
            .isNotNull();
        assertThat(jsonHeaders.get("null-string-value").isNull()).isTrue();
        assertThat(jsonHeaders.get("string-value").asText()).isEqualTo("as-is");

        assertThat(jsonHeaders.get("null-binary-value"))
            .describedAs("null value binary header should be serialized")
            .isNotNull();
        assertThat(jsonHeaders.get("null-binary-value").isNull())
            .isTrue();
        assertThat(jsonHeaders.get("binary-value").asText()).isEqualTo("binary-value");
    }

    @Test
    void testSpanTypeSerialization() {
        Span span = new Span(MockTracer.create());
        span.getTraceContext().asRootSpan(ConstantSampler.of(true));
        span.withType("template.jsf.render.view");
        JsonNode spanJson = readJsonString(serializer.toJsonString(span));
        assertThat(spanJson.get("type").textValue()).isEqualTo("template_jsf_render_view");
        JsonNode context = spanJson.get("context");
        assertThat(context).isNotNull();
        assertThat(context.get("message")).isNull();
        assertThat(context.get("db")).isNull();
        assertThat(context.get("http")).isNull();

        span.withType("template").withSubtype("jsf.lifecycle").withAction("render.view");
        spanJson = readJsonString(serializer.toJsonString(span));
        assertThat(spanJson.get("type").textValue()).isEqualTo("template.jsf_lifecycle.render_view");

        span = new Span(MockTracer.create());
        span.getTraceContext().asRootSpan(ConstantSampler.of(true));
        span.withType("template").withAction("jsf.render");
        spanJson = readJsonString(serializer.toJsonString(span));
        assertThat(spanJson.get("type").textValue()).isEqualTo("template..jsf_render");

        span = new Span(MockTracer.create());
        span.getTraceContext().asRootSpan(ConstantSampler.of(true));
        span.withType("template").withSubtype("jsf.render");
        spanJson = readJsonString(serializer.toJsonString(span));
        assertThat(spanJson.get("type").textValue()).isEqualTo("template.jsf_render");

        span = new Span(MockTracer.create());
        span.getTraceContext().asRootSpan(ConstantSampler.of(true));
        span.withSubtype("jsf").withAction("render");
        spanJson = readJsonString(serializer.toJsonString(span));
        assertThat(spanJson.get("type").isNull()).isTrue();
    }

    @Test
    void testSpanHttpContextSerialization() {
        Span span = new Span(MockTracer.create());
        span.getContext().getHttp()
            .withMethod("GET")
            .withStatusCode(523)
            .withUrl("http://whatever.com/path");

        JsonNode spanJson = readJsonString(serializer.toJsonString(span));
        JsonNode context = spanJson.get("context");
        JsonNode http = context.get("http");
        assertThat(http).isNotNull();
        assertThat(http.get("method").textValue()).isEqualTo("GET");
        assertThat(http.get("url").textValue()).isEqualTo("http://whatever.com/path");
        assertThat(http.get("status_code").intValue()).isEqualTo(523);
    }

    @Test
    void testSpanDestinationContextSerialization() {
        Span span = new Span(MockTracer.create());
        span.getContext().getDestination().withAddress("whatever.com").withPort(80)
            .getService()
            .withName("http://whatever.com")
            .withResource("whatever.com:80")
            .withType("external");

        JsonNode spanJson = readJsonString(serializer.toJsonString(span));
        JsonNode context = spanJson.get("context");
        JsonNode destination = context.get("destination");
        assertThat(destination).isNotNull();
        assertThat("whatever.com").isEqualTo(destination.get("address").textValue());
        assertThat(80).isEqualTo(destination.get("port").intValue());
        JsonNode service = destination.get("service");
        assertThat(service).isNotNull();
        assertThat("http://whatever.com").isEqualTo(service.get("name").textValue());
        assertThat("whatever.com:80").isEqualTo(service.get("resource").textValue());
        assertThat("external").isEqualTo(service.get("type").textValue());
    }

    @Test
    void testSpanMessageContextSerialization() {
        Span span = new Span(MockTracer.create());
        span.getContext().getMessage()
            .withQueue("test-queue")
            .withBody("test-body")
            .addHeader("text-header", "text-value")
            .addHeader("binary-header", "binary-value".getBytes(StandardCharsets.UTF_8))
            .withAge(20);

        JsonNode spanJson = readJsonString(serializer.toJsonString(span));
        JsonNode context = spanJson.get("context");
        JsonNode message = context.get("message");
        assertThat(message).isNotNull();
        JsonNode queue = message.get("queue");
        assertThat(queue).isNotNull();
        assertThat("test-queue").isEqualTo(queue.get("name").textValue());
        JsonNode body = message.get("body");
        assertThat("test-body").isEqualTo(body.textValue());
        JsonNode headers = message.get("headers");
        assertThat(headers).isNotNull();
        assertThat(headers.get("text-header").textValue()).isEqualTo("text-value");
        assertThat(headers.get("binary-header").textValue()).isEqualTo("binary-value");
        JsonNode age = message.get("age");
        assertThat(age).isNotNull();
        JsonNode ms = age.get("ms");
        assertThat(ms).isNotNull();
        assertThat(ms.longValue()).isEqualTo(20);
    }

    @Test
    void testSpanMessageContextInvalidTimestamp() {
        Span span = new Span(MockTracer.create());
        span.getContext().getMessage()
            .withQueue("test-queue");

        JsonNode spanJson = readJsonString(serializer.toJsonString(span));
        JsonNode context = spanJson.get("context");
        JsonNode message = context.get("message");
        assertThat(message).isNotNull();
        JsonNode queue = message.get("queue");
        assertThat(queue).isNotNull();
        assertThat("test-queue").isEqualTo(queue.get("name").textValue());
        JsonNode age = message.get("age");
        assertThat(age).isNull();
    }

    @Test
    void testSpanDbContextSerialization() {
        Span span = new Span(MockTracer.create());
        span.getContext().getDb()
            .withAffectedRowsCount(5)
            .withInstance("test-instance")
            .withStatement("SELECT * FROM TABLE").withDbLink("db-link");

        JsonNode spanJson = readJsonString(serializer.toJsonString(span));
        JsonNode context = spanJson.get("context");
        JsonNode db = context.get("db");
        assertThat(db).isNotNull();
        assertThat(db.get("rows_affected").longValue()).isEqualTo(5);
        assertThat(db.get("instance").textValue()).isEqualTo("test-instance");
        assertThat(db.get("statement").textValue()).isEqualTo("SELECT * FROM TABLE");
    }

    @Test
    void testSpanChildIdSerialization() {
        Id id1 = Id.new64BitId();
        id1.setToRandomValue();
        Id id2 = Id.new64BitId();
        id2.setToRandomValue();
        Span span = new Span(MockTracer.create());
        span.withChildIds(LongList.of(id1.getLeastSignificantBits(), id2.getLeastSignificantBits()));

        JsonNode spanJson = readJsonString(serializer.toJsonString(span));
        JsonNode child_ids = spanJson.get("child_ids");
        assertThat(child_ids.get(0).textValue()).isEqualTo(id1.toString());
        assertThat(child_ids.get(1).textValue()).isEqualTo(id2.toString());
    }

    @Test
    void testInlineReplacement() {
        StringBuilder sb = new StringBuilder("this.is.a.string");
        DslJsonSerializer.replace(sb, ".", "_DOT_", 6);
        assertThat(sb.toString()).isEqualTo("this.is_DOT_a_DOT_string");
    }

    @Test
    void testSerializeMetadata() throws Exception {
        SystemInfo systemInfo = mock(SystemInfo.class);
        SystemInfo.Container container = mock(SystemInfo.Container.class);
        when(container.getId()).thenReturn("container_id");
        when(systemInfo.getContainerInfo()).thenReturn(container);
        SystemInfo.Kubernetes kubernetes = createKubernetesMock("pod", "pod_id", "node", "ns");
        when(systemInfo.getKubernetesInfo()).thenReturn(kubernetes);
        when(systemInfo.getPlatform()).thenReturn("9 3/4"); // this terrible pun is intentional

        Service service = new Service()
            .withAgent(new Agent("MyAgent", "1.11.1"))
            .withName("MyService")
            .withVersion("1.0")
            .withLanguage(new Language("c++", "14"));


        ProcessInfo processInfo = new ProcessInfo("title").withPid(1234);
        processInfo.getArgv().add("test");

        CloudProviderInfo cloudProviderInfo = createCloudProviderInfo();
        serializer = new DslJsonSerializer(
            mock(StacktraceConfiguration.class),
            apmServerClient,
            MetaDataMock.create(processInfo, service, systemInfo, cloudProviderInfo, Map.of("foo", "bar", "עברית", "בדיקה"))
        );
        serializer.blockUntilReady();
        serializer.appendMetaDataNdJsonToStream();
        JsonNode metaDataJson = readJsonString(serializer.toString()).get("metadata");

        JsonNode serviceJson = metaDataJson.get("service");
        assertThat(service).isNotNull();
        assertThat(serviceJson.get("name").textValue()).isEqualTo("MyService");
        assertThat(serviceJson.get("version").textValue()).isEqualTo("1.0");

        JsonNode languageJson = serviceJson.get("language");
        assertThat(languageJson).isNotNull();
        assertThat(languageJson.get("name").asText()).isEqualTo("c++");
        assertThat(languageJson.get("version").asText()).isEqualTo("14");

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
        assertThat(metaDataJson.get("labels").get("עברית").textValue()).isEqualTo("בדיקה");

        JsonNode systemJson = metaDataJson.get("system");
        assertThat(systemJson.get("container").get("id").asText()).isEqualTo("container_id");
        assertThat(systemJson.get("platform").asText()).isEqualTo("9 3/4");

        JsonNode jsonKubernetes = systemJson.get("kubernetes");
        assertThat(jsonKubernetes.get("node").get("name").asText()).isEqualTo("node");
        assertThat(jsonKubernetes.get("pod").get("name").asText()).isEqualTo("pod");
        assertThat(jsonKubernetes.get("pod").get("uid").asText()).isEqualTo("pod_id");
        assertThat(jsonKubernetes.get("namespace").asText()).isEqualTo("ns");

        JsonNode jsonCloud = metaDataJson.get("cloud");
        assertThat(jsonCloud.get("availability_zone").asText()).isEqualTo("availabilityZone");
        assertThat(jsonCloud.get("provider").asText()).isEqualTo("aws");
        assertThat(jsonCloud.get("region").asText()).isEqualTo("region");
        JsonNode jsonCloudAccount = jsonCloud.get("account");
        assertThat(jsonCloudAccount.get("id").asText()).isEqualTo("accountId");
        JsonNode jsonCloudInstance = jsonCloud.get("instance");
        assertThat(jsonCloudInstance.get("id").asText()).isEqualTo("instanceId");
        assertThat(jsonCloudInstance.get("name").asText()).isEqualTo("instanceName");
        JsonNode jsonCloudMachine = jsonCloud.get("machine");
        assertThat(jsonCloudMachine.get("type").asText()).isEqualTo("machineType");
        JsonNode jsonCloudProject = jsonCloud.get("project");
        assertThat(jsonCloudProject.get("id").asText()).isEqualTo("projectId");
        assertThat(jsonCloudProject.get("name").asText()).isEqualTo("projectName");
    }

    @Test
    void testConfiguredServiceNodeName() throws Exception {
        ConfigurationRegistry configRegistry = SpyConfiguration.createSpyConfig();
        when(configRegistry.getConfig(CoreConfiguration.class).getServiceNodeName()).thenReturn("Custom-Node-Name");
        serializer = new DslJsonSerializer(mock(StacktraceConfiguration.class), apmServerClient, MetaData.create(configRegistry, null));
        serializer.blockUntilReady();
        serializer.appendMetaDataNdJsonToStream();
        JsonNode metaDataJson = readJsonString(serializer.toString()).get("metadata");
        JsonNode serviceJson = metaDataJson.get("service");
        assertThat(serviceJson).isNotNull();
        JsonNode nodeJson = serviceJson.get("node");
        assertThat(nodeJson).isNotNull();
        assertThat(nodeJson.get("configured_name").textValue()).isEqualTo("Custom-Node-Name");

    }

    @Test
    void testTransactionContextSerialization() {

        ElasticApmTracer tracer = MockTracer.create();
        Transaction transaction = new Transaction(tracer);

        transaction.getContext().getUser()
            .withId("42")
            .withEmail("user@email.com")
            .withUsername("bob");

        Request request =  transaction.getContext().getRequest();

        request.withMethod("PUT")
            .withHttpVersion("5.0")
            .addCookie("cookie1", "cookie1_value1")
            .addCookie("cookie1", "cookie1_value2")
            .addCookie("cookie2", "cookie2_value")
            .addHeader("my_header", "header value")
            .setRawBody("request body");

        request.getUrl()
            .withHostname("my-hostname")
            .withPathname("/path/name")
            .withPort(42)
            .withProtocol("http")
            .withSearch("q=test");

        request.getSocket()
            .withEncrypted(true)
            .withRemoteAddress("::1");

        transaction.getContext().getResponse()
            .withFinished(true)
            .withHeadersSent(false)
            .addHeader("response_header", "value")
            .withStatusCode(418);

        transaction.getContext().getMessage().withQueue("test_queue").withAge(0);

        TraceContext ctx = transaction.getTraceContext();

        String serviceName = RandomStringUtils.randomAlphabetic(5);
        String frameworkName = RandomStringUtils.randomAlphanumeric(10);
        String frameworkVersion = RandomStringUtils.randomNumeric(3);

        ctx.setServiceName(serviceName);

        transaction.setFrameworkName(frameworkName);
        transaction.setFrameworkVersion(frameworkVersion);

        String jsonString = serializer.toJsonString(transaction);
        JsonNode json = readJsonString(jsonString);

        JsonNode jsonContext = json.get("context");
        assertThat(jsonContext.get("user").get("id").asText()).isEqualTo("42");
        assertThat(jsonContext.get("user").get("email").asText()).isEqualTo("user@email.com");
        assertThat(jsonContext.get("user").get("username").asText()).isEqualTo("bob");
        assertThat(jsonContext.get("service").get("name").asText()).isEqualTo(serviceName);
        assertThat(jsonContext.get("service").get("framework").get("name").asText()).isEqualTo(frameworkName);
        assertThat(jsonContext.get("service").get("framework").get("version").asText()).isEqualTo(frameworkVersion);

        JsonNode jsonRequest = jsonContext.get("request");
        assertThat(jsonRequest.get("method").asText()).isEqualTo("PUT");
        assertThat(jsonRequest.get("body").asText()).isEqualTo("request body");
        JsonNode jsonCookies = jsonRequest.get("cookies");
        assertThat(jsonCookies).hasSize(2);
        assertThat(jsonCookies.get("cookie1").get(0).asText()).isEqualTo("cookie1_value1");
        assertThat(jsonCookies.get("cookie1").get(1).asText()).isEqualTo("cookie1_value2");
        assertThat(jsonCookies.get("cookie2").asText()).isEqualTo("cookie2_value");

        assertThat(jsonRequest.get("headers").get("my_header").asText()).isEqualTo("header value");

        JsonNode jsonUrl = jsonRequest.get("url");
        assertThat(jsonUrl).hasSize(6);
        assertThat(jsonUrl.get("hostname").asText()).isEqualTo("my-hostname");
        assertThat(jsonUrl.get("port").asText()).isEqualTo("42");
        assertThat(jsonUrl.get("pathname").asText()).isEqualTo("/path/name");
        assertThat(jsonUrl.get("search").asText()).isEqualTo("q=test");
        assertThat(jsonUrl.get("protocol").asText()).isEqualTo("http");
        assertThat(jsonUrl.get("full").asText()).isEqualTo("http://my-hostname:42/path/name?q=test");

        JsonNode jsonSocket = jsonRequest.get("socket");
        assertThat(jsonSocket).hasSize(2);
        assertThat(jsonSocket.get("encrypted").asBoolean()).isTrue();
        assertThat(jsonSocket.get("remote_address").asText()).isEqualTo("::1");

        JsonNode jsonResponse = jsonContext.get("response");
        assertThat(jsonResponse).hasSize(4);
        assertThat(jsonResponse.get("headers").get("response_header").asText()).isEqualTo("value");
        assertThat(jsonResponse.get("finished").asBoolean()).isTrue();
        assertThat(jsonResponse.get("headers_sent").asBoolean()).isFalse();
        assertThat(jsonResponse.get("status_code").asInt()).isEqualTo(418);

        JsonNode message = jsonContext.get("message");
        assertThat(message).isNotNull();
        JsonNode topic = message.get("topic");
        assertThat(topic).isNull();
        JsonNode queue = message.get("queue");
        assertThat(queue).isNotNull();
        assertThat("test_queue").isEqualTo(queue.get("name").textValue());
        JsonNode age = message.get("age");
        assertThat(age).isNotNull();
        JsonNode ms = age.get("ms");
        assertThat(ms).isNotNull();
        assertThat(ms.longValue()).isEqualTo(0);
    }

    @Test
    void testBodyBuffer() throws IOException {
        final Transaction transaction = createRootTransaction();
        Request request = transaction.getContext().getRequest();
        final CharBuffer bodyBuffer = request.withBodyBuffer();
        IOUtils.decodeUtf8Bytes("{f".getBytes(StandardCharsets.UTF_8), bodyBuffer);
        IOUtils.decodeUtf8Bytes(new byte[]{0, 0, 'o', 'o', 0}, 2, 2, bodyBuffer);
        IOUtils.decodeUtf8Byte((byte) '}', bodyBuffer);
        request.endOfBufferInput();
        final String content = serializer.toJsonString(transaction);
        System.out.println(content);
        final JsonNode transactionJson = objectMapper.readTree(content);
        assertThat(transactionJson.get("context").get("request").get("body").textValue()).isEqualTo("{foo}");

        transaction.resetState();
        assertThat((Object) request.getBodyBuffer()).isNull();
    }

    /**
     * Tests that body not properly finished (not properly flipped) is ignored from serialization
     * @throws IOException indicates failure in deserialization
     */
    @Test
    void testNonFlippedTransactionBodyBuffer() throws IOException {
        final Transaction transaction = createRootTransaction();
        Request request = transaction.getContext().getRequest();
        request.withBodyBuffer().append("TEST");
        final String content = serializer.toJsonString(transaction);
        System.out.println(content);
        final JsonNode transactionJson = objectMapper.readTree(content);
        assertThat(transactionJson.get("context").get("request").get("body")).isNull();
    }

    @Test
    void testBodyBufferCopy() throws IOException {
        final Transaction transaction = createRootTransaction();
        Request request = transaction.getContext().getRequest();
        final CharBuffer bodyBuffer = request.withBodyBuffer();
        IOUtils.decodeUtf8Bytes("{foo}".getBytes(StandardCharsets.UTF_8), bodyBuffer);
        request.endOfBufferInput();

        Transaction copy = createRootTransaction();
        copy.getContext().copyFrom(transaction.getContext());

        assertThat(objectMapper.readTree(serializer.toJsonString(copy)).get("context"))
            .isEqualTo(objectMapper.readTree(serializer.toJsonString(transaction)).get("context"));
    }

    @Test
    void testCustomContext() throws Exception {
        final Transaction transaction = createRootTransaction();
        transaction.addCustomContext("string", "foo");
        final String longString = RandomStringUtils.randomAlphanumeric(10001);
        transaction.addCustomContext("long_string", longString);
        transaction.addCustomContext("number", 42);
        transaction.addCustomContext("boolean", true);

        final JsonNode customContext = objectMapper.readTree(serializer.toJsonString(transaction)).get("context").get("custom");
        assertThat(customContext.get("string").textValue()).isEqualTo("foo");
        assertThat(customContext.get("long_string").textValue()).isEqualTo(longString.substring(0, 9999) + "…");
        assertThat(customContext.get("number").intValue()).isEqualTo(42);
        assertThat(customContext.get("boolean").booleanValue()).isEqualTo(true);
    }

    @Test
    void testJsonSchemaDslJsonEmptyValues() throws IOException {
        Transaction transaction = new Transaction(MockTracer.create());
        final String content = serializer.toJsonString(transaction);
        System.out.println(content);
        JsonNode transactionNode = objectMapper.readTree(content);
        assertThat(transactionNode.get("timestamp").asLong()).isEqualTo(0);
        assertThat(transactionNode.get("duration").asDouble()).isEqualTo(0.0);
        assertThat(transactionNode.get("context").get("tags")).isEmpty();
        assertThat(transactionNode.get("sampled").asBoolean()).isEqualTo(false);
        assertThat(transactionNode.get("span_count").get("dropped").asInt()).isEqualTo(0);
        assertThat(transactionNode.get("span_count").get("started").asInt()).isEqualTo(0);
    }

    @Test
    void testSystemInfo() throws Exception {
        String arc = System.getProperty("os.arch");
        String platform = System.getProperty("os.name");
        String hostname = SystemInfo.getNameOfLocalHost();

        MetaData metaData = createMetaData();
        DslJsonSerializer.serializeMetadata(metaData, serializer.getJsonWriter());
        serializer.appendMetadataToStream();

        JsonNode system = readJsonString(serializer.toString()).get("system");

        assertThat(arc).isEqualTo(system.get("architecture").asText());
        assertThat(hostname).isEqualTo(system.get("hostname").asText());
        assertThat(platform).isEqualTo(system.get("platform").asText());
    }

    @Test
    void testCloudProviderInfoWithNullObjectFields() throws Exception {
        MetaData metaData = createMetaData();
        CloudProviderInfo cloudProviderInfo = Objects.requireNonNull(metaData.getCloudProviderInfo());
        cloudProviderInfo.setAccount(null);
        cloudProviderInfo.setMachine(null);
        cloudProviderInfo.setProject(null);
        cloudProviderInfo.setInstance(null);

        DslJsonSerializer.serializeMetadata(metaData, serializer.getJsonWriter());
        serializer.appendMetadataToStream();

        JsonNode jsonCloud = readJsonString(serializer.toString()).get("cloud");

        assertThat(jsonCloud.get("availability_zone").asText()).isEqualTo("availabilityZone");
        assertThat(jsonCloud.get("provider").asText()).isEqualTo("aws");
        assertThat(jsonCloud.get("region").asText()).isEqualTo("region");
        JsonNode jsonCloudAccount = jsonCloud.get("account");
        assertThat(jsonCloudAccount).isNull();
        JsonNode jsonCloudInstance = jsonCloud.get("instance");
        assertThat(jsonCloudInstance).isNull();
        JsonNode jsonCloudMachine = jsonCloud.get("machine");
        assertThat(jsonCloudMachine).isNull();
        JsonNode jsonCloudProject = jsonCloud.get("project");
        assertThat(jsonCloudProject).isNull();
    }

    @Test
    void testCloudProviderInfoWithNullNameFields() throws Exception {
        MetaData metaData = createMetaData();
        CloudProviderInfo cloudProviderInfo = Objects.requireNonNull(metaData.getCloudProviderInfo());
        Objects.requireNonNull(cloudProviderInfo.getProject()).setName(null);
        Objects.requireNonNull(cloudProviderInfo.getInstance()).setName(null);

        DslJsonSerializer.serializeMetadata(metaData, serializer.getJsonWriter());
        serializer.appendMetadataToStream();

        JsonNode jsonCloud = readJsonString(serializer.toString()).get("cloud");

        assertThat(jsonCloud.get("availability_zone").asText()).isEqualTo("availabilityZone");
        assertThat(jsonCloud.get("provider").asText()).isEqualTo("aws");
        assertThat(jsonCloud.get("region").asText()).isEqualTo("region");
        JsonNode jsonCloudAccount = jsonCloud.get("account");
        assertThat(jsonCloudAccount.get("id").asText()).isEqualTo("accountId");
        JsonNode jsonCloudInstance = jsonCloud.get("instance");
        assertThat(jsonCloudInstance.get("id").asText()).isEqualTo("instanceId");
        // APM Server 7.9.x does not allow sending null fields
        assertThat(jsonCloudInstance.get("name")).isNull();
        JsonNode jsonCloudMachine = jsonCloud.get("machine");
        assertThat(jsonCloudMachine.get("type").asText()).isEqualTo("machineType");
        JsonNode jsonCloudProject = jsonCloud.get("project");
        assertThat(jsonCloudProject.get("id").asText()).isEqualTo("projectId");
        // APM Server 7.9.x does not allow sending null fields
        assertThat(jsonCloudProject.get("name")).isNull();
    }

    @Test
    void testCloudProviderInfoWithNullNameAndIdFields() throws Exception {
        MetaData metaData = createMetaData();
        CloudProviderInfo cloudProviderInfo = Objects.requireNonNull(metaData.getCloudProviderInfo());
        CloudProviderInfo.NameAndIdField project = Objects.requireNonNull(cloudProviderInfo.getProject());
        project.setName(null);
        project.setId(null);
        CloudProviderInfo.NameAndIdField instance = Objects.requireNonNull(cloudProviderInfo.getInstance());
        instance.setName(null);
        instance.setId(null);

        DslJsonSerializer.serializeMetadata(metaData, serializer.getJsonWriter());
        serializer.appendMetadataToStream();

        JsonNode jsonCloud = readJsonString(serializer.toString()).get("cloud");

        assertThat(jsonCloud.get("availability_zone").asText()).isEqualTo("availabilityZone");
        assertThat(jsonCloud.get("provider").asText()).isEqualTo("aws");
        assertThat(jsonCloud.get("region").asText()).isEqualTo("region");
        JsonNode jsonCloudAccount = jsonCloud.get("account");
        assertThat(jsonCloudAccount.get("id").asText()).isEqualTo("accountId");
        JsonNode jsonCloudMachine = jsonCloud.get("machine");
        assertThat(jsonCloudMachine.get("type").asText()).isEqualTo("machineType");
        assertThat(jsonCloud.get("instance")).isNull();
        assertThat(jsonCloud.get("project")).isNull();
    }

    @Test
    void testCloudProviderInfoWithNullIdFields() throws Exception {
        MetaData metaData = createMetaData();
        CloudProviderInfo cloudProviderInfo = Objects.requireNonNull(metaData.getCloudProviderInfo());
        Objects.requireNonNull(cloudProviderInfo.getProject()).setId(null);
        Objects.requireNonNull(cloudProviderInfo.getInstance()).setId(null);
        Objects.requireNonNull(cloudProviderInfo.getAccount()).setId(null);

        DslJsonSerializer.serializeMetadata(metaData, serializer.getJsonWriter());
        serializer.appendMetadataToStream();

        JsonNode jsonCloud = readJsonString(serializer.toString()).get("cloud");

        assertThat(jsonCloud.get("availability_zone").asText()).isEqualTo("availabilityZone");
        assertThat(jsonCloud.get("provider").asText()).isEqualTo("aws");
        assertThat(jsonCloud.get("region").asText()).isEqualTo("region");
        // Currently account only has ID (although the intake API allows name as well), so it should not be in the JSON
        assertThat(jsonCloud.get("account")).isNull();
        JsonNode jsonCloudInstance = jsonCloud.get("instance");
        assertThat(jsonCloudInstance.get("id")).isNull();
        assertThat(jsonCloudInstance.get("name").asText()).isEqualTo("instanceName");
        JsonNode jsonCloudMachine = jsonCloud.get("machine");
        assertThat(jsonCloudMachine.get("type").asText()).isEqualTo("machineType");
        JsonNode jsonCloudProject = jsonCloud.get("project");
        assertThat(jsonCloudProject.get("id")).isNull();
        assertThat(jsonCloudProject.get("name").asText()).isEqualTo("projectName");
    }

    private MetaData createMetaData() throws Exception {
        return createMetaData(SystemInfo.create());
    }

    private MetaData createMetaData(SystemInfo system) throws Exception {
        Service service = new Service().withAgent(new Agent("name", "version")).withName("name");
        final ProcessInfo processInfo = new ProcessInfo("title");
        processInfo.getArgv().add("test");
        return MetaDataMock.create(processInfo, service, system, createCloudProviderInfo(), new HashMap<>(0)).get();
    }

    private CloudProviderInfo createCloudProviderInfo() {
        CloudProviderInfo cloudProviderInfo = new CloudProviderInfo("aws");
        cloudProviderInfo.setMachine(new CloudProviderInfo.ProviderMachine("machineType"));
        cloudProviderInfo.setInstance(new CloudProviderInfo.NameAndIdField("instanceName", "instanceId"));
        cloudProviderInfo.setAvailabilityZone("availabilityZone");
        cloudProviderInfo.setAccount(new CloudProviderInfo.ProviderAccount("accountId"));
        cloudProviderInfo.setRegion("region");
        cloudProviderInfo.setProject(new CloudProviderInfo.NameAndIdField("projectName", "projectId"));
        return cloudProviderInfo;
    }

    private Transaction createRootTransaction(Sampler sampler) {
        Transaction t = new Transaction(MockTracer.create());
        t.start(TraceContext.asRoot(), null, 0, sampler, getClass().getClassLoader());
        t.withType("type");
        t.getContext().getRequest().withMethod("GET");
        t.getContext().getRequest().getUrl().withFull("http://localhost:8080/foo/bar");
        return t;
    }

    private Transaction createRootTransaction() {
        return createRootTransaction(ConstantSampler.of(true));
    }

    @Test
    void testSpanStackFrameSerialization() {
        Span span = new Span(MockTracer.create());
        span.setStackTrace(Arrays.asList(StackFrame.of("foo.Bar", "baz"), StackFrame.of("foo.Bar$Baz", "qux")));

        JsonNode spanJson = readJsonString(serializer.toJsonString(span));
        JsonNode jsonStackTrace = spanJson.get("stacktrace");
        assertThat(jsonStackTrace.getNodeType()).isEqualTo(JsonNodeType.ARRAY);
        assertThat(jsonStackTrace).isNotNull();
        assertThat(jsonStackTrace).hasSize(2);

        assertThat(jsonStackTrace.get(0).get("filename").textValue()).isEqualTo("Bar.java");
        assertThat(jsonStackTrace.get(0).get("function").textValue()).isEqualTo("baz");
        assertThat(jsonStackTrace.get(0).get("library_frame").booleanValue()).isTrue();
        assertThat(jsonStackTrace.get(0).get("lineno").intValue()).isEqualTo(-1);
        assertThat(jsonStackTrace.get(0).get("module")).isNull();

        assertThat(jsonStackTrace.get(1).get("filename").textValue()).isEqualTo("Bar.java");
        assertThat(jsonStackTrace.get(1).get("function").textValue()).isEqualTo("qux");
        assertThat(jsonStackTrace.get(1).get("library_frame").booleanValue()).isTrue();
        assertThat(jsonStackTrace.get(1).get("lineno").intValue()).isEqualTo(-1);
        assertThat(jsonStackTrace.get(1).get("module")).isNull();
    }

    @Test
    void testSampledRootTransaction() {
        // take sampler rate when sampled
        testRootTransactionSampleRate(true, 0.5d, 0.5d);

        // take zero when not sampled
        testRootTransactionSampleRate(false, 1.0d, 0d);
    }

    private void testRootTransactionSampleRate(boolean sampled, double samplerRate, @Nullable Double expectedRate){
        Sampler sampler = mock(Sampler.class);
        when(sampler.isSampled(any(Id.class))).thenReturn(sampled);
        when(sampler.getSampleRate()).thenReturn(samplerRate);

        Transaction transaction = createRootTransaction(sampler);

        JsonNode jsonTransaction = readJsonString(serializer.toJsonString(transaction));

        JsonNode jsonSampleRate = jsonTransaction.get("sample_rate");
        JsonNode jsonSampled = jsonTransaction.get("sampled");
        assertThat(jsonSampled.asBoolean()).isEqualTo(sampled);
        if(null == expectedRate){
            assertThat(jsonSampleRate).isNull();
        } else {
            assertThat(jsonSampleRate.asDouble()).isEqualTo(expectedRate);
        }
    }

    @Test
    void testSampledSpan_rateFromParent() {

        Sampler sampler = mock(Sampler.class);
        when(sampler.isSampled(any(Id.class))).thenReturn(true);
        when(sampler.getSampleRate()).thenReturn(0.42d);

        Transaction transaction = createRootTransaction(sampler);
        TraceContext transactionContext = transaction.getTraceContext();
        assertThat(transactionContext.isSampled()).isTrue();
        assertThat(transactionContext.getSampleRate()).isEqualTo(0.42d);

        Span span = new Span(MockTracer.create());
        span.getTraceContext().asChildOf(transactionContext);

        JsonNode jsonSpan = readJsonString(serializer.toJsonString(span));

        assertThat(jsonSpan.get("sample_rate").asDouble()).isEqualTo(0.42d);
    }

    private JsonNode readJsonString(String jsonString) {
        try {
            JsonNode json = objectMapper.readTree(jsonString);

            // pretty print JSON in standard output for easier test debug
            System.out.println(json.toPrettyString());

            return json;
        } catch (JsonProcessingException e) {
            // any invalid JSON will require debugging the raw string
            throw new IllegalArgumentException("invalid JSON = "+jsonString);
        }
    }

    private static SystemInfo.Kubernetes createKubernetesMock(String podName, String podId, String nodeName, String namespace) {
        SystemInfo.Kubernetes k = mock(SystemInfo.Kubernetes.class);

        when(k.hasContent()).thenReturn(true);

        SystemInfo.Kubernetes.Pod pod = mock(SystemInfo.Kubernetes.Pod.class);
        when(pod.getName()).thenReturn(podName);
        when(pod.getUid()).thenReturn(podId);

        when(k.getPod()).thenReturn(pod);

        SystemInfo.Kubernetes.Node node = mock( SystemInfo.Kubernetes.Node.class);
        when(node.getName()).thenReturn(nodeName);
        when(k.getNode()).thenReturn(node);

        when(k.getNamespace()).thenReturn(namespace);

        return k;
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
