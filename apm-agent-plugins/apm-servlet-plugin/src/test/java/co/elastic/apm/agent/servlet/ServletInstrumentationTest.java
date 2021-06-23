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
package co.elastic.apm.agent.servlet;

import co.elastic.apm.agent.configuration.CoreConfiguration;
import co.elastic.apm.agent.impl.TracerInternalApiUtils;
import co.elastic.apm.agent.impl.context.web.ResultUtil;
import co.elastic.apm.agent.impl.transaction.Span;
import okhttp3.Response;
import org.eclipse.jetty.servlet.ErrorPageErrorHandler;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.servlet.DispatcherType;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.RequestDispatcher;
import javax.servlet.Servlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.EnumSet;

import static co.elastic.apm.agent.servlet.RequestDispatcherSpanType.ERROR;
import static co.elastic.apm.agent.servlet.RequestDispatcherSpanType.FORWARD;
import static co.elastic.apm.agent.servlet.RequestDispatcherSpanType.INCLUDE;
import static co.elastic.apm.agent.servlet.ServletApiAdvice.SPAN_SUBTYPE;
import static co.elastic.apm.agent.servlet.ServletApiAdvice.SPAN_TYPE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class ServletInstrumentationTest extends AbstractServletTest {

    @BeforeEach
    void beforeEach() {
        // servlet type & subtype are nor part of shared spec
        reporter.disableCheckStrictSpanType();
    }

    @Override
    protected void setUpHandler(ServletContextHandler handler) {
        handler.setDisplayName(getClass().getSimpleName());
        handler.setClassLoader(getClass().getClassLoader());
        handler.addServlet(TestServlet.class, "/filter/test");
        handler.addServlet(TestServlet.class, "/test");
        handler.addServlet(BaseTestServlet.class, "/base");
        handler.addServlet(ForwardingServlet.class, "/forward");
        handler.addServlet(ForwardingServletWithPathInfo.class, "/forward/path");
        handler.addServlet(IncludingServlet.class, "/include");
        handler.addServlet(IncludingServletWithPathInfo.class, "/include/path");
        handler.addServlet(TestServletWithPathInfo.class, "/test/path/*");
        handler.addFilter(TestFilter.class, "/filter/*", EnumSet.of(DispatcherType.REQUEST));
        handler.addServlet(ErrorServlet.class, "/error");
        handler.addServlet(ServletWithRuntimeException.class, "/throw-error");
        ErrorPageErrorHandler errorHandler = new ErrorPageErrorHandler();
        errorHandler.addErrorPage(404, "/error");
        errorHandler.addErrorPage(500, "/error");
        handler.setErrorHandler(errorHandler);
    }

    @Test
    void testServletInstrumentation() throws Exception {
        callServlet(1, "/test");
    }

    @Test
    void testBaseServletInstrumentation() throws Exception {
        callServlet(1, "/base");
    }

    @Test
    void testFilterChainInstrumentation() throws Exception {
        callServlet(1, "/filter/test");
    }

    @Test
    void testNoopInstrumentation() throws Exception {
        TracerInternalApiUtils.pauseTracer(tracer);
        callServlet(0, "/test");
    }

    @Test
    void testForward_verifyThatSpanNameContainsOriginalServletPath() throws Exception {
        callServlet(1, "/forward");
        assertThat(reporter.getSpans().size()).isEqualTo(1);
        assertThat(reporter.getFirstSpan().getNameAsString()).isEqualTo("FORWARD /test");
    }

    @Test
    void testInclude_verifyThatSpanNameContainsTargetServletPath() throws Exception {
        callServlet(1, "/include");
        assertThat(reporter.getSpans().size()).isEqualTo(1);
        assertThat(reporter.getFirstSpan().getNameAsString()).isEqualTo("INCLUDE /test");
    }

    @Test
    void testClientError() throws Exception {
        callServlet(1, "/unknown", "Hello Error!", 404);
        assertThat(reporter.getSpans().size()).isEqualTo(1);
        Span span = reporter.getFirstSpan();
        assertThat(span.getType()).isEqualTo(SPAN_TYPE);
        assertThat(span.getSubtype()).isEqualTo(SPAN_SUBTYPE);
        assertThat(span.getAction()).isEqualTo(ERROR.getAction());
        assertThat(span.getNameAsString()).isEqualTo("ERROR /error");
        assertThat(reporter.getErrors().size()).isEqualTo(1);
        assertThat(reporter.getFirstError().getException()).isInstanceOf(ErrorServlet.HelloException.class);
    }

    @Test
    void testForward_DispatchSpansDisabled() throws Exception {
        when(getConfig().getConfig(CoreConfiguration.class).getDisabledInstrumentations())
            .thenReturn(Collections.singletonList(ServletInstrumentation.SERVLET_API_DISPATCH));
        callServlet(1, "/forward");
        assertThat(reporter.getSpans()).isEmpty();
    }

    @Test
    void testInclude_DispatchSpansDisabled() throws Exception {
        when(getConfig().getConfig(CoreConfiguration.class).getDisabledInstrumentations())
            .thenReturn(Collections.singletonList(ServletInstrumentation.SERVLET_API_DISPATCH));
        callServlet(1, "/include");
        assertThat(reporter.getSpans()).isEmpty();
    }

    @Test
    void testClientError_DispatchSpansDisabled() throws Exception {
        when(getConfig().getConfig(CoreConfiguration.class).getDisabledInstrumentations())
            .thenReturn(Collections.singletonList(ServletInstrumentation.SERVLET_API_DISPATCH));
        callServlet(1, "/unknown", "Hello Error!", 404);
        assertThat(reporter.getSpans()).isEmpty();
        assertThat(reporter.getErrors().size()).isEqualTo(1);
        assertThat(reporter.getFirstError().getException()).isInstanceOf(ErrorServlet.HelloException.class);
    }

    @Test
    void testServerError() throws Exception {
        callServlet(1, "/throw-error", "Hello Error!", 500);
        // Because the servlet itself throws an Exception, the server ends the transaction before it delegates to the
        // error page in this case, so we don't create a span
        assertThat(reporter.getSpans()).isEmpty();
        assertThat(reporter.getErrors().size()).isEqualTo(1);
        assertThat(reporter.getFirstError().getException()).isInstanceOf(ServletWithRuntimeException.HelloRuntimeException.class);
    }

    @Test
    void testServletInstrumentationWithPathInfo() throws Exception {
        callServlet(1, "/test/path/name", "Hello World! /name", 200);
    }

    @Test
    void testForwardWithPathInfo_verifyThatSpanNameContainsOriginalServletPathAndPathInfo() throws Exception {
        callServlet(1, "/forward/path", "Hello World! /forward-path-info", 200);
        assertThat(reporter.getSpans().size()).isEqualTo(1);
        Span span = reporter.getFirstSpan();
        assertThat(span.getType()).isEqualTo(SPAN_TYPE);
        assertThat(span.getSubtype()).isEqualTo(SPAN_SUBTYPE);
        assertThat(span.getAction()).isEqualTo(FORWARD.getAction());
        assertThat(span.getNameAsString()).isEqualTo("FORWARD /test/path/forward-path-info");
    }

    @Test
    void testIncludeWithPathInfo_verifyThatSpanNameContainsOriginalServletPathAndPathInfo() throws Exception {
        callServlet(1, "/include/path", "Hello World! /include-path-info", 200);
        assertThat(reporter.getSpans().size()).isEqualTo(1);
        Span span = reporter.getFirstSpan();
        assertThat(span.getType()).isEqualTo(SPAN_TYPE);
        assertThat(span.getSubtype()).isEqualTo(SPAN_SUBTYPE);
        assertThat(span.getAction()).isEqualTo(INCLUDE.getAction());
        assertThat(span.getNameAsString()).isEqualTo("INCLUDE /test/path/include-path-info");
    }

    private void callServlet(int expectedTransactions, String path) throws IOException, InterruptedException {
        callServlet(expectedTransactions, path, "Hello World!", 200);
    }

    private void callServlet(int expectedTransactions, String path, String expectedResponseBody, int expectedStatusCode) throws IOException, InterruptedException {
        final Response response = get(path);
        assertThat(response.code()).isEqualTo(expectedStatusCode);
        assertThat(response.body().string()).isEqualTo(expectedResponseBody);

        if (expectedTransactions > 0) {
            reporter.getFirstTransaction(500);
            assertThat(reporter.getTransactions()
                .stream()
                .map(transaction -> transaction.getTraceContext().getServiceName())
                .distinct())
                .describedAs("transaction service name should be inherited from test class name")
                .containsExactly(getClass().getSimpleName());
        }
        assertThat(reporter.getTransactions())
            .hasSize(expectedTransactions);

        reporter.getTransactions().stream().forEach( t -> {
            assertThat(t.getResult()).isEqualTo(ResultUtil.getResultByHttpStatus(expectedStatusCode));
            assertThat(t.getOutcome()).isEqualTo(ResultUtil.getOutcomeByHttpServerStatus(expectedStatusCode));
        });
    }


    public static class TestServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            resp.getWriter().append("Hello World!");
        }
    }

    public static class TestServletWithPathInfo extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            String pathInfo = req.getPathInfo();
            String includePathInfoHeader = (String) req.getAttribute(RequestDispatcher.INCLUDE_PATH_INFO);
            resp.getWriter().append("Hello World! " + (pathInfo != null ? pathInfo : includePathInfoHeader != null ? includePathInfoHeader : ""));
        }
    }

    public static class ForwardingServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            req.getRequestDispatcher("/test").forward(req, resp);
        }
    }

    public static class ForwardingServletWithPathInfo extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            req.getRequestDispatcher("/test/path/forward-path-info").forward(req, resp);
        }
    }

    public static class IncludingServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            req.getRequestDispatcher("/test").include(req, resp);
        }
    }

    public static class IncludingServletWithPathInfo extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            req.getRequestDispatcher("/test/path/include-path-info").include(req, resp);
        }
    }

    public static class ErrorServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            System.out.println("SERVLET = ErrorServlet");
            req.setAttribute(RequestDispatcher.ERROR_EXCEPTION, new HelloException());
            PrintWriter out = resp.getWriter();
            out.print("Hello Error!");
            out.close();
        }

        private static class HelloException extends RuntimeException {
        }
    }

    public static class ServletWithRuntimeException extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            throw new HelloRuntimeException();
        }

        private static class HelloRuntimeException extends RuntimeException {
        }
    }

    public static class BaseTestServlet implements Servlet {
        @Override
        public void init(ServletConfig config) {

        }

        @Override
        public ServletConfig getServletConfig() {
            return null;
        }

        @Override
        public void service(ServletRequest req, ServletResponse res) throws IOException {
            res.getWriter()
                .append("Hello World!")
                .flush();
            res.getBufferSize();
        }

        @Override
        public String getServletInfo() {
            return null;
        }

        @Override
        public void destroy() {

        }
    }

    public static class TestFilter implements Filter {
        @Override
        public void init(FilterConfig filterConfig) {

        }

        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
            chain.doFilter(request, response);
        }

        @Override
        public void destroy() {

        }
    }
}
