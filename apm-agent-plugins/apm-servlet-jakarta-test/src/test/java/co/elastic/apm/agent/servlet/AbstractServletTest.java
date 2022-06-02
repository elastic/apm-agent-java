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

import co.elastic.apm.agent.AbstractInstrumentationTest;
import okhttp3.OkHttpClient;
import okhttp3.Response;
import org.eclipse.jetty.server.NetworkConnector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

abstract class AbstractServletTest extends AbstractInstrumentationTest {

    @Nullable
    private static Server server;

    @Nullable
    protected OkHttpClient httpClient;

    @BeforeEach
    void initServerAndClient() throws Exception {
        // because we reuse the same classloader with different servlet context names
        // we need to explicitly reset the name cache to make service name detection work as expected
        ServletServiceNameHelper.clearServiceNameCache();

        // server is not reused between tests as handler is provided from subclass
        // another alternative
        server = new Server();
        server.addConnector(new ServerConnector(server));
        ServletContextHandler handler = new ServletContextHandler();
        setUpHandler(handler);
        server.setHandler(handler);
        server.start();

        assertThat(getPort()).isPositive();

        httpClient = new OkHttpClient.Builder()
            // set to 0 for debugging
            .readTimeout(0, TimeUnit.SECONDS)
            .connectTimeout(0, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build();
    }

    @AfterEach
    void stopServer() throws Exception {
        server.stop();
        server = null;
    }

    protected Response get(String path) throws IOException {
        return httpClient.newCall(new okhttp3.Request.Builder().url("http://localhost:" + getPort() + path).build()).execute();
    }

    protected abstract void setUpHandler(ServletContextHandler handler);

    protected int getPort() {
        return ((NetworkConnector) server.getConnectors()[0]).getLocalPort();
    }
}
