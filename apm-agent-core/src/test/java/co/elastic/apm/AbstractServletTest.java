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

import org.eclipse.jetty.server.NetworkConnector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import javax.annotation.Nullable;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class AbstractServletTest {
    protected static final MockReporter reporter = new MockReporter();
    @Nullable
    private static Server server;
    protected HttpURLConnection connection;

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
    }

    protected HttpURLConnection createRequest(String path) {
        try {
            URL url = new URL("http://localhost:" + getPort() + path);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setDoOutput(true);
            // set to 0 for debugging
            connection.setConnectTimeout(10 * 1000);
            connection.setReadTimeout(10 * 1000);
            connection.connect();
            return connection;
        } catch (IOException e) {
            System.out.println("IOException:"+e.getLocalizedMessage());
        }
        return null;
    }

    protected abstract void setUpHandler(ServletContextHandler handler);

    @AfterEach
    final void tearDown() {
        reporter.reset();

        if (connection != null) {
            connection.disconnect();
            connection = null;
        }
    }

    protected int getPort() {
        return ((NetworkConnector) server.getConnectors()[0]).getLocalPort();
    }
}
