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

import co.elastic.apm.AbstractServletTest;
import co.elastic.apm.bci.ElasticApmAgent;
import co.elastic.apm.configuration.SpyConfiguration;
import co.elastic.apm.impl.ElasticApmTracer;
import co.elastic.apm.impl.ElasticApmTracerBuilder;
import co.elastic.apm.impl.context.TransactionContext;
import net.bytebuddy.agent.ByteBuddyAgent;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.servlet.AsyncContext;
import javax.servlet.DispatcherType;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.EnumSet;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

public class AsyncServletTest extends AbstractServletTest {

    private static ElasticApmTracer tracer;

    @BeforeAll
    static void setUp() {
        tracer = new ElasticApmTracerBuilder()
            .configurationRegistry(SpyConfiguration.createSpyConfig())
            .reporter(reporter)
            .build();
        ElasticApmAgent.initInstrumentation(tracer, ByteBuddyAgent.install());
    }

    @AfterAll
    static void afterAll() {
        ElasticApmAgent.reset();
    }

    @Override
    protected void setUpHandler(ServletContextHandler handler) {
        handler.addServlet(PlainServlet.class, "/plain").setAsyncSupported(true);
        handler.addServlet(AsyncStartServlet.class, "/async").setAsyncSupported(true);
        handler.addServlet(DispatchServlet.class, "/dispatch").setAsyncSupported(true);
        handler.addServlet(DispatchTwiceServlet.class, "/async-dispatch-twice").setAsyncSupported(true);
        handler.addServlet(AsyncDispatchServlet.class, "/async-dispatch").setAsyncSupported(true);
        handler.addServlet(AsyncErrorServlet.class, "/async-error").setAsyncSupported(true);
        handler.addServlet(ErrorServlet.class, "/error").setAsyncSupported(true);
        handler.addServlet(AsyncTimeoutServlet.class, "/async-timeout").setAsyncSupported(true);
        handler.addFilter(CurrentTransactionTestFilter.class, "/*",
            EnumSet.of(DispatcherType.REQUEST, DispatcherType.ASYNC, DispatcherType.FORWARD, DispatcherType.INCLUDE, DispatcherType.ERROR))
            .setAsyncSupported(true);
    }

    @Test
    void testAsync() throws Exception {
        assertHasOneTransaction("/async", Predicate.isEqual("async response"), 200);
    }

    @Test
    void testAsyncDispatch() throws Exception {
        assertHasOneTransaction("/async-dispatch", Predicate.isEqual("plain response"), 200);
    }

    @Test
    void testDispatch() throws Exception {
        assertHasOneTransaction("/dispatch", Predicate.isEqual("plain response"), 200);
    }

    @Test
    void testAsyncDispatchTwice() throws Exception {
        assertHasOneTransaction("/async-dispatch-twice", Predicate.isEqual("plain response"), 200);
    }

    @Test
    void testAsyncTimeout() throws Exception {
        assertHasOneTransaction("/async-timeout", body -> true, 200);
    }

    @Test
    void testAsyncError() throws Exception {
        assertHasOneTransaction("/async-error", body -> true, 500);

        assertThat(reporter.getFirstError()).isNotNull();
        assertThat(reporter.getFirstError().getException().getMessage()).isEqualTo("Testing async servlet error handling");
    }

    private void assertHasOneTransaction(String path, Predicate<String> bodyPredicate, int status) throws IOException, InterruptedException {
        assertThat(get(path).body().string()).matches(bodyPredicate);
        assertThat(reporter.getFirstTransaction(500)).isNotNull();
        assertThat(reporter.getTransactions()).hasSize(1);
        final TransactionContext context = reporter.getFirstTransaction().getContext();
        assertThat(context.getRequest().getUrl().getPathname()).isEqualTo(path);
        assertThat(context.getResponse().getStatusCode()).isEqualTo(status);
    }

    @WebServlet(asyncSupported = true)
    public static class AsyncStartServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            final AsyncContext asyncContext = req.startAsync();
            asyncContext.start(() -> {
                try {
                    asyncContext.getResponse().getWriter().append("async response");
                    asyncContext.complete();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }

    public static class DispatchTwiceServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            req.startAsync().dispatch("/async-dispatch");
        }
    }

    public static class AsyncDispatchServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            final AsyncContext ctx = req.startAsync();
            ctx.start(() -> ctx.dispatch("/plain"));
        }
    }

    public static class DispatchServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            req.startAsync().dispatch("/plain");
        }
    }

    public static class AsyncErrorServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            req.startAsync().dispatch("/error");
        }
    }

    public static class ErrorServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            throw new ServletException("Testing async servlet error handling");
        }
    }

    public static class PlainServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            assertThat(tracer.currentTransaction()).isNotNull();
            resp.getWriter().append("plain response");
        }
    }

    public static class AsyncTimeoutServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            final AsyncContext asyncContext = req.startAsync();
            asyncContext.setTimeout(1);
            try {
                Thread.sleep(10L);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static class CurrentTransactionTestFilter implements Filter {

        @Override
        public void init(FilterConfig filterConfig) {
        }

        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
            assertThat(tracer.currentTransaction()).isNotNull();
            chain.doFilter(request, response);
            assertThat(tracer.currentTransaction()).isNotNull();
        }

        @Override
        public void destroy() {
        }
    }
}
