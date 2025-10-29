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
package co.elastic.apm.agent.httpclient;

import co.elastic.apm.agent.AbstractInstrumentationTest;
import co.elastic.apm.agent.configuration.CoreConfigurationImpl;
import co.elastic.apm.agent.impl.TextHeaderMapAccessor;
import co.elastic.apm.agent.impl.context.BodyCaptureImpl;
import co.elastic.apm.agent.impl.context.DestinationImpl;
import co.elastic.apm.agent.impl.context.HttpImpl;
import co.elastic.apm.agent.impl.transaction.SpanImpl;
import co.elastic.apm.agent.impl.transaction.TraceContextImpl;
import co.elastic.apm.agent.impl.transaction.TransactionImpl;
import co.elastic.apm.agent.sdk.internal.util.IOUtils;
import co.elastic.apm.agent.tracer.Outcome;
import co.elastic.apm.agent.tracer.Scope;
import co.elastic.apm.agent.tracer.TraceState;
import co.elastic.apm.agent.tracer.configuration.WebConfiguration;
import co.elastic.apm.agent.tracer.dispatch.TextHeaderGetter;
import co.elastic.apm.agent.tracer.util.ResultUtil;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.http.HttpHeader;
import com.github.tomakehurst.wiremock.http.HttpHeaders;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import javax.annotation.Nullable;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static co.elastic.apm.agent.impl.transaction.TraceContextImpl.W3C_TRACE_PARENT_TEXTUAL_HEADER_NAME;
import static co.elastic.apm.agent.testutils.assertions.Assertions.assertThat;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.seeOther;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.mockito.Mockito.doReturn;

public abstract class AbstractHttpClientInstrumentationTest extends AbstractInstrumentationTest {

    @RegisterExtension
    public static WireMockExtension wireMockRule = WireMockExtension.newInstance()
        .options(WireMockConfiguration.wireMockConfig().dynamicPort().dynamicHttpsPort())
        .build();

    private TraceState<?> emptyContext;

    @BeforeEach
    public final void setUpWiremock() {
        // ensure that HTTP spans outcome is not unknown
        wireMockRule.stubFor(any(urlEqualTo("/"))
            .willReturn(dummyResponse()
                .withStatus(200)));
        wireMockRule.stubFor(any(urlEqualTo("/dummy"))
            .willReturn(dummyResponse()
                .withStatus(200)));
        wireMockRule.stubFor(get(urlEqualTo("/error"))
            .willReturn(dummyResponse()
                .withStatus(515)));
        wireMockRule.stubFor(get(urlEqualTo("/redirect"))
            .willReturn(seeOther("/")));
        wireMockRule.stubFor(get(urlEqualTo("/circular-redirect"))
            .willReturn(seeOther("/circular-redirect")));

        emptyContext = tracer.currentContext();
        startTestRootTransaction("parent of http span");
    }

    protected ResponseDefinitionBuilder dummyResponse() {
        return aResponse()
            // old spring 3.0 require content type
            .withHeader("Content-Type", "text/plain")
            .withBody("hello");
    }

    @AfterEach
    public final void after() {
        TransactionImpl transaction = tracer.currentTransaction();
        assertThat(transaction).isNotNull();
        transaction.deactivate().end();
        assertThat(reporter.getTransactions()).hasSize(1);

        // reset reporter to avoid error state to propagate to the next test
        reporter.reset();
    }

    @Test
    public void testHttpCall() {
        String path = "/";
        performGetWithinTransaction(path);
        expectSpan(path).verify();
    }

    @Test
    public void testPostBodyCapture() throws Exception {
        if (!isBodyCapturingSupported()) {
            return;
        }
        byte[] content = "Hello World!".getBytes(StandardCharsets.UTF_8);

        // Ensure that the setting can be turned on dynamically by first issuing a request with the feature disabled
        doReturn(0).when(config.getConfig(WebConfiguration.class)).getCaptureClientRequestBytes();
        performPost(getBaseUrl() + "/dummy", content, "text/plain; charset=utf-8");
        expectSpan("/dummy")
            .withRequestBodySatisfying(body -> assertThat(body.hasContent()).isFalse())
            .verify();
        reporter.reset();

        doReturn(5).when(config.getConfig(WebConfiguration.class)).getCaptureClientRequestBytes();
        performPost(getBaseUrl() + "/", content, "text/plain; charset=utf-8");
        expectSpan("/")
            .withRequestBodySatisfying(body -> {
                List<ByteBuffer> buffer = body.getBody();
                assertThat(IOUtils.copyToByteArray(buffer)).isEqualTo("Hello".getBytes(StandardCharsets.UTF_8));
                assertThat(Objects.toString(body.getCharset())).isEqualTo("utf-8");
            })
            .verify();
    }


    /**
     * This test verifies
     *
     * @throws Exception
     */
    @Test
    public void testPostBodyCaptureForExistingSpan() throws Exception {
        if (!isBodyCapturingSupported()) {
            return;
        }
        doReturn(1024).when(config.getConfig(WebConfiguration.class)).getCaptureClientRequestBytes();
        byte[] content = "Hello World!".getBytes(StandardCharsets.UTF_8);
        String path = "/";

        SpanImpl capture = createExitSpan("capture");
        capture.getContext().getHttp().getRequestBody().markEligibleForCapturing();
        capture.activate();
        try {
            performPost(getBaseUrl() + path, content, "application/json; charset=iso-8859-1");
        } finally {
            capture.deactivate().end();
        }

        //Do not not capture body for "noCapture" because it is not marked eligible
        SpanImpl noCapture = createExitSpan("no-capture");
        noCapture.activate();
        try {
            performPost(getBaseUrl() + path, content, "application/json; charset=iso-8859-1");
        } finally {
            noCapture.deactivate().end();
        }

        assertThat(reporter.getSpans())
            .containsExactly(capture, noCapture);

        BodyCaptureImpl captureBody = capture.getContext().getHttp().getRequestBody();
        assertThat(captureBody.hasContent()).isTrue();
        assertThat(Objects.toString(captureBody.getCharset())).isEqualTo("iso-8859-1");
        assertThat(IOUtils.copyToByteArray(captureBody.getBody())).isEqualTo(content);

        BodyCaptureImpl noCaptureBody = noCapture.getContext().getHttp().getRequestBody();
        assertThat(noCaptureBody.hasContent()).isFalse();
        assertThat(noCaptureBody.getCharset()).isNull();
    }

    @Test
    public void testDisabledOutgoingHeaders() {
        doReturn(true).when(config.getConfig(CoreConfigurationImpl.class)).isOutgoingTraceContextHeadersInjectionDisabled();
        String path = "/";
        performGetWithinTransaction(path);
        expectSpan(path).withoutTraceContextHeaders().verify();
    }

    @Test
    public void testContextPropagationFromExitParent() {
        String path = "/";
        SpanImpl exitSpan = createExitSpan("exit");
        try {
            exitSpan.activate();
            performGetWithinTransaction(path);
            verifyTraceContextHeaders(exitSpan, path);
            assertThat(reporter.getSpans()).isEmpty();
        } finally {
            exitSpan.deactivate().end();
        }
    }

    private static SpanImpl createExitSpan(String name) {
        SpanImpl exitSpan = Objects.requireNonNull(Objects.requireNonNull(Objects.requireNonNull(tracer.currentTransaction()).createExitSpan()));
        exitSpan.withName(name).withType("custom").withSubtype("exit");
        exitSpan.getContext().getDestination().withAddress("test-host").withPort(6000);
        exitSpan.getContext().getServiceTarget().withType("test-resource");
        return exitSpan;
    }

    @Test
    public void testBaggagePropagatedWithoutTrace() {
        TraceState<?> baggageOnly = emptyContext.withUpdatedBaggage()
            .put("foo", "bar")
            .buildContext();
        try (Scope scope = baggageOnly.activateInScope()) {
            assertThat(tracer.getActive()).isNull();
            performGetWithinTransaction("/");

            List<LoggedRequest> loggedRequests = findLoggedRequests("/");
            assertThat(loggedRequests).hasSize(1);
            assertThat(loggedRequests.get(0).getHeader("baggage")).isEqualTo("foo=bar");
            assertThat(reporter.getSpans()).isEmpty();
        }
    }

    @Test
    public void testHttpCallWithUserInfo() throws Exception {
        Assumptions.assumeTrue(isTestHttpCallWithUserInfoEnabled());

        performGet("http://user:passwd@localhost:" + wireMockRule.getPort() + "/");
        expectSpan("/").verify();
    }

    @Test
    public void testHttpCallWithIpv4() throws Exception {
        performGet("http://127.0.0.1:" + wireMockRule.getPort() + "/");
        expectSpan("/")
            .withHost("127.0.0.1")
            .verify();
    }

    @Test
    public void testHttpCallWithIpv6() throws Exception {
        Assumptions.assumeTrue(isIpv6Supported());

        performGet(String.format("http://[::1]:%d/", wireMockRule.getPort()));
        expectSpan("/")
            .withHost("[::1]")
            .verify();
    }

    @Test
    public void testNonExistingHttpCall() {
        String path = "/non-existing";
        performGetWithinTransaction(path);

        expectSpan(path)
            .withStatus(404)
            .verify();

    }

    @Test
    public void testErrorHttpCall() {
        String path = "/error";
        performGetWithinTransaction(path);

        expectSpan(path)
            .withStatus(515)
            .verify();

    }

    @Test
    public void testHttpCallRedirect() {
        Assumptions.assumeTrue(isRedirectFollowingSupported());

        String path = "/redirect";
        performGetWithinTransaction(path);

        SpanImpl span = expectSpan(path).verify();

        verifyTraceContextHeaders(span, "/redirect");
        verifyTraceContextHeaders(span, "/");
    }

    @Test
    public void testHttpCallCircularRedirect() {
        Assumptions.assumeTrue(isErrorOnCircularRedirectSupported());

        String path = "/circular-redirect";
        performGetWithinTransaction(path);

        SpanImpl span = reporter.getFirstSpan(500);
        assertThat(span).isNotNull();

        assertThat(reporter.getSpans()).hasSize(1);
        if (isRequireCheckErrorWhenCircularRedirect()) {
            assertThat(reporter.getErrors()).hasSize(1);
            assertThat(reporter.getFirstError().getException()).isNotNull();
            assertThat(reporter.getFirstError().getException().getClass()).isNotNull();
            assertThat(span.getOutcome()).isEqualTo(Outcome.FAILURE);
        }

        verifyTraceContextHeaders(span, "/circular-redirect");
    }

    // assumption
    protected boolean isIpv6Supported() {
        return true;
    }

    // assumption
    protected boolean isRedirectFollowingSupported() {
        return true;
    }

    // assumption
    protected boolean isBodyCapturingSupported() {
        return false;
    }

    // assumption
    protected boolean isErrorOnCircularRedirectSupported() {
        return isRedirectFollowingSupported();
    }

    // assumption
    protected boolean isTestHttpCallWithUserInfoEnabled() {
        return true;
    }

    // some http clients does not capture error
    public boolean isRequireCheckErrorWhenCircularRedirect() {
        return true;
    }

    protected String getBaseUrl() {
        return "http://localhost:" + wireMockRule.getPort();
    }

    protected void performGetWithinTransaction(String path) {
        try {
            performGet(getBaseUrl() + path);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public boolean isAsync() {
        return false;
    }

    protected abstract void performGet(String path) throws Exception;

    protected void performPost(String path, byte[] content, String contentTypeHeader) throws Exception {
        throw new UnsupportedOperationException();
    }

    protected VerifyBuilder expectSpan(String path) {
        return new VerifyBuilder(path);
    }

    protected class VerifyBuilder {
        private final String path;
        private String host = "localhost";
        private int status = 200;
        private boolean https = false;
        private boolean traceContextHeaders = true;
        private boolean requestExecuted = true;

        private Consumer<? super BodyCaptureImpl> requestBodyVerification = null;

        private VerifyBuilder(String path) {
            this.path = path;
        }

        public VerifyBuilder withHost(String host) {
            this.host = host;
            return this;
        }

        public VerifyBuilder withStatus(int status) {
            this.status = status;
            return this;
        }

        public VerifyBuilder withHttps() {
            this.https = true;
            return this;
        }

        public VerifyBuilder withoutTraceContextHeaders() {
            this.traceContextHeaders = false;
            return this;
        }

        public VerifyBuilder withoutRequestExecuted() {
            this.requestExecuted = false;
            // when request is not executed, we don't expect tracing headers
            this.traceContextHeaders = false;
            return this;
        }

        public VerifyBuilder withRequestBodySatisfying(Consumer<? super BodyCaptureImpl> requestBodyVerification) {
            this.requestBodyVerification = requestBodyVerification;
            return this;
        }

        public SpanImpl verify() {
            assertThat(reporter.getFirstSpan(500)).isNotNull();
            assertThat(reporter.getSpans()).hasSize(1);
            SpanImpl span = reporter.getSpans().get(0);

            int port = https ? wireMockRule.getHttpsPort() : wireMockRule.getPort();

            String schema = https ? "https" : "http";
            String baseUrl = String.format("%s://%s:%d", schema, host, port);

            HttpImpl httpContext = span.getContext().getHttp();

            assertThat(span)
                .hasName(String.format("%s %s", httpContext.getMethod(), host))
                .hasType("external")
                .hasSubType("http");

            assertThat(span.getAction()).isNull();

            assertThat(httpContext.getUrl().toString()).isEqualTo(baseUrl + path);
            assertThat(httpContext.getStatusCode()).isEqualTo(status);

            if (requestExecuted) {
                assertThat(span).hasOutcome(ResultUtil.getOutcomeByHttpClientStatus(status));
            } else {
                assertThat(span).hasOutcome(Outcome.FAILURE);
            }

            BodyCaptureImpl reqBody = span.getContext().getHttp().getRequestBody();
            if (requestBodyVerification != null) {
                requestBodyVerification.accept(reqBody);
            }

            if (isAsync()) {
                assertThat(span).isAsync();
            }

            DestinationImpl destination = span.getContext().getDestination();
            int addressStartIndex = (host.startsWith("[")) ? 1 : 0;
            int addressEndIndex = (host.endsWith("]")) ? host.length() - 1 : host.length();
            assertThat(destination.getAddress().toString()).isEqualTo(host.substring(addressStartIndex, addressEndIndex));
            assertThat(destination.getPort()).isEqualTo(port);

            assertThat(span.getContext().getServiceTarget())
                .hasName(String.format("%s:%d", host, port))
                .hasType("http")
                .hasNameOnlyDestinationResource();

            if (requestExecuted) {
                if (traceContextHeaders) {
                    verifyTraceContextHeaders(span, path);
                } else {
                    findLoggedRequests(path).forEach(request ->
                        assertThat(TraceContextImpl.containsTraceContextTextHeaders(request, HeaderAccessor.INSTANCE)).isFalse()
                    );
                }
            }

            return span;
        }
    }

    private void verifyTraceContextHeaders(SpanImpl span, String path) {
        Map<String, String> headerMap = new HashMap<>();
        span.propagateContext(headerMap, TextHeaderMapAccessor.INSTANCE, TextHeaderMapAccessor.INSTANCE);
        assertThat(headerMap).isNotEmpty();
        final List<LoggedRequest> loggedRequests = findLoggedRequests(path);
        loggedRequests.forEach(request -> {
            assertThat(TraceContextImpl.containsTraceContextTextHeaders(request, HeaderAccessor.INSTANCE)).isTrue();
            AtomicInteger headerCount = new AtomicInteger();
            HeaderAccessor.INSTANCE.forEach(
                W3C_TRACE_PARENT_TEXTUAL_HEADER_NAME,
                request,
                headerCount,
                (headerValue, state) -> state.incrementAndGet()
            );
            assertThat(headerCount.get()).isEqualTo(1);
            headerMap.forEach((key, value) -> assertThat(request.getHeader(key)).isEqualTo(value));
            TransactionImpl transaction = tracer.startChildTransaction(request, new HeaderAccessor(), AbstractHttpClientInstrumentationTest.class.getClassLoader());
            assertThat(transaction).isNotNull();
            assertThat(transaction.getTraceContext().getTraceId()).isEqualTo(span.getTraceContext().getTraceId());
            assertThat(transaction.getTraceContext().getParentId()).isEqualTo(span.getTraceContext().getId());
            transaction.decrementReferences(); //recycle transaction without reporting
        });
    }


    private List<LoggedRequest> findLoggedRequests(String path) {
        final AtomicReference<List<LoggedRequest>> loggedRequests = new AtomicReference<>();
        Awaitility.await()
            .pollInterval(1, TimeUnit.MILLISECONDS)
            .timeout(1000, TimeUnit.MILLISECONDS)
            .untilAsserted(() -> {
                List<LoggedRequest> tmp = wireMockRule.findAll(anyRequestedFor(urlPathEqualTo(path)));
                loggedRequests.set(tmp);
                assertThat(tmp).isNotEmpty();
            });
        return loggedRequests.get();
    }

    private static class HeaderAccessor implements TextHeaderGetter<LoggedRequest> {

        static final HeaderAccessor INSTANCE = new HeaderAccessor();

        @Nullable
        @Override
        public String getFirstHeader(String headerName, LoggedRequest loggedRequest) {
            return loggedRequest.getHeader(headerName);
        }

        @Override
        public <S> void forEach(String headerName, LoggedRequest loggedRequest, S state, HeaderConsumer<String, S> consumer) {
            HttpHeaders headers = loggedRequest.getHeaders();
            if (headers != null) {
                HttpHeader header = headers.getHeader(headerName);
                if (header.isPresent()) {
                    List<String> values = header.values();
                    for (String value : values) {
                        consumer.accept(value, state);
                    }
                }
            }
        }
    }
}
