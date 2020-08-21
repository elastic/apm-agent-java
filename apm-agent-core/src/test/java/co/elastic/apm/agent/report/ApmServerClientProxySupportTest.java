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
package co.elastic.apm.agent.report;

import co.elastic.apm.agent.AbstractInstrumentationTest;
import co.elastic.apm.agent.configuration.SpyConfiguration;
import co.elastic.apm.agent.proxysupport.AuthenticatorInstrumentation;
import io.undertow.Undertow;
import io.undertow.io.Sender;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

// Using a separate test class for proxy support
//
// We have to inherit from instrumentation tests because proxy support relies on instrumentation thanks to Authenticator
public class ApmServerClientProxySupportTest extends AbstractInstrumentationTest {

    private static final Logger logger = LoggerFactory.getLogger(ApmServerClientProxySupportTest.class);

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

        simpleTestScenario(true);
    }

    @Test
    void basicAuthProxy_noAuthenticator() throws IOException {
        startProxy(true);

        setSystemProxyProperties();
        setSystemProxyCredentialProperties();

        ApmServerClient client = createAndStartClient(true);
        URL requestUrl = requestUrl(true);

        AuthenticatorInstrumentation.toggleFallback(true);
        checkUsingProxy(client.startRequest(requestUrl.getPath()), true); // TODO : use client.execute instead to enable proper wrapping
        AuthenticatorInstrumentation.toggleFallback(false);

        // in this case, no global authenticator is set
        // thus we should get a 407 error from the proxy because credentials aren't taken in account by default
        checkProxyAuthenticationError(requestUrl.openConnection());

        assertThat(Authenticator.getDefault())
            .describedAs("global authenticator should remain unset")
            .isNull();
    }

    @Test
    void basicAuthProxy_globalAuthenticator() throws IOException {
        startProxy(true);

        setSystemProxyProperties();
        setSystemProxyCredentialProperties();

        final AtomicBoolean allow = new AtomicBoolean(false);

        // hostile authenticator that does not allow agent to connect
        Authenticator authenticator = new Authenticator() {
            @Nullable
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return !allow.get() ? null : new PasswordAuthentication(PROXY_LOGIN, PROXY_PASSWORD.toCharArray());
            }
        };
        Authenticator.setDefault(authenticator);

        ApmServerClient client = createAndStartClient(true);
        URL requestUrl = requestUrl(true);
        checkUsingProxy(client.startRequest(requestUrl.getPath()), true);

        allow.set(true);
        checkUsingProxy(requestUrl.openConnection(), true);

        assertThat(Authenticator.getDefault())
            .describedAs("authenticator instance should remain unchanged")
            .isSameAs(authenticator);
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
        ReporterConfiguration config = SpyConfiguration.createSpyConfig().getConfig(ReporterConfiguration.class);

        doReturn(Collections.singletonList(useProxy ? proxyUrl : directUrl)).when(config).getServerUrls();
        ApmServerClient client = new ApmServerClient(config);
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

    @NotNull
    private static URL baseUrl(String host, int port) {
        try {
            return new URL(String.format("http://%s:%d", host, port));
        } catch (MalformedURLException e) {
            throw new IllegalStateException(e);
        }
    }

    // test cases to cover
    //
    // no proxy
    // proxy without authentication
    // proxy with authentication (basic)
    //
    // authenticator
    // - no global authenticator set
    // - one global authenticator is set, we have to override it's returned value

    // how to test
    // using a squid proxy docker image
    //
    //
    // -> how to make squid provide basic/digest authentication ?
    //   - might be doable, but that does not really help here
    //
    // -> how to make sure that requests go through the proxy ?
    //   - one more line in the proxy logs
    //   - adding an extra header in proxy
}
