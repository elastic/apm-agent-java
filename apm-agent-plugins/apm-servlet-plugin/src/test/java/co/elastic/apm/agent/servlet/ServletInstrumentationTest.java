/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2020 Elastic and contributors
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

import co.elastic.apm.agent.impl.TracerInternalApiUtils;
import okhttp3.Response;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.junit.jupiter.api.Test;

import javax.servlet.DispatcherType;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.Servlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;

class ServletInstrumentationTest extends AbstractServletTest {

    @Override
    protected void setUpHandler(ServletContextHandler handler) {
        handler.setDisplayName(getClass().getSimpleName());
        handler.setClassLoader(getClass().getClassLoader());
        handler.addServlet(TestServlet.class, "/filter/test");
        handler.addServlet(TestServlet.class, "/test");
        handler.addServlet(BaseTestServlet.class, "/base");
        handler.addServlet(ForwardingServlet.class, "/forward");
        handler.addServlet(IncludingServlet.class, "/include");
        handler.addFilter(TestFilter.class, "/filter/*", EnumSet.of(DispatcherType.REQUEST));
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
    void testForward() throws Exception {
        callServlet(1, "/forward");
    }

    @Test
    void testInclude() throws Exception {
        callServlet(1, "/include");
    }

    private void callServlet(int expectedTransactions, String path) throws IOException, InterruptedException {
        final Response response = get(path);
        assertThat(response.code()).isEqualTo(200);
        assertThat(response.body().string()).isEqualTo("Hello World!");

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
    }


    public static class TestServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            resp.getWriter().append("Hello World!");
        }
    }

    public static class ForwardingServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            req.getRequestDispatcher("/test").forward(req, resp);
        }
    }

    public static class IncludingServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            req.getRequestDispatcher("/test").include(req, resp);
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
