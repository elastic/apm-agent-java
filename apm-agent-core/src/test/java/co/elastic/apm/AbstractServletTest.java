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
package co.elastic.apm;

import okhttp3.OkHttpClient;
import okhttp3.Response;
import org.eclipse.jetty.server.NetworkConnector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class AbstractServletTest {
    protected static final MockReporter reporter = new MockReporter();
    @Nullable
    private static Server server;
    protected OkHttpClient httpClient;

    @AfterAll
    static void stopServer() throws Exception {
        server.stop();
        server = null;
    }

    @BeforeEach
    final void initServer() throws Exception {
        if (server == null) {
            server = new Server();
            server.addConnector(new ServerConnector(server));
            ServletContextHandler handler = new ServletContextHandler();
            setUpHandler(handler);
            server.setHandler(handler);
            server.start();
        }
        assertThat(getPort()).isPositive();

        httpClient = new OkHttpClient.Builder()
            // set to 0 for debugging
            .readTimeout(10, TimeUnit.SECONDS)
            .connectTimeout(10, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build();
    }


    protected Response get(String path) throws IOException {
        return httpClient.newCall(new okhttp3.Request.Builder().url("http://localhost:" + getPort() + path).build()).execute();
    }

    protected abstract void setUpHandler(ServletContextHandler handler);

    @AfterEach
    final void tearDown() {
        reporter.reset();
    }

    protected int getPort() {
        return ((NetworkConnector) server.getConnectors()[0]).getLocalPort();
    }
}
