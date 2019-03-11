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
package org.example.stacktrace;

import co.elastic.apm.agent.MockTracer;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.stacktrace.StacktraceConfiguration;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.TraceContext;
import co.elastic.apm.agent.impl.transaction.Transaction;
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
import static org.mockito.Mockito.when;

/**
 * This class intentionally is not inside the co.elastic.apm package. This is to test the library_frame feature.
 */
class StacktraceSerializationTest {

    private List<JsonNode> stacktrace;
    private StacktraceConfiguration stacktraceConfiguration;
    private DslJsonSerializer serializer;
    private ObjectMapper objectMapper;
    private ElasticApmTracer tracer;

    @BeforeEach
    void setUp() throws IOException {
        tracer = MockTracer.createRealTracer();
        stacktraceConfiguration = tracer.getConfig(StacktraceConfiguration.class);
        // always enable
        when(stacktraceConfiguration.getSpanFramesMinDurationMs()).thenReturn(-1L);
        serializer = new DslJsonSerializer(stacktraceConfiguration);
        objectMapper = new ObjectMapper();
        stacktrace = getStackTrace();
    }

    @Test
    void fillStackTrace() {
        assertThat(stacktrace).isNotEmpty();
        // even though the stacktrace is captured within our tracer class, the first method should be getStackTrace
        assertThat(stacktrace.get(0).get("abs_path").textValue()).doesNotStartWith("co.elastic");
        assertThat(stacktrace.get(0).get("function").textValue()).isEqualTo("getStackTrace");
        assertThat(stacktrace.stream().filter(st -> st.get("abs_path").textValue().endsWith("StacktraceSerializationTest"))).isNotEmpty();
    }

    @Test
    void testAppFrame() throws Exception {
        when(stacktraceConfiguration.getApplicationPackages()).thenReturn(Collections.singletonList("org.example.stacktrace"));
        stacktrace = getStackTrace();
        Optional<JsonNode> thisMethodsFrame = stacktrace.stream()
            .filter(st -> st.get("abs_path").textValue().startsWith(getClass().getName()))
            .findAny();
        assertThat(thisMethodsFrame).isPresent();
        assertThat(thisMethodsFrame.get().get("library_frame").booleanValue()).isFalse();
    }

    @Test
    void testNoAppFrame() {
        Optional<JsonNode> thisMethodsFrame = stacktrace.stream()
            .filter(st -> st.get("abs_path").textValue().startsWith(getClass().getName()))
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
            softly.assertThat(stacktrace.stream().filter(st -> st.get("abs_path").textValue().contains("java.lang.reflect."))).isEmpty();
            softly.assertThat(stacktrace.stream().filter(st -> st.get("abs_path").textValue().contains("sun."))).isEmpty();
        });
    }

    private List<JsonNode> getStackTrace() throws IOException {
        final Transaction transaction = tracer.startTransaction(TraceContext.asRoot(), null, null);
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
