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
package org.example.stacktrace;

import co.elastic.apm.agent.MockTracer;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.stacktrace.StacktraceConfiguration;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.report.ApmServerClient;
import co.elastic.apm.agent.report.serialize.DslJsonSerializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

/**
 * This class intentionally is not inside the co.elastic.apm package. This is to test the library_frame feature.
 */
class StacktraceSerializationTest {

    private List<JsonNode> stacktrace;
    private StacktraceConfiguration stacktraceConfiguration;
    private DslJsonSerializer.Writer serializer;
    private ObjectMapper objectMapper;
    private ElasticApmTracer tracer;

    @BeforeEach
    void setUp() throws IOException {
        tracer = MockTracer.createRealTracer();
        stacktraceConfiguration = tracer.getConfig(StacktraceConfiguration.class);
        // always enable
        doReturn(0L).when(stacktraceConfiguration).getSpanStackTraceMinDurationMs();
        serializer = new DslJsonSerializer(stacktraceConfiguration, mock(ApmServerClient.class), tracer.getMetaDataFuture())
            .newWriter();
        objectMapper = new ObjectMapper();
        stacktrace = getStackTrace();
    }

    @Test
    void fillStackTrace() {
        assertThat(stacktrace).isNotEmpty();
        // even though the stacktrace is captured within our tracer class, the first method should be getStackTrace
        assertThat(stacktrace.get(0).get("module").textValue()).doesNotStartWith("co.elastic");
        assertThat(stacktrace.get(0).get("function").textValue()).isEqualTo("getStackTrace");
        assertThat(stacktrace.stream().filter(st -> st.get("filename").textValue().equals("StacktraceSerializationTest.java"))).isNotEmpty();
    }

    @Test
    void testAppFrame() throws Exception {
        doReturn(Collections.singletonList("org.example.stacktrace")).when(stacktraceConfiguration).getApplicationPackages();
        stacktrace = getStackTrace();
        Optional<JsonNode> thisMethodsFrame = stacktrace.stream()
            .filter(st -> st.get("module").textValue().equals(getClass().getPackageName()))
            .findAny();
        assertThat(thisMethodsFrame).isPresent();
        assertThat(thisMethodsFrame.get().get("library_frame").booleanValue()).isFalse();
    }

    @Test
    void testNoAppFrame() {
        Optional<JsonNode> thisMethodsFrame = stacktrace.stream()
            .filter(st -> st.get("module").textValue().startsWith(getClass().getPackageName()))
            .findAny();
        assertThat(thisMethodsFrame).isPresent();
        assertThat(thisMethodsFrame.get().get("library_frame").booleanValue()).isTrue();
    }

    @Test
    void testFileNamePresent() {
        assertThat(stacktrace.stream().filter(st -> st.get("filename").textValue() == null)).isEmpty();
    }

    @Test
    void testNoInternalStackFrames() {
        assertSoftly(softly -> {
            softly.assertThat(stacktrace.stream().filter(st -> st.get("module").textValue().startsWith("java.lang."))).isEmpty();
            softly.assertThat(stacktrace.stream().filter(st -> st.get("module").textValue().startsWith("sun."))).isEmpty();
        });
    }

    @Test
    void testStackTraceElementSerialization() throws IOException {
        doReturn(Collections.singletonList("co.elastic.apm")).when(stacktraceConfiguration).getApplicationPackages();

        StackTraceElement stackTraceElement = new StackTraceElement("co.elastic.apm.test.TestClass",
            "testMethod", "TestClass.java", 34);
        String json = serializer.toJsonString(stackTraceElement);
        JsonNode stackTraceElementParsed = objectMapper.readTree(json);
        assertThat(stackTraceElementParsed.get("filename").textValue()).isEqualTo("TestClass.java");
        assertThat(stackTraceElementParsed.get("function").textValue()).isEqualTo("testMethod");
        assertThat(stackTraceElementParsed.get("library_frame").booleanValue()).isFalse();
        assertThat(stackTraceElementParsed.get("lineno").intValue()).isEqualTo(34);
        assertThat(stackTraceElementParsed.get("module").textValue()).isEqualTo("co.elastic.apm.test");

        stackTraceElement = new StackTraceElement("co.elastic.TestClass",
            "testMethod", "TestClass.java", 34);
        json = serializer.toJsonString(stackTraceElement);
        stackTraceElementParsed = objectMapper.readTree(json);
        assertThat(stackTraceElementParsed.get("library_frame").booleanValue()).isTrue();
        assertThat(stackTraceElementParsed.get("module").textValue()).isEqualTo("co.elastic");

        stackTraceElement = new StackTraceElement(".TestClass",
            "testMethod", "TestClass.java", 34);
        json = serializer.toJsonString(stackTraceElement);
        stackTraceElementParsed = objectMapper.readTree(json);
        assertThat(stackTraceElementParsed.get("module").textValue()).isEqualTo("");

        stackTraceElement = new StackTraceElement("TestClass",
            "testMethod", "TestClass.java", 34);
        json = serializer.toJsonString(stackTraceElement);
        stackTraceElementParsed = objectMapper.readTree(json);
        assertThat(stackTraceElementParsed.get("module").textValue()).isEqualTo("");
    }


    private List<JsonNode> getStackTrace() throws IOException {
        final Transaction transaction = tracer.startRootTransaction(getClass().getClassLoader());
        final Span span = transaction.createSpan();
        span.end();
        transaction.end();
        return StreamSupport.stream(objectMapper
                .readTree(serializer.toJsonString(span))
                .get("stacktrace")
                .spliterator(), false)
            .collect(Collectors.toList());
    }

}
