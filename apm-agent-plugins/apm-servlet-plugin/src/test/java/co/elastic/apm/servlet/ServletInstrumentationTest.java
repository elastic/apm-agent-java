/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 Elastic and contributors
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
package co.elastic.apm.servlet;

import co.elastic.apm.bci.ElasticApmAgent;
import co.elastic.apm.bci.ElasticApmInstrumentation;
import co.elastic.apm.configuration.SpyConfiguration;
import co.elastic.apm.impl.ElasticApmTracerBuilder;
import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import okhttp3.Response;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.servlet.DispatcherType;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.EnumSet;

import static net.bytebuddy.matcher.ElementMatchers.none;
import static org.assertj.core.api.Assertions.assertThat;

class ServletInstrumentationTest extends AbstractServletTest {

    @AfterEach
    final void afterEach() {
        ElasticApmAgent.reset();
    }

    @Override
    protected void setUpHandler(ServletContextHandler handler) {
        handler.addServlet(TestServlet.class, "/test");
        handler.addServlet(ForwardingServlet.class, "/forward");
        handler.addServlet(IncludingServlet.class, "/include");
        handler.addFilter(TestFilter.class, "/*", EnumSet.of(DispatcherType.REQUEST));
    }

    @Test
    void testServletInstrumentation() throws Exception {
        testInstrumentation(new ServletInstrumentation(), 1, "/test");
    }

    @Test
    void testFilterChainInstrumentation() throws Exception {
        testInstrumentation(new FilterChainInstrumentation(), 1, "/test");
    }

    @Test
    void testNoopInstrumentation() throws Exception {
        testInstrumentation(new NoopInstrumentation(), 0, "/test");
    }

    @Test
    void testForward() throws Exception {
        testInstrumentation(new ServletInstrumentation(), 1, "/forward");
    }

    @Test
    void testInclude() throws Exception {
        testInstrumentation(new ServletInstrumentation(), 1, "/include");
    }

    private void testInstrumentation(ElasticApmInstrumentation instrumentation, int expectedTransactions, String path) throws IOException, InterruptedException {
        initInstrumentation(instrumentation);

        final Response response = get(path);
        assertThat(response.code()).isEqualTo(200);
        assertThat(response.body().string()).isEqualTo("Hello World!");
        if (expectedTransactions > 0) {
            reporter.getFirstTransaction(500);
        }
        assertThat(reporter.getTransactions()).hasSize(expectedTransactions);
    }

    private void initInstrumentation(ElasticApmInstrumentation instrumentation) {
        ElasticApmAgent.initInstrumentation(new ElasticApmTracerBuilder()
            .configurationRegistry(SpyConfiguration.createSpyConfig())
            .reporter(reporter)
            .build(), ByteBuddyAgent.install(), Collections.singleton(instrumentation));
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

    private static class NoopInstrumentation extends ElasticApmInstrumentation {
        @Override
        public ElementMatcher<? super TypeDescription> getTypeMatcher() {
            return none();
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return none();
        }

        @Override
        public String getInstrumentationGroupName() {
            return "noop";
        }
    }
}
