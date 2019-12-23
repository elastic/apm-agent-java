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
import co.elastic.apm.agent.impl.context.Request;
import co.elastic.apm.agent.impl.error.ErrorCapture;
import co.elastic.apm.agent.impl.payload.Agent;
import co.elastic.apm.agent.impl.payload.Framework;
import co.elastic.apm.agent.impl.payload.Language;
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
    void testErrorSerialization() {
        ElasticApmTracer tracer = MockTracer.create();
        Transaction transaction = new Transaction(tracer);
        ErrorCapture error = new ErrorCapture(tracer).asChildOf(transaction.getTraceContext()).withTimestamp(5000);
        error.setTransactionSampled(true);
        error.setTransactionType("test-type");
        error.setException(new Exception("test"));
        error.getContext().addLabel("foo", "bar");

        JsonNode errorTree = readJsonString(serializer.toJsonString(error));

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
    void testErrorSerializationOutsideTrace() {
        MockReporter reporter = new MockReporter();
        ElasticApmTracer tracer = MockTracer.createRealTracer(reporter);
        tracer.captureException(new Exception("test"), getClass().getClassLoader());

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
    void testErrorSerializationWithExceptionCause() throws JsonProcessingException {
        // testing outside trace is enough to test exception serialization logic
        MockReporter reporter = new MockReporter();
        ElasticApmTracer tracer = MockTracer.createRealTracer(reporter);

        Exception cause2 = new IllegalStateException("second cause");
        Exception cause1 = new RuntimeException("first cause", cause2);
        Exception mainException = new Exception("main exception", cause1);

        tracer.captureException(mainException, getClass().getClassLoader());

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
    void testNullHeaders() {
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
            .withTopic("test-topic")
            .withBody("test-body")
            .addHeader("test-header1", "value")
            .addHeader("test-header2", "value")
            .withAge(20);

        JsonNode spanJson = readJsonString(serializer.toJsonString(span));
        JsonNode context = spanJson.get("context");
        JsonNode message = context.get("message");
        assertThat(message).isNotNull();
        JsonNode queue = message.get("queue");
        assertThat(queue).isNull();
        JsonNode topic = message.get("topic");
        assertThat(topic).isNotNull();
        assertThat("test-topic").isEqualTo(topic.get("name").textValue());
        JsonNode body = message.get("body");
        assertThat("test-body").isEqualTo(body.textValue());
        JsonNode headers = message.get("headers");
        assertThat(headers).isNotNull();
        assertThat(headers.get("test-header1").textValue()).isEqualTo("value");
        assertThat(headers.get("test-header2").textValue()).isEqualTo("value");
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
    void testInlineReplacement() {
        StringBuilder sb = new StringBuilder("this.is.a.string");
        DslJsonSerializer.replace(sb, ".", "_DOT_", 6);
        assertThat(sb.toString()).isEqualTo("this.is_DOT_a_DOT_string");
    }

    @Test
    void testSerializeMetadata() {

        Framework framework = mock(Framework.class);
        when(framework.getName()).thenReturn("awesome");
        when(framework.getVersion()).thenReturn("0.0.1-alpha");

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
            .withFramework(framework)
            .withLanguage(new Language("c++", "14"));


        ProcessInfo processInfo = new ProcessInfo("title").withPid(1234);
        processInfo.getArgv().add("test");

        serializer.serializeMetaDataNdJson(new MetaData(processInfo, service, systemInfo, Map.of("foo", "bar", "baz", "qux")));

        JsonNode metaDataJson = readJsonString(serializer.toString()).get("metadata");

        JsonNode serviceJson = metaDataJson.get("service");
        assertThat(service).isNotNull();
        assertThat(serviceJson.get("name").textValue()).isEqualTo("MyService");
        assertThat(serviceJson.get("version").textValue()).isEqualTo("1.0");

        JsonNode frameworkJson = serviceJson.get("framework");
        assertThat(frameworkJson).isNotNull();
        assertThat(frameworkJson.get("name").asText()).isEqualTo("awesome");
        assertThat(frameworkJson.get("version").asText()).isEqualTo("0.0.1-alpha");

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
        assertThat(metaDataJson.get("labels").get("baz").textValue()).isEqualTo("qux");

        JsonNode systemJson = metaDataJson.get("system");
        assertThat(systemJson.get("container").get("id").asText()).isEqualTo("container_id");
        assertThat(systemJson.get("platform").asText()).isEqualTo("9 3/4");

        JsonNode jsonKubernetes = systemJson.get("kubernetes");
        assertThat(jsonKubernetes.get("node").get("name").asText()).isEqualTo("node");
        assertThat(jsonKubernetes.get("pod").get("name").asText()).isEqualTo("pod");
        assertThat(jsonKubernetes.get("pod").get("uid").asText()).isEqualTo("pod_id");
        assertThat(jsonKubernetes.get("namespace").asText()).isEqualTo("ns");
    }

    @Test
    void testConfiguredServiceNodeName() {
        ConfigurationRegistry configRegistry = SpyConfiguration.createSpyConfig();
        when(configRegistry.getConfig(CoreConfiguration.class).getServiceNodeName()).thenReturn("Custom-Node-Name");
        MetaData metaData = MetaData.create(configRegistry, null, null);
        serializer.serializeMetaDataNdJson(metaData);
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

        String jsonString = serializer.toJsonString(transaction);
        JsonNode json = readJsonString(jsonString);

        JsonNode jsonContext = json.get("context");
        assertThat(jsonContext.get("user").get("id").asText()).isEqualTo("42");
        assertThat(jsonContext.get("user").get("email").asText()).isEqualTo("user@email.com");
        assertThat(jsonContext.get("user").get("username").asText()).isEqualTo("bob");

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
        assertThat(jsonUrl).hasSize(5);
        assertThat(jsonUrl.get("hostname").asText()).isEqualTo("my-hostname");
        assertThat(jsonUrl.get("port").asText()).isEqualTo("42");
        assertThat(jsonUrl.get("pathname").asText()).isEqualTo("/path/name");
        assertThat(jsonUrl.get("search").asText()).isEqualTo("q=test");
        assertThat(jsonUrl.get("protocol").asText()).isEqualTo("http");

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
