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
package co.elastic.apm.agent.report;

import co.elastic.apm.agent.configuration.CoreConfiguration;
import co.elastic.apm.agent.configuration.SpyConfiguration;
import io.undertow.Undertow;
import io.undertow.io.Sender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;
import org.stagemonitor.configuration.ConfigurationRegistry;
import org.testcontainers.Testcontainers;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;

import javax.annotation.Nullable;
import java.io.IOException;
import java.net.Authenticator;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.PasswordAuthentication;
import java.net.URL;
import java.net.URLConnection;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

// Using a separate test class for proxy support
public class ApmServerClientProxySupportIT {

    private static final Logger logger = LoggerFactory.getLogger(ApmServerClientProxySupportIT.class);

    private static final String PROXY_HEADER = "proxy-header";
    private static final String PROXY_HEADER_VALUE = "1234";

    private static final String DOCKER_IMAGE_NAME = "sameersbn/squid:3.5.27-2";
    private static final String PROXY_LOGIN = "elastic";
    private static final String PROXY_PASSWORD = "elasticpwd";

    private static URL directUrl;
    private static URL proxyUrl;

    @Nullable
    private GenericContainer<?> proxy = null;

    @BeforeAll
    static void initAll() {
        String host = "127.0.0.1";
        Undertow server = Undertow.builder()
            .addHttpListener(0, "0.0.0.0") // listen to all interfaces
            .setHandler(exchange -> {
                exchange.setStatusCode(200);
                String path = exchange.getRequestPath();
                Sender response = exchange.getResponseSender();
                if (path.equals("/")) {
                    response.send("{\"version\":\"7.9.0\"}");
                } else if (path.equals("/proxy")) {
                    String proxyHeader = exchange.getRequestHeaders().getFirst(PROXY_HEADER);
                    response.send(PROXY_HEADER_VALUE.equals(proxyHeader) ? "proxy" : "no-proxy");
                }
                exchange.endExchange();
            }).build();
        server.start();
        int port = ((InetSocketAddress) server.getListenerInfo().get(0).getAddress()).getPort();

        directUrl = baseUrl(host, port);
        // we have to use another host name so the proxy host can call the docker host where
        // the http server is running
        proxyUrl = baseUrl("host.testcontainers.internal", port);

        logger.info("server direct url = {}", directUrl);
        logger.info("server proxy url = {}", proxyUrl);

        // allow proxy to connect to host port
        Testcontainers.exposeHostPorts(port);
    }

    @BeforeEach
    void beforeEach() {
        // ensure that there is no global authenticator set
        Authenticator.setDefault(null);
    }

    @AfterEach
    void afterEach() {
        if (proxy != null) {
            proxy.stop();
        }

        System.setProperty("http.proxyHost", "");
        System.setProperty("http.proxyPort", "");
        System.setProperty("http.proxyUser", "");
        System.setProperty("http.proxyPassword", "");

        assertThat(Authenticator.getDefault())
            .describedAs("test should not leave a global authenticator instance set")
            .isNull();
    }

    private void startProxy(boolean useAuth) {
        String squidConfig = String.format("squid/squid_%s.conf", useAuth ? "basic-auth" : "no-auth");

        proxy = new GenericContainer<>(DOCKER_IMAGE_NAME)
            .withClasspathResourceMapping(squidConfig, "/etc/squid/squid.conf", BindMode.READ_ONLY)
            .withClasspathResourceMapping("squid/squid_passwd", "/etc/squid/passwd", BindMode.READ_ONLY);

        proxy.addExposedPorts(3128);
        proxy.start();
    }


    @Test
    void noProxy() throws IOException {
        simpleTestScenario(false);
    }

    @Test
    void noAuthProxy() throws IOException {
        startProxy(false);

        setSystemProxyProperties();

        expectProxySuccess();
    }

    @Test
    void basicAuthProxy_noAuthenticator() throws IOException {
        startProxy(true);

        setSystemProxyProperties();
        setSystemProxyCredentialProperties();

        // in this case, no global authenticator is set
        // thus we should get a 407 error from the proxy because credentials aren't taken in account by default
        expectProxyError();
    }

    @Test
    void basicAuthProxy_globalAuthenticator() throws IOException {
        startProxy(true);

        setSystemProxyProperties();
        setSystemProxyCredentialProperties();

        // we should not be able to use proxy without authenticator
        expectProxyError();

        Authenticator.setDefault(new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(PROXY_LOGIN, PROXY_PASSWORD.toCharArray());
            }
        });

        expectProxySuccess();

        Authenticator.setDefault(null);

    }

    private void expectProxyError() throws IOException {
        ApmServerClient client = createAndStartClient(true);
        URL requestUrl = requestUrl(true);

        checkProxyAuthenticationError(client.startRequest(requestUrl.getPath()));
        checkProxyAuthenticationError(requestUrl.openConnection());
    }

    private void expectProxySuccess() throws IOException {
        ApmServerClient client = createAndStartClient(true);
        URL requestUrl = requestUrl(true);

        checkUsingProxy(client.startRequest(requestUrl.getPath()), true);
        checkUsingProxy(requestUrl.openConnection(), true);
    }

    private void simpleTestScenario(boolean useProxy) throws IOException {
        ApmServerClient client = createAndStartClient(useProxy);
        URL requestUrl = requestUrl(useProxy);
        checkUsingProxy(client.startRequest(requestUrl.getPath()), useProxy);
        checkUsingProxy(requestUrl.openConnection(), useProxy);
    }

    private void setSystemProxyProperties() {
        System.setProperty("http.proxyHost", proxy.getContainerIpAddress());
        System.setProperty("http.proxyPort", Integer.toString(proxy.getMappedPort(3128)));
    }

    private void setSystemProxyCredentialProperties() {
        System.setProperty("http.proxyUser", PROXY_LOGIN);
        System.setProperty("http.proxyPassword", PROXY_PASSWORD);
    }

    private static ApmServerClient createAndStartClient(boolean useProxy) {
        ConfigurationRegistry spyConfig = SpyConfiguration.createSpyConfig();
        ReporterConfiguration config = spyConfig.getConfig(ReporterConfiguration.class);

        doReturn(Collections.singletonList(useProxy ? proxyUrl : directUrl)).when(config).getServerUrls();
        ApmServerClient client = new ApmServerClient(config, spyConfig.getConfig(CoreConfiguration.class));
        client.start();
        return client;
    }

    private static URL requestUrl(boolean useProxy) {
        try {
            return new URL((useProxy ? proxyUrl : directUrl).toString() + "/proxy");
        } catch (MalformedURLException e) {
            throw new IllegalStateException(e);
        }
    }

    private void checkUsingProxy(@Nullable URLConnection connection, boolean expectProxy) throws IOException {
        assertThat(connection)
            .isNotNull()
            .isInstanceOf(HttpURLConnection.class);

        assertThat(HttpUtils.readToString(connection.getInputStream()))
            .isEqualTo(expectProxy ? "proxy" : "no-proxy");

        assertThat(((HttpURLConnection) connection).getResponseCode())
            .isEqualTo(200);
    }

    private void checkProxyAuthenticationError(@Nullable URLConnection connection) throws IOException {
        assertThat(connection)
            .isNotNull()
            .isInstanceOf(HttpURLConnection.class);

        assertThat(((HttpURLConnection) connection).getResponseCode())
            .isEqualTo(407);
    }

    private static URL baseUrl(String host, int port) {
        try {
            return new URL(String.format("http://%s:%d", host, port));
        } catch (MalformedURLException e) {
            throw new IllegalStateException(e);
        }
    }

}
