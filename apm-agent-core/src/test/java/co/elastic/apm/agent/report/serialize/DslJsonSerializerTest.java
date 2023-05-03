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
import co.elastic.apm.agent.configuration.ServerlessConfiguration;
import co.elastic.apm.agent.configuration.SpyConfiguration;
import co.elastic.apm.agent.impl.BinaryHeaderMapAccessor;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.TextHeaderMapAccessor;
import co.elastic.apm.agent.impl.Tracer;
import co.elastic.apm.agent.impl.context.AbstractContext;
import co.elastic.apm.agent.impl.context.Destination;
import co.elastic.apm.agent.impl.context.Headers;
import co.elastic.apm.agent.impl.context.Request;
import co.elastic.apm.agent.impl.context.Url;
import co.elastic.apm.agent.impl.error.ErrorCapture;
import co.elastic.apm.agent.impl.metadata.Agent;
import co.elastic.apm.agent.impl.metadata.CloudProviderInfo;
import co.elastic.apm.agent.impl.metadata.FaaSMetaDataExtension;
import co.elastic.apm.agent.impl.metadata.Framework;
import co.elastic.apm.agent.impl.metadata.Language;
import co.elastic.apm.agent.impl.metadata.MetaData;
import co.elastic.apm.agent.impl.metadata.MetaDataMock;
import co.elastic.apm.agent.impl.metadata.NameAndIdField;
import co.elastic.apm.agent.impl.metadata.ProcessInfo;
import co.elastic.apm.agent.impl.metadata.Service;
import co.elastic.apm.agent.impl.metadata.SystemInfo;
import co.elastic.apm.agent.impl.sampling.ConstantSampler;
import co.elastic.apm.agent.impl.sampling.Sampler;
import co.elastic.apm.agent.impl.stacktrace.StacktraceConfiguration;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Id;
import co.elastic.apm.agent.impl.transaction.OTelSpanKind;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
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
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;


class DslJsonSerializerTest {

    private DslJsonSerializer.Writer writer;
    private ObjectMapper objectMapper;
    private ApmServerClient apmServerClient;
    private Future<MetaData> metaData;
    private ElasticApmTracer tracer;

    @BeforeEach
    void setUp() throws Exception {
        StacktraceConfiguration stacktraceConfiguration = mock(StacktraceConfiguration.class);
        doReturn(15).when(stacktraceConfiguration).getStackTraceLimit();
        apmServerClient = mock(ApmServerClient.class);
        metaData = MetaDataMock.create();
        objectMapper = new ObjectMapper();
        tracer = MockTracer.createRealTracer();
        SerializationConstants.init(tracer.getConfig(CoreConfiguration.class));

        DslJsonSerializer serializer = new DslJsonSerializer(stacktraceConfiguration, apmServerClient, metaData);
        writer = serializer.newWriter();
        writer.blockUntilReady();
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

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testSerializeNonStringLabels(boolean numericLabels) {
        doReturn(numericLabels).when(apmServerClient).supportsNonStringLabels();

        Map<String, Object> expectedMap;
        if (numericLabels) {
            expectedMap = Map.of("foo", true);
        } else {
            expectedMap = Collections.singletonMap("foo", null);
        }
        assertThat(serializeTags(Map.of("foo", true))).isEqualTo(toJson(expectedMap));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testSerializeUrlPort(boolean useNumericPort) {

        doReturn(useNumericPort).when(apmServerClient).supportsNumericUrlPort();

        Url url = new Url()
            .withPort(42)
            .withHostname("hostname")
            .withProtocol("http")
            .withPathname("/hello").withSearch("search");
        writer.serializeUrl(url);
        JsonNode json = readJsonString(getAndResetSerializerJson());
        if (useNumericPort) {
            assertThat(json.get("port").asInt()).isEqualTo(42);
        } else {
            assertThat(json.get("port").asText()).isEqualTo("42");
        }
        assertThat(json.get("full").asText()).isEqualTo("http://hostname:42/hello?search");
        assertThat(json.get("search").asText()).isEqualTo("search");
        assertThat(json.get("protocol").asText()).isEqualTo("http");
    }

    @Test
    void testErrorSerialization() {
        Transaction transaction = new Transaction(tracer);
        transaction.startRoot(-1, ConstantSampler.of(true));
        ErrorCapture error = new ErrorCapture(tracer).asChildOf(transaction).withTimestamp(5000);
        error.setTransactionSampled(true);
        error.setTransactionType("test-type");
        error.setTransactionName(new StringBuilder("Test Transaction"));
        error.setException(new Exception("test"));
        error.getContext().addLabel("foo", "bar");

        JsonNode errorTree = readJsonString(writer.toJsonString(error));

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

        JsonNode transactionTree = errorTree.get("transaction");
        assertThat(transactionTree.get("sampled").booleanValue()).isTrue();
        assertThat(transactionTree.get("type").textValue()).isEqualTo("test-type");
        assertThat(transactionTree.get("name").asText()).isEqualTo("Test Transaction");
    }

    @Test
    void testErrorSerializationAllFrames() {
        StacktraceConfiguration stacktraceConfiguration = mock(StacktraceConfiguration.class);
        doReturn(-1).when(stacktraceConfiguration).getStackTraceLimit();
        writer = new DslJsonSerializer(stacktraceConfiguration, apmServerClient, metaData).newWriter();

        ErrorCapture error = new ErrorCapture(tracer).withTimestamp(5000);
        Exception exception = new Exception("test");
        error.setException(exception);

        JsonNode errorTree = readJsonString(writer.toJsonString(error));
        JsonNode stacktrace = checkException(errorTree.get("exception"), Exception.class, "test").get("stacktrace");
        assertThat(stacktrace).hasSizeGreaterThan(15);
    }

    @Test
    void testErrorSerializationWithEmptyTraceId() {
        Transaction transaction = new Transaction(tracer);
        transaction.startRoot(-1, ConstantSampler.of(true));
        transaction.getTraceContext().getTraceId().resetState();
        ErrorCapture error = new ErrorCapture(tracer).asChildOf(transaction).withTimestamp(5000);

        JsonNode errorTree = readJsonString(writer.toJsonString(error));

        assertThat(errorTree.get("id")).isNotNull();
        assertThat(errorTree.get("timestamp").longValue()).isEqualTo(5000);

        // Verify the limitation of not sending an Error event with parent_id and/or transaction_id without trace_id
        assertThat(errorTree.get("trace_id")).isNull();
        assertThat(errorTree.get("parent_id")).isNull();
        assertThat(errorTree.get("transaction_id")).isNull();

        JsonNode transactionTree = errorTree.get("transaction");
        assertThat(transactionTree.get("sampled").booleanValue()).isFalse();
        assertThat(transactionTree.get("type")).isNull();
        assertThat(transactionTree.get("name")).isNull();
    }

    @Test
    void testErrorSerializationOutsideTrace() {
        MockReporter reporter = new MockReporter();
        Tracer tracer = MockTracer.createRealTracer(reporter);
        tracer.captureAndReportException(new Exception("test"), getClass().getClassLoader());

        String errorJson = writer.toJsonString(reporter.getFirstError());
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

        JsonNode errorTree = readJsonString(writer.toJsonString(reporter.getFirstError()));

        JsonNode exception = checkException(errorTree.get("exception"), Exception.class, "main exception");

        JsonNode firstCause = checkExceptionCause(exception, RuntimeException.class, "first cause");
        checkExceptionCause(firstCause, IllegalStateException.class, "second cause");

    }

    private static JsonNode checkExceptionCause(JsonNode exception, Class<?> expectedType, String expectedMessage) {
        JsonNode causeArray = exception.get("cause");
        assertThat(causeArray.getNodeType())
            .describedAs("cause should be an array")
            .isEqualTo(JsonNodeType.ARRAY);
        assertThat(causeArray).hasSize(1);

        return checkException(causeArray.get(0), expectedType, expectedMessage);
    }

    private static JsonNode checkException(JsonNode jsonException, Class<?> expectedType, String expectedMessage) {
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
        int maxValueLength = SerializationConstants.MAX_VALUE_LENGTH;
        int maxLongValueLength = SerializationConstants.getMaxLongStringValueLength();
        StringBuilder longValue = new StringBuilder(maxValueLength + 1);
        for (int i = 0; i < maxValueLength + 1; i++) {
            longValue.append('0');
        }

        StringBuilder longStringValue = new StringBuilder(maxLongValueLength + 1);
        for (int i = 0; i < maxLongValueLength + 1; i++) {
            longStringValue.append('0');
        }
        writer.jw.writeByte(JsonWriter.OBJECT_START);
        writer.writeField("string", longValue.toString());
        writer.writeField("stringBuilder", longValue);
        writer.writeLongStringField("longString", longStringValue.toString());
        writer.writeLastField("lastString", longValue.toString());
        writer.jw.writeByte(JsonWriter.OBJECT_END);
        final JsonNode jsonNode = objectMapper.readTree(writer.jw.toString());
        assertThat(jsonNode.get("stringBuilder").textValue()).hasSize(maxValueLength).endsWith("…");
        assertThat(jsonNode.get("string").textValue()).hasSize(maxValueLength).endsWith("…");
        assertThat(jsonNode.get("longString").textValue()).hasSize(maxLongValueLength).endsWith("…");
        assertThat(jsonNode.get("lastString").textValue()).hasSize(maxValueLength).endsWith("…");
    }

    @Test
    void testNullTransactionHeaders() {
        Transaction transaction = new Transaction(tracer);
        transaction.getContext().getRequest().addHeader("foo", (String) null);
        transaction.getContext().getRequest().addHeader("baz", (Enumeration<String>) null);
        transaction.getContext().getRequest().getHeaders().add("bar", null);
        JsonNode jsonNode = readJsonString(writer.toJsonString(transaction));
        // calling addHeader with a null value ignores the header
        assertThat(jsonNode.get("context").get("request").get("headers").get("foo")).isNull();
        assertThat(jsonNode.get("context").get("request").get("headers").get("baz")).isNull();
        // should a null value sneak in, it should not break
        assertThat(jsonNode.get("context").get("request").get("headers").get("bar").isNull()).isTrue();
    }

    @Test
    void testMessageHeaders() {
        Span span = new Span(tracer);
        span.getTraceContext().asRootSpan(ConstantSampler.of(true));
        span.withType("messaging").withSubtype("kafka");

        Headers headers = span.getContext().getMessage().getHeaders();

        headers.add("null-string-value", (String) null);
        headers.add("string-value", "as-is");

        headers.add("null-binary-value", (byte[]) null);
        headers.add("binary-value", "binary-value".getBytes(StandardCharsets.UTF_8));

        JsonNode jsonNode = readJsonString(writer.toJsonString(span));
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
        Span span = new Span(tracer);
        span.getTraceContext().asRootSpan(ConstantSampler.of(true));
        span.withType("template.jsf.render.view");
        JsonNode spanJson = readJsonString(writer.toJsonString(span));
        assertThat(spanJson.get("type").textValue()).isEqualTo("template_jsf_render_view");
        JsonNode context = spanJson.get("context");
        assertThat(context).isNotNull();
        assertThat(context.get("message")).isNull();
        assertThat(context.get("db")).isNull();
        assertThat(context.get("http")).isNull();

        span.withType("template").withSubtype("jsf.lifecycle").withAction("render.view");
        spanJson = readJsonString(writer.toJsonString(span));
        assertThat(spanJson.get("type").textValue()).isEqualTo("template.jsf_lifecycle.render_view");

        span = new Span(tracer);
        span.getTraceContext().asRootSpan(ConstantSampler.of(true));
        span.withType("template").withAction("jsf.render");
        spanJson = readJsonString(writer.toJsonString(span));
        assertThat(spanJson.get("type").textValue()).isEqualTo("template..jsf_render");

        span = new Span(tracer);
        span.getTraceContext().asRootSpan(ConstantSampler.of(true));
        span.withType("template").withSubtype("jsf.render");
        spanJson = readJsonString(writer.toJsonString(span));
        assertThat(spanJson.get("type").textValue()).isEqualTo("template.jsf_render");

        span = new Span(tracer);
        span.getTraceContext().asRootSpan(ConstantSampler.of(true));
        span.withSubtype("jsf").withAction("render");
        spanJson = readJsonString(writer.toJsonString(span));
        assertThat(spanJson.get("type").isNull()).isTrue();
    }

    @Test
    void testSpanHttpContextSerialization() {
        Span span = new Span(tracer);
        span.getContext().getHttp()
            .withMethod("GET")
            .withStatusCode(523)
            .withUrl("http://whatever.com/path");

        JsonNode spanJson = readJsonString(writer.toJsonString(span));
        JsonNode context = spanJson.get("context");
        JsonNode http = context.get("http");
        assertThat(http).isNotNull();
        assertThat(http.get("method").textValue()).isEqualTo("GET");
        assertThat(http.get("url").textValue()).isEqualTo("http://whatever.com/path");
        assertThat(http.get("status_code").intValue()).isEqualTo(523);
    }

    public static boolean[][] getContentCombinations() {
        return new boolean[][]{
            {true, true, true, true},
            {true, false, true, true},
            {false, true, true, true},
            {false, true, false, true},
            {false, false, true, true},
            {false, false, true, false},
            {false, false, false, true},
            {false, false, false, false}
        };
    }

    @ParameterizedTest
    @MethodSource(value = "getContentCombinations")
    void testSpanDestinationContextSerializationWithCombinations(boolean[] contentCombination) {
        boolean hasAddress = contentCombination[0];
        boolean hasPort = contentCombination[1];
        boolean hasServiceTargetContent = contentCombination[2];
        boolean hasCloudContent = contentCombination[3];
        Span span = new Span(MockTracer.create());
        Destination dest = span.getContext().getDestination();
        if (hasAddress) {
            dest.withAddress("whatever.com");
        }
        if (hasPort) {
            dest.withPort(80);
        }
        if (hasServiceTargetContent) {
            span.getContext().getServiceTarget()
                .withType("http")
                .withHostPortName("whatever.com", 80)
                .withNameOnlyDestinationResource();
        }
        if (hasCloudContent) {
            dest.getCloud().withRegion("us-east-1");
        }

        JsonNode spanJson = readJsonString(writer.toJsonString(span));
        JsonNode context = spanJson.get("context");
        JsonNode destination = context.get("destination");


        if (hasAddress || hasPort || hasServiceTargetContent || hasCloudContent) {
            assertThat(destination).isNotNull();
            if (hasAddress) {
                assertThat("whatever.com").isEqualTo(destination.get("address").textValue());
            } else {
                assertThat(destination.get("address")).isNull();
            }

            if (hasServiceTargetContent) {
                JsonNode service = destination.get("service");
                assertThat(service).isNotNull();
                assertThat("whatever.com:80").isEqualTo(service.get("resource").textValue());
                assertThat(service.get("name").textValue()).isEqualTo("");
                assertThat(service.get("type").textValue()).isEqualTo("");
                JsonNode serviceTarget = context.get("service").get("target");
                assertThat(serviceTarget.get("type").asText()).isEqualTo("http");
                assertThat(serviceTarget.get("name").asText()).isEqualTo("whatever.com:80");
            } else {
                assertThat(destination.get("service")).isNull();
            }

            if (hasCloudContent) {
                JsonNode cloud = destination.get("cloud");
                assertThat(cloud).isNotNull();
                assertThat(cloud.get("region").textValue()).isEqualTo("us-east-1");
            } else {
                assertThat(destination.get("cloud")).isNull();
            }
        } else {
            assertThat(destination).isNull();
        }
    }

    @Test
    void testTransactionNullFrameworkNameSerialization() {
        Transaction transaction = new Transaction(tracer);
        transaction.getTraceContext().setServiceInfo("service-name", null);
        transaction.setUserFrameworkName(null);
        JsonNode transactionJson = readJsonString(writer.toJsonString(transaction));
        assertThat(transactionJson.get("context").get("service").get("framework")).isNull();
    }

    @Test
    void testTransactionEmptyFrameworkNameSerialization() {
        Transaction transaction = new Transaction(tracer);
        transaction.getTraceContext().setServiceInfo("service-name", null);
        transaction.setUserFrameworkName("");
        JsonNode transactionJson = readJsonString(writer.toJsonString(transaction));
        assertThat(transactionJson.get("context").get("service").get("framework")).isNull();
    }

    @Test
    void testSpanInvalidDestinationSerialization() {
        Span span = new Span(tracer);
        span.getContext().getDestination().withAddress(null).withPort(-1);

        span.getContext().getServiceTarget().withUserName("").withNameOnlyDestinationResource();

        JsonNode spanJson = readJsonString(writer.toJsonString(span));
        JsonNode contextJson = spanJson.get("context");
        assertThat(contextJson.get("destination")).isNull();
        assertThat(contextJson.get("service")).isNull();
    }

    @Test
    void testSpanValidPortSerialization() {
        Span span = new Span(tracer);
        span.getContext().getDestination().withAddress(null).withPort(8090);

        JsonNode spanJson = readJsonString(writer.toJsonString(span));
        JsonNode destination = spanJson.get("context").get("destination");
        assertThat(destination).isNotNull();
        assertThat(destination.get("port").intValue()).isEqualTo(8090);
        assertThat(destination.get("address")).isNull();
        assertThat(destination.get("service")).isNull();
    }

    @Test
    void testSpanValidAddressAndPortSerialization() {
        Span span = new Span(tracer);
        span.getContext().getDestination().withAddress("test").withPort(8090);

        JsonNode spanJson = readJsonString(writer.toJsonString(span));
        JsonNode destination = spanJson.get("context").get("destination");
        assertThat(destination).isNotNull();
        assertThat(destination.get("port").intValue()).isEqualTo(8090);
        assertThat(destination.get("address").textValue()).isEqualTo("test");
        assertThat(destination.get("service")).isNull();
    }

    @Test
    void testSpanValidAddressSerialization() {
        Span span = new Span(tracer);
        span.getContext().getDestination().withAddress("test").withPort(0);

        JsonNode spanJson = readJsonString(writer.toJsonString(span));
        JsonNode destination = spanJson.get("context").get("destination");
        assertThat(destination).isNotNull();
        assertThat(destination.get("port")).isNull();
        assertThat(destination.get("address").textValue()).isEqualTo("test");
        assertThat(destination.get("service")).isNull();
    }

    @Test
    void testSpanValidServiceResourceSerialization() {
        Span span = new Span(tracer);
        span.getContext().getDestination().withAddress("").withPort(0);
        span.getContext().getServiceTarget().withType("test").withName("resource");

        JsonNode spanJson = readJsonString(writer.toJsonString(span));
        JsonNode destination = spanJson.get("context").get("destination");
        assertThat(destination).isNotNull();
        JsonNode service = destination.get("service");
        assertThat(service.get("resource").textValue()).isEqualTo("test/resource");
        assertThat(service.get("name").textValue()).isEmpty();
        assertThat(service.get("type").textValue()).isEmpty();

        JsonNode serviceTarget = spanJson.get("context").get("service").get("target");
        assertThat(serviceTarget.get("type").textValue()).isEqualTo("test");
        assertThat(serviceTarget.get("name").textValue()).isEqualTo("resource");
    }

    @Test
    void testSpanValidServiceAndAddressResourceSerialization() {
        Span span = new Span(tracer);
        span.getContext().getDestination().withAddress("test-address").withPort(0);

        span.getContext().getServiceTarget().withType("test").withName("test-resource").withNameOnlyDestinationResource();

        JsonNode spanJson = readJsonString(writer.toJsonString(span));
        JsonNode destination = spanJson.get("context").get("destination");
        assertThat(destination).isNotNull();
        assertThat(destination.get("port")).isNull();
        assertThat(destination.get("address").textValue()).isEqualTo("test-address");

        assertThat(destination.get("service").get("resource").textValue()).isEqualTo("test-resource");
        JsonNode serviceTarget = spanJson.get("context").get("service").get("target");
        assertThat(serviceTarget.get("type").textValue()).isEqualTo("test");
        assertThat(serviceTarget.get("name").textValue()).isEqualTo("test-resource");

    }

    @Test
    void testSpanMessageContextSerialization() {
        Span span = new Span(tracer);
        span.getContext().getMessage()
            .withRoutingKey("routing-key")
            .withQueue("test-queue")
            .withBody("test-body")
            .addHeader("text-header", "text-value")
            .addHeader("binary-header", "binary-value".getBytes(StandardCharsets.UTF_8))
            .withAge(20);

        JsonNode spanJson = readJsonString(writer.toJsonString(span));
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
        JsonNode routingKey = message.get("routing_key");
        assertThat(routingKey.textValue()).isEqualTo("routing-key");
    }

    @Test
    void testSpanMessageContextSerializationWithoutRoutingKey() {
        Span span = new Span(tracer);
        span.getContext().getMessage()
            .withQueue("test-queue")
            .withBody("test-body")
            .addHeader("text-header", "text-value")
            .addHeader("binary-header", "binary-value".getBytes(StandardCharsets.UTF_8))
            .withAge(20);

        JsonNode spanJson = readJsonString(writer.toJsonString(span));
        JsonNode routingKey = spanJson.get("context").get("message").get("routing_key");
        assertThat(routingKey).isNull();
    }

    @Test
    void testSpanMessageContextInvalidTimestamp() {
        Span span = new Span(tracer);
        span.getContext().getMessage()
            .withQueue("test-queue");

        JsonNode spanJson = readJsonString(writer.toJsonString(span));
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
        Span span = new Span(tracer);
        span.getContext().getDb()
            .withAffectedRowsCount(5)
            .withInstance("test-instance")
            .withStatement("SELECT * FROM TABLE").withDbLink("db-link");

        JsonNode spanJson = readJsonString(writer.toJsonString(span));
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
        Span span = new Span(tracer);
        span.withChildIds(LongList.of(id1.getLeastSignificantBits(), id2.getLeastSignificantBits()));

        JsonNode spanJson = readJsonString(writer.toJsonString(span));
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
        doReturn("container_id").when(container).getId();
        doReturn(container).when(systemInfo).getContainerInfo();
        SystemInfo.Kubernetes kubernetes = createKubernetesMock("pod", "pod_id", "node", "ns");
        doReturn(kubernetes).when(systemInfo).getKubernetesInfo();
        doReturn("9 3/4").when(systemInfo).getPlatform(); // this terrible pun is intentional

        Service service = new Service()
            .withAgent(new Agent("MyAgent", "1.11.1"))
            .withFramework(new Framework("Lambda_Java", "1.2.3"))
            .withName("MyService")
            .withVersion("1.0")
            .withLanguage(new Language("c++", "14"));


        ProcessInfo processInfo = new ProcessInfo("title").withPid(1234);
        processInfo.getArgv().add("test");

        CloudProviderInfo cloudProviderInfo = createCloudProviderInfo();
        writer = new DslJsonSerializer(
            mock(StacktraceConfiguration.class),
            apmServerClient,
            MetaDataMock.create(
                processInfo, service, systemInfo, cloudProviderInfo,
                Map.of("foo", "bar", "עברית", "בדיקה"), createFaaSMetaDataExtension()
            )
        ).newWriter();
        writer.blockUntilReady();
        writer.appendMetaDataNdJsonToStream();
        JsonNode metaDataJson = readJsonString(writer.toString()).get("metadata");

        JsonNode serviceJson = metaDataJson.get("service");
        assertThat(service).isNotNull();
        assertThat(serviceJson.get("name").textValue()).isEqualTo("MyService");
        assertThat(serviceJson.get("version").textValue()).isEqualTo("1.0");

        JsonNode languageJson = serviceJson.get("language");
        assertThat(languageJson).isNotNull();
        assertThat(languageJson.get("name").asText()).isEqualTo("c++");
        assertThat(languageJson.get("version").asText()).isEqualTo("14");

        JsonNode frameworkJson = serviceJson.get("framework");
        assertThat(frameworkJson).isNotNull();
        assertThat(frameworkJson.get("name").asText()).isEqualTo("Lambda_Java");
        assertThat(frameworkJson.get("version").asText()).isEqualTo("1.2.3");

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
        assertThat(jsonCloudAccount).isNotNull();
        assertThat(jsonCloudAccount.get("name")).isNull();
        assertThat(jsonCloudAccount.get("id").asText()).isEqualTo("accountId");
        JsonNode jsonCloudInstance = jsonCloud.get("instance");
        assertThat(jsonCloudInstance.get("id").asText()).isEqualTo("instanceId");
        assertThat(jsonCloudInstance.get("name").asText()).isEqualTo("instanceName");
        JsonNode jsonCloudMachine = jsonCloud.get("machine");
        assertThat(jsonCloudMachine.get("type").asText()).isEqualTo("machineType");
        JsonNode jsonCloudProject = jsonCloud.get("project");
        assertThat(jsonCloudProject.get("id").asText()).isEqualTo("projectId");
        assertThat(jsonCloudProject.get("name").asText()).isEqualTo("projectName");
        JsonNode jsonCloudService = jsonCloud.get("service");
        assertThat(jsonCloudService).isNotNull();
        assertThat(jsonCloudService.get("name").asText()).isEqualTo("ec2");
    }

    @Test
    void testConfiguredServiceNodeName() throws Exception {
        ConfigurationRegistry configRegistry = SpyConfiguration.createSpyConfig();
        doReturn("Custom-Node-Name").when(configRegistry.getConfig(CoreConfiguration.class)).getServiceNodeName();
        writer = new DslJsonSerializer(mock(StacktraceConfiguration.class), apmServerClient, MetaData.create(configRegistry, null)).newWriter();
        writer.blockUntilReady();
        writer.appendMetaDataNdJsonToStream();
        JsonNode metaDataJson = readJsonString(writer.toString()).get("metadata");
        JsonNode serviceJson = metaDataJson.get("service");
        assertThat(serviceJson).isNotNull();
        JsonNode nodeJson = serviceJson.get("node");
        assertThat(nodeJson).isNotNull();
        assertThat(nodeJson.get("configured_name").textValue()).isEqualTo("Custom-Node-Name");

    }

    @Test
    void testTransactionContextSerialization() {

        Transaction transaction = new Transaction(tracer);

        // test only the most recent server here
        doReturn(true).when(apmServerClient).supportsMultipleHeaderValues();
        doReturn(true).when(apmServerClient).supportsNumericUrlPort();

        transaction.getContext().getUser()
            .withId("42")
            .withEmail("user@email.com")
            .withUsername("bob");

        Request request = transaction.getContext().getRequest();

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
            .withRemoteAddress("::1");

        transaction.getContext().getResponse()
            .withFinished(true)
            .withHeadersSent(false)
            .addHeader("response_header", "value")
            .withStatusCode(418);

        transaction.getContext().getMessage().withQueue("test_queue").withAge(0);

        transaction.getFaas()
            .withExecution("faas_execution")
            .withColdStart(true)
            .getTrigger()
            .withType("other")
            .withRequestId("requestId");

        transaction.getContext().getServiceOrigin().withName("origin_service_name").withId("origin_service_id").withVersion("origin_service_version");
        transaction.getContext().getCloudOrigin().withRegion("origin_cloud_region").withAccountId("origin_cloud_account_id").withProvider("origin_cloud_provider").withServiceName("origin_cloud_service_name");

        TraceContext ctx = transaction.getTraceContext();

        String serviceName = RandomStringUtils.randomAlphabetic(5);
        String serviceVersion = RandomStringUtils.randomAlphabetic(5);
        String frameworkName = RandomStringUtils.randomAlphanumeric(10);
        String frameworkVersion = RandomStringUtils.randomNumeric(3);

        ctx.setServiceInfo(serviceName, serviceVersion);

        transaction.setFrameworkName(frameworkName);
        transaction.setFrameworkVersion(frameworkVersion);

        String jsonString = writer.toJsonString(transaction);
        JsonNode json = readJsonString(jsonString);

        JsonNode jsonContext = json.get("context");
        assertThat(jsonContext.get("user").get("id").asText()).isEqualTo("42");
        assertThat(jsonContext.get("user").get("email").asText()).isEqualTo("user@email.com");
        assertThat(jsonContext.get("user").get("username").asText()).isEqualTo("bob");
        assertThat(jsonContext.get("service").get("name").asText()).isEqualTo(serviceName);
        assertThat(jsonContext.get("service").get("version").asText()).isEqualTo(serviceVersion);
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
        assertThat(jsonSocket).hasSize(1);
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

        JsonNode jsonService = jsonContext.get("service");
        JsonNode jsonServiceOrigin = jsonService.get("origin");
        assertThat(jsonServiceOrigin.get("name").asText()).isEqualTo("origin_service_name");
        assertThat(jsonServiceOrigin.get("id").asText()).isEqualTo("origin_service_id");
        assertThat(jsonServiceOrigin.get("version").asText()).isEqualTo("origin_service_version");

        JsonNode jsonCloud = jsonContext.get("cloud");
        JsonNode jsonCloudOrigin = jsonCloud.get("origin");
        assertThat(jsonCloudOrigin.get("region").asText()).isEqualTo("origin_cloud_region");
        assertThat(jsonCloudOrigin.get("provider").asText()).isEqualTo("origin_cloud_provider");
        JsonNode jsonCloudOriginAccount = jsonCloudOrigin.get("account");
        assertThat(jsonCloudOriginAccount.get("id").asText()).isEqualTo("origin_cloud_account_id");
        JsonNode jsonCloudOriginService = jsonCloudOrigin.get("service");
        assertThat(jsonCloudOriginService.get("name").asText()).isEqualTo("origin_cloud_service_name");

        JsonNode jsonFaas = json.get("faas");
        assertThat(jsonFaas).isNotNull();
        assertThat(jsonFaas.get("execution").asText()).isEqualTo("faas_execution");
        assertThat(jsonFaas.get("coldstart").asBoolean()).isEqualTo(true);

        JsonNode jsonFaasTrigger = jsonFaas.get("trigger");
        assertThat(jsonFaasTrigger).isNotNull();
        assertThat(jsonFaasTrigger.get("type").asText()).isEqualTo("other");
        assertThat(jsonFaasTrigger.get("request_id").asText()).isEqualTo("requestId");
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
        final String content = writer.toJsonString(transaction);
        System.out.println(content);
        final JsonNode transactionJson = objectMapper.readTree(content);
        assertThat(transactionJson.get("context").get("request").get("body").textValue()).isEqualTo("{foo}");

        transaction.resetState();
        assertThat((Object) request.getBodyBuffer()).isNull();
    }

    /**
     * Tests that body not properly finished (not properly flipped) is ignored from serialization
     *
     * @throws IOException indicates failure in deserialization
     */
    @Test
    void testNonFlippedTransactionBodyBuffer() throws IOException {
        final Transaction transaction = createRootTransaction();
        Request request = transaction.getContext().getRequest();
        request.withBodyBuffer().append("TEST");
        final String content = writer.toJsonString(transaction);
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

        assertThat(objectMapper.readTree(writer.toJsonString(copy)).get("context"))
            .isEqualTo(objectMapper.readTree(writer.toJsonString(transaction)).get("context"));
    }

    @Test
    void testCustomContext() throws Exception {
        final Transaction transaction = createRootTransaction();
        transaction.addCustomContext("string", "foo");
        final String longString = RandomStringUtils.randomAlphanumeric(10001);
        transaction.addCustomContext("long_string", longString);
        transaction.addCustomContext("number", 42);
        transaction.addCustomContext("boolean", true);

        final JsonNode customContext = objectMapper.readTree(writer.toJsonString(transaction)).get("context").get("custom");
        assertThat(customContext.get("string").textValue()).isEqualTo("foo");
        assertThat(customContext.get("long_string").textValue()).isEqualTo(longString.substring(0, 9999) + "…");
        assertThat(customContext.get("number").intValue()).isEqualTo(42);
        assertThat(customContext.get("boolean").booleanValue()).isEqualTo(true);
    }

    @Test
    void testJsonSchemaDslJsonEmptyValues() throws IOException {
        Transaction transaction = new Transaction(tracer);
        final String content = writer.toJsonString(transaction);
        System.out.println(content);
        JsonNode transactionNode = objectMapper.readTree(content);
        assertThat(transactionNode.get("timestamp").asLong()).isEqualTo(0);
        assertThat(transactionNode.get("duration").asDouble()).isEqualTo(0.0);
        assertThat(transactionNode.get("context").get("tags")).isEmpty();
        assertThat(transactionNode.get("sampled").asBoolean()).isEqualTo(false);
        assertThat(transactionNode.get("span_count").get("dropped").asInt()).isEqualTo(0);
        assertThat(transactionNode.get("span_count").get("started").asInt()).isEqualTo(0);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testSystemInfo_configuredHostname(boolean supportsConfiguredAndDetectedHostname) throws Exception {
        String arc = "test-arc";
        String platform = "test-platform";

        MetaData metaData = createMetaData(new SystemInfo(arc, "configured", "detected", platform));
        DslJsonSerializer.serializeMetadata(metaData, writer.getJsonWriter(), supportsConfiguredAndDetectedHostname, true);
        writer.appendMetadataToStream();

        JsonNode system = readJsonString(writer.toString()).get("system");

        assertThat(arc).isEqualTo(system.get("architecture").asText());
        assertThat(platform).isEqualTo(system.get("platform").asText());
        if (supportsConfiguredAndDetectedHostname) {
            assertThat(system.get("configured_hostname").asText()).isEqualTo("configured");
            assertThat(system.get("detected_hostname")).isNull();
            assertThat(system.get("hostname")).isNull();
        } else {
            assertThat(system.get("configured_hostname")).isNull();
            assertThat(system.get("detected_hostname")).isNull();
            assertThat(system.get("hostname").asText()).isEqualTo("configured");
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testSystemInfo_detectedHostname(boolean supportsConfiguredAndDetectedHostname) throws Exception {
        String arc = "test-arc";
        String platform = "test-platform";

        MetaData metaData = createMetaData(new SystemInfo(arc, null, "detected", platform));
        DslJsonSerializer.serializeMetadata(metaData, writer.getJsonWriter(), supportsConfiguredAndDetectedHostname, true);
        writer.appendMetadataToStream();

        JsonNode system = readJsonString(writer.toString()).get("system");

        assertThat(arc).isEqualTo(system.get("architecture").asText());
        assertThat(platform).isEqualTo(system.get("platform").asText());
        if (supportsConfiguredAndDetectedHostname) {
            assertThat(system.get("configured_hostname")).isNull();
            assertThat(system.get("detected_hostname").asText()).isEqualTo("detected");
            assertThat(system.get("hostname")).isNull();
        } else {
            assertThat(system.get("configured_hostname")).isNull();
            assertThat(system.get("detected_hostname")).isNull();
            assertThat(system.get("hostname").asText()).isEqualTo("detected");
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testSystemInfo_nullHostname(boolean supportsConfiguredAndDetectedHostname) throws Exception {
        String arc = "test-arc";
        String platform = "test-platform";

        MetaData metaData = createMetaData(new SystemInfo(arc, null, null, platform));
        DslJsonSerializer.serializeMetadata(metaData, writer.getJsonWriter(), supportsConfiguredAndDetectedHostname, true);
        writer.appendMetadataToStream();

        JsonNode system = readJsonString(writer.toString()).get("system");
        assertThat(arc).isEqualTo(system.get("architecture").asText());
        assertThat(platform).isEqualTo(system.get("platform").asText());
        if (supportsConfiguredAndDetectedHostname) {
            assertThat(system.get("configured_hostname")).isNull();
            assertThat(system.get("detected_hostname")).isNull();
            assertThat(system.get("hostname")).isNull();
        } else {
            assertThat(system.get("configured_hostname")).isNull();
            assertThat(system.get("detected_hostname")).isNull();
            assertThat(system.get("hostname").asText()).isEqualTo("<unknown>");
        }
    }

    @Test
    void testCloudProviderInfoWithNullObjectFields() throws Exception {
        MetaData metaData = createMetaData();
        CloudProviderInfo cloudProviderInfo = Objects.requireNonNull(metaData.getCloudProviderInfo());
        cloudProviderInfo.setAccount(null);
        cloudProviderInfo.setMachine(null);
        cloudProviderInfo.setProject(null);
        cloudProviderInfo.setInstance(null);
        cloudProviderInfo.setService(null);

        DslJsonSerializer.serializeMetadata(metaData, writer.getJsonWriter(), true, true);
        writer.appendMetadataToStream();

        JsonNode jsonCloud = readJsonString(writer.toString()).get("cloud");

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
        JsonNode jsonCloudService = jsonCloud.get("service");
        assertThat(jsonCloudService).isNull();
    }

    @Test
    void testCloudProviderInfoWithNullNameFields() throws Exception {
        MetaData metaData = createMetaData();
        CloudProviderInfo cloudProviderInfo = Objects.requireNonNull(metaData.getCloudProviderInfo());
        Objects.requireNonNull(cloudProviderInfo.getProject()).setName(null);
        Objects.requireNonNull(cloudProviderInfo.getInstance()).setName(null);

        DslJsonSerializer.serializeMetadata(metaData, writer.getJsonWriter(), true, true);
        writer.appendMetadataToStream();

        JsonNode jsonCloud = readJsonString(writer.toString()).get("cloud");

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
        NameAndIdField project = Objects.requireNonNull(cloudProviderInfo.getProject());
        project.setName(null);
        project.setId(null);
        NameAndIdField instance = Objects.requireNonNull(cloudProviderInfo.getInstance());
        instance.setName(null);
        instance.setId(null);

        DslJsonSerializer.serializeMetadata(metaData, writer.getJsonWriter(), true, true);
        writer.appendMetadataToStream();

        JsonNode jsonCloud = readJsonString(writer.toString()).get("cloud");

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

        DslJsonSerializer.serializeMetadata(metaData, writer.getJsonWriter(), true, true);
        writer.appendMetadataToStream();

        JsonNode jsonCloud = readJsonString(writer.toString()).get("cloud");

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

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testActivationMethod(boolean supportsActivationMethod) throws Exception {

        MetaData metaData = createMetaData();

        DslJsonSerializer.serializeMetadata(metaData, writer.getJsonWriter(), true, supportsActivationMethod);
        writer.appendMetadataToStream();

        checkMetadataActivationMethod(writer.toString(), supportsActivationMethod ? "unknown" : null);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "true  | false", // likely when going from "version unknown" to "known unsupported version"
        "false | true", // unlikey to happen in practice, but worth testing anyway
    })
    void testActivationMethodMetadataUpdate(boolean value1, boolean value2 ) throws Exception {
        // because metadata is serialized only once, we need to ensure it's properly updated whenever needed
        // in particular when going from 'apm server version unknown' to 'apm server version known'

        StacktraceConfiguration stacktraceConfiguration = mock(StacktraceConfiguration.class);
        apmServerClient = mock(ApmServerClient.class);
        doReturn(value1).when(apmServerClient).supportsActivationMethod();

        Service service = mock(Service.class);
        Agent agent = new Agent("java-test", "1.0.0");
        doReturn(agent).when(service).getAgent();
        MetaData mockMetada = MetaDataMock.createDefaultMock();
        doReturn(service).when(mockMetada).getService();

        writer = new DslJsonSerializer(stacktraceConfiguration, apmServerClient, MetaDataMock.create(mockMetada)).newWriter();
        writer.blockUntilReady();
        writer.appendMetadataToStream();

        checkMetadataActivationMethod(writer.toString(), value1 ? "unknown" : null);
        writer.jw.reset();

        doReturn(value2).when(apmServerClient).supportsActivationMethod();

        writer.blockUntilReady();
        writer.appendMetadataToStream();

        checkMetadataActivationMethod(writer.toString(), value2 ? "unknown" : null);
    }

    private void checkMetadataActivationMethod(String json, @Nullable String expectedValue) {
        JsonNode root = readJsonString(json);

        JsonNode service = root.get("service");
        assertThat(service).isNotNull();

        JsonNode agent = service.get("agent");
        assertThat(agent).isNotNull();

        JsonNode activationMethod = agent.get("activation_method");

        if (expectedValue == null) {
            assertThat(activationMethod).isNull();
        } else {
            assertThat(activationMethod.isTextual()).isTrue();
            assertThat(activationMethod.asText()).isEqualTo(expectedValue);
        }
    }

    private MetaData createMetaData() throws Exception {
        return createMetaData(SystemInfo.create("hostname", 0, mock(ServerlessConfiguration.class)));
    }

    private MetaData createMetaData(SystemInfo system) throws Exception {
        Service service = new Service().withAgent(new Agent("name", "version")).withName("name");
        final ProcessInfo processInfo = new ProcessInfo("title");
        processInfo.getArgv().add("test");
        return MetaDataMock.create(processInfo, service, system, createCloudProviderInfo(), new HashMap<>(0), createFaaSMetaDataExtension()).get();
    }

    private CloudProviderInfo createCloudProviderInfo() {
        CloudProviderInfo cloudProviderInfo = new CloudProviderInfo("aws");
        cloudProviderInfo.setMachine(new CloudProviderInfo.ProviderMachine("machineType"));
        cloudProviderInfo.setInstance(new NameAndIdField("instanceName", "instanceId"));
        cloudProviderInfo.setAvailabilityZone("availabilityZone");
        cloudProviderInfo.setAccount(new CloudProviderInfo.ProviderAccount("accountId"));
        cloudProviderInfo.setRegion("region");
        cloudProviderInfo.setProject(new NameAndIdField("projectName", "projectId"));
        cloudProviderInfo.setService(new CloudProviderInfo.Service("ec2"));
        return cloudProviderInfo;
    }

    private FaaSMetaDataExtension createFaaSMetaDataExtension() {
        return new FaaSMetaDataExtension(
            new Framework("Lambda_Java", "1.2.3"),
            new NameAndIdField(null, "accountId"),
            "region"
        );
    }

    private Transaction createRootTransaction(Sampler sampler) {
        Transaction t = new Transaction(tracer);
        t.startRoot(0, sampler);
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
        Span span = new Span(tracer);
        span.setStackTrace(Arrays.asList(StackFrame.of("foo.Bar", "baz"), StackFrame.of("foo.Bar$Baz", "qux")));

        JsonNode spanJson = readJsonString(writer.toJsonString(span));
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

    private void testRootTransactionSampleRate(boolean sampled, double samplerRate, @Nullable Double expectedRate) {
        Sampler sampler = mock(Sampler.class);
        doReturn(sampled).when(sampler).isSampled(any(Id.class));
        doReturn(samplerRate).when(sampler).getSampleRate();

        Transaction transaction = createRootTransaction(sampler);

        JsonNode jsonTransaction = readJsonString(writer.toJsonString(transaction));

        JsonNode jsonSampleRate = jsonTransaction.get("sample_rate");
        JsonNode jsonSampled = jsonTransaction.get("sampled");
        assertThat(jsonSampled.asBoolean()).isEqualTo(sampled);
        if (null == expectedRate) {
            assertThat(jsonSampleRate).isNull();
        } else {
            assertThat(jsonSampleRate.asDouble()).isEqualTo(expectedRate);
        }
    }

    @Test
    void testSampledSpan_rateFromParent() {

        Sampler sampler = mock(Sampler.class);
        doReturn(true).when(sampler).isSampled(any(Id.class));
        doReturn(0.42d).when(sampler).getSampleRate();

        Transaction transaction = createRootTransaction(sampler);
        TraceContext transactionContext = transaction.getTraceContext();
        assertThat(transactionContext.isSampled()).isTrue();
        assertThat(transactionContext.getSampleRate()).isEqualTo(0.42d);

        Span span = new Span(tracer);
        span.getTraceContext().asChildOf(transactionContext);

        JsonNode jsonSpan = readJsonString(writer.toJsonString(span));

        assertThat(jsonSpan.get("sample_rate").asDouble()).isEqualTo(0.42d);
    }

    @Test
    void testNonSampledTransaction() {
        Sampler sampler = mock(Sampler.class);
        doReturn(false).when(sampler).isSampled(any(Id.class));
        doReturn(0.42d).when(sampler).getSampleRate();
        Transaction transaction = createRootTransaction(sampler);
        TraceContext transactionContext = transaction.getTraceContext();
        assertThat(transactionContext.isSampled()).isFalse();
        assertThat(transactionContext.getSampleRate()).isEqualTo(0.0d);
        JsonNode transactionSpan = readJsonString(writer.toJsonString(transaction));
        assertThat(transactionSpan.get("sample_rate").asDouble()).isEqualTo(0.0d);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void multiValueHeaders(boolean supportsMulti) {
        // older versions of APM server do not support multi-value headers
        // thus we should make sure to not break those

        Transaction transaction = createRootTransaction();

        transaction.getContext().getRequest()
            .addHeader("user-agent", "user-agent1")
            .addHeader("user-agent", "user-agent2")
            .addHeader("header", "header1")
            .addHeader("header", "header2")
            .addCookie("cookie", "cookie1")
            .addCookie("cookie", "cookie2");

        transaction.getContext().getResponse()
            .addHeader("content-type", "content-type1")
            .addHeader("content-type", "content-type2");

        if (supportsMulti) {
            doReturn(supportsMulti).when(apmServerClient).supportsMultipleHeaderValues();
        }

        JsonNode jsonTransaction = readJsonString(writer.toJsonString(transaction));

        JsonNode requestJson = jsonTransaction.get("context").get("request");
        JsonNode headersJson = requestJson.get("headers");
        JsonNode cookiesJson = requestJson.get("cookies");
        JsonNode responseHeaders = jsonTransaction.get("context").get("response").get("headers");
        if (supportsMulti) {
            checkMultiValueHeader(headersJson, "user-agent", "user-agent1", "user-agent2");
            checkMultiValueHeader(headersJson, "header", "header1", "header2");
            checkMultiValueHeader(cookiesJson, "cookie", "cookie1", "cookie2");
            checkMultiValueHeader(responseHeaders, "content-type", "content-type1", "content-type2");
        } else {
            checkSingleValueHeader(headersJson, "user-agent", "user-agent1");
            checkSingleValueHeader(headersJson, "header", "header1");
            checkSingleValueHeader(cookiesJson, "cookie", "cookie1");
            checkSingleValueHeader(responseHeaders, "content-type", "content-type1");
        }

        assertThat(headersJson).isNotNull();
    }

    @Test
    void testOTelSpanSerialization() {
        Span span = new Span(tracer).withName("otel span");
        testOTelSpanSerialization(span, s -> readJsonString(writer.toJsonString(s)));

        Transaction transaction = new Transaction(MockTracer.create()).withName("otel span");
        testOTelSpanSerialization(transaction, t -> readJsonString(writer.toJsonString(t)));
    }

    private <T extends AbstractSpan<T>> void testOTelSpanSerialization(T context, Function<T, JsonNode> toJson) {

        assertThat(context.getOtelKind())
            .describedAs("otel span kind should not be set by default")
            .isNull();

        JsonNode spanJson = toJson.apply(context);

        assertThat(spanJson.get("name").asText()).isEqualTo("otel span");
        assertThat(spanJson.get("otel")).isNull();

        for (OTelSpanKind kind : OTelSpanKind.values()) {
            context.withOtelKind(kind);
            spanJson = toJson.apply(context);

            JsonNode otelJson = spanJson.get("otel");
            assertThat(otelJson).isNotNull();
            assertThat(otelJson.get("span_kind").asText()).isEqualTo(kind.name());
        }

        // with custom otel attributes
        context.getOtelAttributes().put("attribute.string", "hello");
        context.getOtelAttributes().put("attribute.long", 123L);
        context.getOtelAttributes().put("attribute.boolean", false);
        context.getOtelAttributes().put("attribute.float", 0.42f);
        spanJson = toJson.apply(context);
        JsonNode otelJson = spanJson.get("otel");
        assertThat(otelJson).isNotNull();
        JsonNode otelAttributes = otelJson.get("attributes");
        assertThat(otelAttributes).isNotNull();
        assertThat(otelAttributes.size()).isEqualTo(4);
        assertThat(otelAttributes.get("attribute.string").asText()).isEqualTo("hello");
        assertThat(otelAttributes.get("attribute.long").asLong()).isEqualTo(123L);
        assertThat(otelAttributes.get("attribute.boolean").asBoolean()).isFalse();
        assertThat(otelAttributes.get("attribute.float").asDouble()).isEqualTo(0.42d);
    }

    @Test
    void testNonCompositeSpan() {
        Span span = new Span(tracer);

        JsonNode jsonSpan = readJsonString(writer.toJsonString(span));
        assertThat(jsonSpan.get("composite")).isNull();
    }

    @Test
    void testCompositeSpan() {
        Span span = new Span(tracer);
        span.getComposite().init(1234, "exact_match");

        JsonNode jsonSpan = readJsonString(writer.toJsonString(span));
        assertThat(jsonSpan.get("composite").get("count").asInt()).isEqualTo(1);
        assertThat(jsonSpan.get("composite").get("sum").asDouble()).isEqualTo(1.234);
        assertThat(jsonSpan.get("composite").get("compression_strategy").asText()).isEqualTo("exact_match");
    }

    @Test
    void testSpanLinksSerialization() {
        Transaction transaction = tracer.startRootTransaction(null);
        Span parent1 = Objects.requireNonNull(transaction).createSpan();
        Map<String, String> textTraceContextCarrier = new HashMap<>();
        parent1.propagateTraceContext(textTraceContextCarrier, TextHeaderMapAccessor.INSTANCE);
        transaction.addSpanLink(TraceContext.getFromTraceContextTextHeaders(), TextHeaderMapAccessor.INSTANCE, textTraceContextCarrier);
        Span parent2 = transaction.createSpan();
        Map<String, byte[]> binaryTraceContextCarrier = new HashMap<>();
        parent2.propagateTraceContext(binaryTraceContextCarrier, BinaryHeaderMapAccessor.INSTANCE);
        transaction.addSpanLink(TraceContext.getFromTraceContextBinaryHeaders(), BinaryHeaderMapAccessor.INSTANCE, binaryTraceContextCarrier);
        JsonNode transactionJson = readJsonString(writer.toJsonString(transaction));
        JsonNode spanLinks = transactionJson.get("links");
        assertThat(spanLinks).isNotNull();
        assertThat(spanLinks.isArray()).isTrue();
        assertThat(spanLinks.size()).isEqualTo(2);
        JsonNode parent1link = spanLinks.get(0);
        assertThat(parent1link.isObject()).isTrue();
        assertThat(parent1link.get("trace_id").textValue()).isEqualTo(parent1.getTraceContext().getTraceId().toString());
        assertThat(parent1link.get("span_id").textValue()).isEqualTo(parent1.getTraceContext().getId().toString());
        JsonNode parent2link = spanLinks.get(1);
        assertThat(parent2link.isObject()).isTrue();
        assertThat(parent2link.get("trace_id").textValue()).isEqualTo(parent2.getTraceContext().getTraceId().toString());
        assertThat(parent2link.get("span_id").textValue()).isEqualTo(parent2.getTraceContext().getId().toString());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testSerializeLog(boolean asString) {
        String ecsJsonLog = "{\"@timestamp\":\"2022-10-27T12:38:00.593Z\",\"log.level\": \"INFO\",\"message\":\"msg\",\"ecs.version\": \"1.2.0\",\"service.name\":\"opbeans\",\"service.version\":\"0.0.1-SNAPSHOT\",\"event.dataset\":\"opbeans.console\",\"process.thread.name\":\"main\",\"log.logger\":\"logger\"}\n";
        if (asString) {
            writer.serializeLogNdJson(ecsJsonLog);
        } else {
            writer.serializeLogNdJson(ecsJsonLog.getBytes(StandardCharsets.UTF_8));
        }
        String serializedJson = getAndResetSerializerJson();

        // this is probably an implementation detail, as generated JSON could be "equivalent" while not being exactly
        // identical, but it allows to ensure that the original log event is sent as-is without alteration
        assertThat(serializedJson)
            .describedAs("original ECS formatted log event should be copied as-is (minus the EOL)")
            .contains(ecsJsonLog.trim());

        // original EOL should be discarded in provided log event otherwise it breaks ND-JSON
        assertThat(serializedJson.indexOf("\n"))
            .describedAs("only single EOL character expected at the end of serialized ND-JSON")
            .isEqualTo(serializedJson.length() - 1);

        JsonNode ndJsonLog = readJsonString(serializedJson);
        assertThat(ndJsonLog.has("log")).isTrue();
        // only testing a single field is enough to test structure as we already checked that the original event was copied as-is
        assertThat(ndJsonLog.get("log").get("message").asText()).isEqualTo("msg");


    }

    private static void checkSingleValueHeader(JsonNode json, String fieldName, String value) {
        JsonNode fieldValue = json.get(fieldName);
        assertThat(fieldValue.isTextual()).isTrue();
        assertThat(fieldValue.asText()).isEqualTo(value);
    }

    private static void checkMultiValueHeader(JsonNode json, String fieldName, String value1, String value2) {
        JsonNode fieldValue = json.get(fieldName);
        assertThat(fieldValue.isArray()).isTrue();
        assertThat(fieldValue.size()).isEqualTo(2);
        assertThat(fieldValue.get(0).asText()).isEqualTo(value1);
        assertThat(fieldValue.get(1).asText()).isEqualTo(value2);
    }

    private JsonNode readJsonString(String jsonString) {
        try {
            JsonNode json = objectMapper.readTree(jsonString);

            // pretty print JSON in standard output for easier test debug
            System.out.println(json.toPrettyString());

            return json;
        } catch (JsonProcessingException e) {
            // any invalid JSON will require debugging the raw string
            throw new IllegalArgumentException("invalid JSON = " + jsonString);
        }
    }

    private static SystemInfo.Kubernetes createKubernetesMock(String podName, String podId, String nodeName, String namespace) {
        SystemInfo.Kubernetes k = mock(SystemInfo.Kubernetes.class);

        doReturn(true).when(k).hasContent();

        SystemInfo.Kubernetes.Pod pod = mock(SystemInfo.Kubernetes.Pod.class);
        doReturn(podName).when(pod).getName();
        doReturn(podId).when(pod).getUid();

        doReturn(pod).when(k).getPod();

        SystemInfo.Kubernetes.Node node = mock(SystemInfo.Kubernetes.Node.class);
        doReturn(nodeName).when(node).getName();
        doReturn(node).when(k).getNode();

        doReturn(namespace).when(k).getNamespace();

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
        final AbstractContext context = new AbstractContext() {
        };
        for (Map.Entry<String, Object> entry : tags.entrySet()) {
            if (entry.getValue() instanceof String) {
                context.addLabel(entry.getKey(), (String) entry.getValue());
            } else if (entry.getValue() instanceof Boolean) {
                context.addLabel(entry.getKey(), (Boolean) entry.getValue());
            } else if (entry.getValue() instanceof Number) {
                context.addLabel(entry.getKey(), (Number) entry.getValue());
            }
        }
        writer.serializeLabels(context);
        return getAndResetSerializerJson();
    }

    private String getAndResetSerializerJson() {
        final String jsonString = writer.jw.toString();
        writer.jw.reset();
        return jsonString;
    }

}
