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
package co.elastic.apm.agent.servlet;

import co.elastic.apm.agent.AbstractServletTest;
import co.elastic.apm.agent.bci.ElasticApmAgent;
import co.elastic.apm.agent.bci.ElasticApmInstrumentation;
import co.elastic.apm.agent.configuration.SpyConfiguration;
import co.elastic.apm.agent.impl.ElasticApmTracerBuilder;
import co.elastic.apm.agent.matcher.WildcardMatcher;
import co.elastic.apm.agent.web.WebConfiguration;
import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import okhttp3.Response;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.stagemonitor.configuration.ConfigurationRegistry;

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
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

import static net.bytebuddy.matcher.ElementMatchers.none;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class ServletInstrumentationTest extends AbstractServletTest {

    @AfterEach
    final void afterEach() {
        ElasticApmAgent.reset();
    }

    @BeforeEach
    void init() throws IOException {
        // makes sure Servlet#init is called
        // the problem is that the tracer is re-created for each test run and thus does not contain service names from previous runs
        get("/init");
    }

    @Override
    protected void setUpHandler(ServletContextHandler handler) {
        handler.setDisplayName(getClass().getSimpleName());
        handler.setClassLoader(getClass().getClassLoader());
        handler.addServlet(EnsureInitServlet.class, "/init");
        handler.addServlet(TestServlet.class, "/test");
        handler.addServlet(BaseTestServlet.class, "/base");
        handler.addServlet(ForwardingServlet.class, "/forward");
        handler.addServlet(IncludingServlet.class, "/include");
        handler.addFilter(TestFilter.class, "/*", EnumSet.of(DispatcherType.REQUEST));
    }

    @Test
    void testServletInstrumentation() throws Exception {
        testInstrumentation(new ServletInstrumentation(), 1, "/test");
    }

    @Test
    void testBaseServletInstrumentation() throws Exception {
        testInstrumentation(new ServletInstrumentation(), 1, "/base");
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
            assertThat(reporter.getTransactions().stream().map(transaction -> transaction.getTraceContext().getServiceName()).distinct()).containsExactly(getClass().getSimpleName());
        }
        assertThat(reporter.getTransactions()).hasSize(expectedTransactions);
    }

    private void initInstrumentation(ElasticApmInstrumentation instrumentation) {
        final ConfigurationRegistry config = SpyConfiguration.createSpyConfig();
        when(config.getConfig(WebConfiguration.class).getIgnoreUrls()).thenReturn(List.of(WildcardMatcher.valueOf("/init")));
        ElasticApmAgent.initInstrumentation(new ElasticApmTracerBuilder()
            .configurationRegistry(config)
            .reporter(reporter)
            .build(), ByteBuddyAgent.install(), List.of(instrumentation, new ServletContextServiceNameInstrumentation()));
    }

    public static class TestServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            resp.getWriter().append("Hello World!");
        }
    }

    public static class EnsureInitServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException {
            init(getServletConfig());
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
            res.getWriter().append("Hello World!")
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
        public Collection<String> getInstrumentationGroupNames() {
            return Collections.singleton("noop");
        }
    }
}
