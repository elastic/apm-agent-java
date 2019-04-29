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
package co.elastic.apm.agent.servlet;

import co.elastic.apm.agent.AbstractInstrumentationTest;
import co.elastic.apm.agent.impl.error.ErrorCapture;
import co.elastic.apm.agent.impl.transaction.TraceContext;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.report.serialize.DslJsonSerializer;
import co.elastic.apm.agent.util.PotentiallyMultiValuedMap;
import co.elastic.apm.agent.web.WebConfiguration;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import javax.annotation.Nonnull;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class TestRequestBodyCapturing extends AbstractInstrumentationTest {

    private static final byte[] BUFFER = new byte[1024];
    private InputStreamConsumer streamConsumer;
    private InputStreamCloser streamCloser;
    private WebConfiguration webConfiguration;
    private MockFilterChain filterChain;

    static Stream<InputStreamConsumer> streamConsumers() {
        return Stream.of(
            InputStream::read,
            is -> is.read(BUFFER),
            is -> is.read(BUFFER, 0, BUFFER.length),
            is -> is.read(BUFFER, 42, BUFFER.length / 2),
            is -> is.readLine(BUFFER, 0, BUFFER.length),
            is -> {
                is.readNBytes(BUFFER, 0, BUFFER.length);
                return -1;
            },
            is -> {
                is.readAllBytes();
                return -1;
            }
        );
    }

    @BeforeEach
    void setUp() {
        webConfiguration = tracer.getConfig(WebConfiguration.class);
        when(webConfiguration.getCaptureBody()).thenReturn(WebConfiguration.EventType.ALL);
        filterChain = new MockFilterChain(new HttpServlet() {
            @Override
            protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
                final ServletInputStream is = req.getInputStream();
                int read;
                do {
                    read = streamConsumer.read(is);
                } while (read != -1);
                streamCloser.close(is);
            }
        });
        streamConsumer = is -> is.readLine(BUFFER, 0, BUFFER.length);
        streamCloser = InputStream::close;

    }

    @ParameterizedTest
    @MethodSource("streamConsumers")
    void testReadTextPlain(InputStreamConsumer consumer) throws Exception {
        streamConsumer = consumer;
        executeRequest(filterChain, "foo\nbar".getBytes(StandardCharsets.UTF_8), "text/plain");

        final Object body = reporter.getFirstTransaction().getContext().getRequest().getBody();
        assertThat(body).isNotNull();
        assertThat(body.toString()).isEqualTo("foo\nbar");
    }

    @Test
    void testCaptureBodyOff() throws Exception {
        when(webConfiguration.getCaptureBody()).thenReturn(WebConfiguration.EventType.OFF);
        executeRequest(filterChain, "foo".getBytes(StandardCharsets.UTF_8), "text/plain");

        final Object body = reporter.getFirstTransaction().getContext().getRequest().getBody();
        assertThat(body).isNotNull();
        assertThat(body.toString()).isEqualTo("[REDACTED]");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ALL", "TRANSACTIONS", "ERRORS"})
    void testCaptureBodyNotOff(WebConfiguration.EventType eventType) throws Exception {
        streamCloser = is -> { throw new RuntimeException(); };

        when(webConfiguration.getCaptureBody()).thenReturn(eventType);
        executeRequest(filterChain, "foo".getBytes(StandardCharsets.UTF_8), "text/plain");

        final Transaction transaction = reporter.getFirstTransaction();
        final Object body = transaction.getContext().getRequest().getBody();
        assertThat(body).isNotNull();
        // this is not [REDACTED] in this test as the BodyProcessor is not active in MockReporter
        assertThat(body.toString()).isEqualTo("foo");

        final ErrorCapture error = reporter.getFirstError();
        assertThat(error).isNotNull();
        assertThat(error.getContext().getRequest().getBody().toString()).isEqualTo("foo");
    }

    @Test
    void testReadWithoutClose() throws Exception {
        streamCloser = is -> {};
        executeRequest(filterChain, "foo".getBytes(StandardCharsets.UTF_8), "text/plain");

        final Object body = reporter.getFirstTransaction().getContext().getRequest().getBody();
        assertThat(body).isNotNull();
        assertThat(body.toString()).isEqualTo("foo");
    }

    @Test
    void testReadLongText() throws Exception {
        final byte[] longBody = RandomStringUtils.randomAlphanumeric(DslJsonSerializer.MAX_LONG_STRING_VALUE_LENGTH * 2).getBytes(StandardCharsets.UTF_8);
        executeRequest(filterChain, longBody, "text/plain");

        final Object body = reporter.getFirstTransaction().getContext().getRequest().getBody();
        assertThat(body).isNotNull();
        assertThat(body.toString().length()).isEqualTo(DslJsonSerializer.MAX_LONG_STRING_VALUE_LENGTH);
    }

    @Test
    void testReadTextPlainNonUtf8() throws Exception {
        executeRequest(filterChain, "foo{}".getBytes(StandardCharsets.UTF_16), "text/plain");

        final Object body = reporter.getFirstTransaction().getContext().getRequest().getBody();
        assertThat(body).isNotNull();
        assertThat(body.toString()).isEqualTo("[Non UTF-8 data]");
    }

    @Test
    void testReadUnknownContentType() throws Exception {
        executeRequest(filterChain, "foo".getBytes(StandardCharsets.UTF_8), "application/unknown");

        final Object body = reporter.getFirstTransaction().getContext().getRequest().getBody();
        assertThat(body).isNotNull();
        assertThat(body.toString()).isEqualTo("[REDACTED]");
    }

    @Test
    void testNotReading() throws Exception {
        MockFilterChain filterChain = new MockFilterChain(new HttpServlet() {
            @Override
            protected void doPost(HttpServletRequest req, HttpServletResponse resp) {
            }
        });

        executeRequest(filterChain, "foo".getBytes(StandardCharsets.UTF_8), "text/plain");

        final Object body = reporter.getFirstTransaction().getContext().getRequest().getBody();
        assertThat(body).isNotNull();
        assertThat(body.toString()).isEqualTo("");
    }

    @Test
    void testReadOtherStream() throws Exception {
        MockFilterChain filterChain = new MockFilterChain(new HttpServlet() {
            @Override
            protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
                new ByteArrayInputStream("foo".getBytes(StandardCharsets.UTF_8)).read(new byte[42]);
            }
        });

        executeRequest(filterChain, "foo".getBytes(StandardCharsets.UTF_8), "text/plain");

        final Object body = reporter.getFirstTransaction().getContext().getRequest().getBody();
        assertThat(body).isNotNull();
        assertThat(body.toString()).isEqualTo("");
    }

    @Test
    void testTrackPostParams() throws IOException, ServletException {
        when(webConfiguration.getCaptureBody()).thenReturn(WebConfiguration.EventType.ALL);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/foo/bar");
        request.addParameter("foo", "bar");
        request.addParameter("baz", "qux", "quux");
        request.addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE + ";charset=uft-8");

        filterChain.doFilter(request, new MockHttpServletResponse());
        assertThat(reporter.getFirstTransaction().getContext().getRequest().getBody()).isInstanceOf(PotentiallyMultiValuedMap.class);
        PotentiallyMultiValuedMap params = (PotentiallyMultiValuedMap) reporter.getFirstTransaction().getContext().getRequest().getBody();
        assertThat(params.get("foo")).isEqualTo("bar");
        assertThat(params.get("baz")).isEqualTo(Arrays.asList("qux", "quux"));
    }

    @Test
    void testTrackPostParamsDisabled() throws IOException, ServletException {
        when(webConfiguration.getCaptureBody()).thenReturn(WebConfiguration.EventType.ALL);
        when(webConfiguration.getCaptureContentTypes()).thenReturn(Collections.emptyList());
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/foo/bar");
        request.addParameter("foo", "bar");
        request.addParameter("baz", "qux", "quux");
        request.addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE + ";charset=uft-8");

        filterChain.doFilter(request, new MockHttpServletResponse());
        assertThat(reporter.getFirstTransaction().getContext().getRequest().getBody()).isEqualTo("[REDACTED]");
    }

    @Test
    void testNoExplicitEndOfInput() {
        final Transaction transaction = tracer.startTransaction(TraceContext.asRoot(), null, getClass().getClassLoader());
        transaction.getContext().getRequest().withBodyBuffer();
        transaction.end();
        assertThat(reporter.getFirstTransaction().getContext().getRequest().getBody().toString()).isEqualTo("");
    }

    private void executeRequest(MockFilterChain filterChain, byte[] bytes, String contentType) throws IOException, ServletException {
        try {
            filterChain.doFilter(createMockRequest(bytes, contentType), new MockHttpServletResponse());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Nonnull
    private HttpServletRequest createMockRequest(byte[] bytes, String contentType) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/foo");
        request.setContent(bytes);
        request.setContentType(contentType);
        return new HttpServletRequestWrapper(request);
    }

    interface InputStreamConsumer {
        int read(ServletInputStream inputStream) throws IOException;
    }

    interface InputStreamCloser {
        void close(ServletInputStream inputStream) throws IOException;
    }
}
