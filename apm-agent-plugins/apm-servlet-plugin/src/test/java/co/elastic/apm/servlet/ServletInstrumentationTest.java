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

import co.elastic.apm.MockReporter;
import co.elastic.apm.bci.ElasticApmAgent;
import co.elastic.apm.bci.ElasticApmInstrumentation;
import co.elastic.apm.configuration.SpyConfiguration;
import co.elastic.apm.impl.ElasticApmTracer;
import co.elastic.apm.impl.ElasticApmTracerBuilder;
import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import okhttp3.OkHttpClient;
import okhttp3.Response;
import org.eclipse.jetty.server.NetworkConnector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
import java.util.concurrent.TimeUnit;

import static net.bytebuddy.matcher.ElementMatchers.none;
import static org.assertj.core.api.Assertions.assertThat;

class ServletInstrumentationTest {

    private Server server;
    private OkHttpClient httpClient;

    @BeforeEach
    void setUp() throws Exception {
        server = new Server();
        server.addConnector(new ServerConnector(server));
        final ServletHandler handler = new ServletHandler();
        handler.addServletWithMapping(TestServlet.class, "/");
        handler.addFilterWithMapping(TestFilter.class, "/", EnumSet.of(DispatcherType.REQUEST));
        server.setHandler(handler);
        server.start();

        httpClient = new OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.SECONDS)
            .connectTimeout(0, TimeUnit.SECONDS)
            .build();
    }

    @AfterEach
    void tearDown() throws Exception {
        ElasticApmAgent.reset();
        server.stop();
    }

    @Test
    void testServletInstrumentation() throws IOException {
        testInstrumentation(new ServletInstrumentation(), 1);
    }

    @Test
    void testFilterChainInstrumentation() throws IOException {
        testInstrumentation(new FilterChainInstrumentation(), 1);
    }

    @Test
    void testNoopInstrumentation() throws IOException {
        testInstrumentation(new NoopInstrumentation(), 0);
    }

    private void testInstrumentation(ElasticApmInstrumentation instrumentation, int expectedTransactions) throws IOException {
        final MockReporter reporter = new MockReporter();
        ElasticApmAgent.initInstrumentation(new ElasticApmTracerBuilder()
            .configurationRegistry(SpyConfiguration.createSpyConfig())
            .reporter(reporter)
            .build(), ByteBuddyAgent.install(), Collections.singleton(instrumentation));

        final Response response = httpClient.newCall(new okhttp3.Request.Builder().url("http://localhost:" + getPort()).build()).execute();
        assertThat(response.code()).isEqualTo(200);
        assertThat(reporter.getTransactions()).hasSize(expectedTransactions);
    }

    private int getPort() {
        return ((NetworkConnector) server.getConnectors()[0]).getLocalPort();
    }

    public static class TestServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
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
