/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 the original author or authors
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
import co.elastic.apm.configuration.SpyConfiguration;
import co.elastic.apm.impl.ElasticApmTracer;
import net.bytebuddy.agent.ByteBuddyAgent;
import okhttp3.OkHttpClient;
import okhttp3.Response;
import org.eclipse.jetty.server.NetworkConnector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletHandler;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class ServletAdviceTest {

    private static MockReporter reporter;
    private Server server;
    private OkHttpClient httpClient;

    @BeforeAll
    static void beforeAll() {
        reporter = new MockReporter();
        ElasticApmAgent.initInstrumentation(ElasticApmTracer.builder()
            .configurationRegistry(SpyConfiguration.createSpyConfig())
            .reporter(reporter)
            .build(), ByteBuddyAgent.install());
    }

    @AfterAll
    static void afterAll() {
        ElasticApmAgent.reset();
    }

    @BeforeEach
    void setUp() throws Exception {
        server = new Server();
        server.addConnector(new ServerConnector(server));
        final ServletHandler handler = new ServletHandler();
        handler.addServletWithMapping(TestServlet.class, "/");
        server.setHandler(handler);
        server.start();

        httpClient = new OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.SECONDS)
            .connectTimeout(0, TimeUnit.SECONDS)
            .build();
    }


    @AfterEach
    void tearDown() throws Exception {
        server.stop();
    }

    @Test
    void name() throws IOException {
        final Response response = httpClient.newCall(new okhttp3.Request.Builder().url("http://localhost:" + getPort()).build()).execute();
        assertThat(response.code()).isEqualTo(200);
        assertThat(reporter.getTransactions()).hasSize(1);
    }

    private int getPort() {
        return ((NetworkConnector) server.getConnectors()[0]).getLocalPort();
    }

    public static class TestServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
        }
    }
}
