/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2019 Elastic and contributors
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

import co.elastic.apm.agent.configuration.SpyConfiguration;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.ElasticApmTracerBuilder;
import co.elastic.apm.agent.util.Version;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.stagemonitor.configuration.ConfigurationRegistry;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder.okForJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.notFound;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;

public class ApmServerClientTest {

    @Rule
    public WireMockRule apmServer1 = new WireMockRule(WireMockConfiguration.wireMockConfig().dynamicPort());
    @Rule
    public WireMockRule apmServer2 = new WireMockRule(WireMockConfiguration.wireMockConfig().dynamicPort());
    private ApmServerClient apmServerClient;
    private ConfigurationRegistry config;
    private ElasticApmTracer tracer;
    private ReporterConfiguration reporterConfiguration;

    @Before
    public void setUp() throws MalformedURLException {
        URL url1 = new URL("http", "localhost", apmServer1.port(), "/");
        URL url2 = new URL("http", "localhost", apmServer2.port(), "/");
        // APM server 6.x style
        apmServer1.stubFor(get(urlEqualTo("/")).willReturn(okForJson(Map.of("ok", Map.of("version", "6.7.0-SNAPSHOT")))));
        apmServer1.stubFor(get(urlEqualTo("/test")).willReturn(notFound()));
        apmServer1.stubFor(get(urlEqualTo("/not-found")).willReturn(notFound()));
        // APM server 7+ style
        apmServer2.stubFor(get(urlEqualTo("/")).willReturn(okForJson(Map.of("version", "7.3.0-RC1"))));
        apmServer2.stubFor(get(urlEqualTo("/test")).willReturn(ok("hello from server 2")));
        apmServer2.stubFor(get(urlEqualTo("/not-found")).willReturn(notFound()));

        config = SpyConfiguration.createSpyConfig();
        reporterConfiguration = config.getConfig(ReporterConfiguration.class);
        doReturn(List.of(url1, url2)).when(reporterConfiguration).getServerUrls();
        tracer = new ElasticApmTracerBuilder()
            .configurationRegistry(config)
            .build();

        apmServerClient = new ApmServerClient(reporterConfiguration, tracer.getConfig(ReporterConfiguration.class).getServerUrls());
    }

    @Test
    public void testInitialCurrentUrlIsFirstUrl() throws Exception {
        assertThat(apmServerClient.getCurrentUrl().getPort()).isEqualTo(apmServer1.port());

        apmServerClient.execute("/test", HttpURLConnection::getResponseCode);

        apmServer1.verify(1, getRequestedFor(urlEqualTo("/test")));
        apmServer2.verify(0, getRequestedFor(urlEqualTo("/test")));
    }

    @Test
    public void testUseNextUrlOnError() throws Exception {
        apmServerClient.incrementAndGetErrorCount(0);
        assertThat(apmServerClient.getCurrentUrl().getPort()).isEqualTo(apmServer2.port());

        apmServerClient.execute("/test", HttpURLConnection::getResponseCode);

        apmServer1.verify(0, getRequestedFor(urlEqualTo("/test")));
        apmServer2.verify(1, getRequestedFor(urlEqualTo("/test")));
        assertThat(apmServerClient.getErrorCount()).isEqualTo(1);
    }

    @Test
    public void testWrapUrlsOnConsecutiveError() throws Exception {
        int expectedErrorCount = apmServerClient.incrementAndGetErrorCount(0);
        apmServerClient.incrementAndGetErrorCount(expectedErrorCount);

        testInitialCurrentUrlIsFirstUrl();
        assertThat(apmServerClient.getErrorCount()).isEqualTo(2);
    }

    @Test
    public void testRetry() throws Exception {
        assertThat(apmServerClient.<String>execute("/test", conn -> new String(conn.getInputStream().readAllBytes()))).isEqualTo("hello from server 2");
        assertThat(apmServerClient.getCurrentUrl().getPort()).isEqualTo(apmServer2.port());
        apmServer1.verify(1, getRequestedFor(urlEqualTo("/test")));
        apmServer2.verify(1, getRequestedFor(urlEqualTo("/test")));
        assertThat(apmServerClient.getErrorCount()).isEqualTo(1);
    }

    @Test
    public void testRetryFailure() {
        assertThatThrownBy(() -> apmServerClient.execute("/not-found", URLConnection::getInputStream))
            .isInstanceOf(FileNotFoundException.class)
            .matches(t -> t.getSuppressed().length == 1, "should have a suppressed exception");
        apmServer1.verify(1, getRequestedFor(urlEqualTo("/not-found")));
        apmServer2.verify(1, getRequestedFor(urlEqualTo("/not-found")));
        // two failures -> urls wrap
        assertThat(apmServerClient.getCurrentUrl().getPort()).isEqualTo(apmServer1.port());
        assertThat(apmServerClient.getErrorCount()).isEqualTo(2);
    }

    @Test
    public void testExecuteSuccessfullyForAllUrls() {
        apmServerClient.executeForAllUrls("/not-found", connection -> {
            connection.getResponseCode();
            return null;
        });
        apmServer1.verify(1, getRequestedFor(urlEqualTo("/not-found")));
        apmServer2.verify(1, getRequestedFor(urlEqualTo("/not-found")));
        // no failures -> urls in initial state
        assertThat(apmServerClient.getCurrentUrl().getPort()).isEqualTo(apmServer1.port());
        assertThat(apmServerClient.getErrorCount()).isZero();
    }

    @Test
    public void testExecuteFailureForAllUrls() {
        // exception will only be logged, not thrown
        apmServerClient.executeForAllUrls("/not-found", connection -> {
            connection.getInputStream();
            return null;
        });
        apmServer1.verify(1, getRequestedFor(urlEqualTo("/not-found")));
        apmServer2.verify(1, getRequestedFor(urlEqualTo("/not-found")));
        assertThat(apmServerClient.getErrorCount()).isEqualTo(0);
    }

    @Test
    public void testSimulateConcurrentConnectionError() {
        apmServerClient.incrementAndGetErrorCount(0);
        apmServerClient.incrementAndGetErrorCount(0);
        assertThat(apmServerClient.getErrorCount()).isOne();
    }

    @Test
    public void testGetServerUrlsVerifyThatServerUrlsWillBeReloaded() throws IOException {
        URL tempUrl = new URL("http", "localhost", 9999, "");
        config.save("server_urls", tempUrl.toString(), SpyConfiguration.CONFIG_SOURCE_NAME);

        List<URL> updatedServerUrls = apmServerClient.getServerUrls();

        assertThat(updatedServerUrls).isEqualTo(List.of(tempUrl));
    }

    @Test
    public void testApmServerVersion() throws IOException {
        assertThat(apmServerClient.isAtLeast(new Version("6.7.0"))).isTrue();
        assertThat(apmServerClient.isAtLeast(new Version("6.7.1"))).isFalse();
        assertThat(apmServerClient.supportsNonStringLabels()).isTrue();
        apmServer1.stubFor(get(urlEqualTo("/")).willReturn(okForJson(Map.of("version", "6.6.1"))));
        config.save("server_urls", new URL("http", "localhost", apmServer1.port(), "/").toString(), SpyConfiguration.CONFIG_SOURCE_NAME);
        assertThat(apmServerClient.supportsNonStringLabels()).isFalse();

    }

    @Test
    public void testWithEmptyServerUrlList() {
        ApmServerClient client = new ApmServerClient(reporterConfiguration, Collections.emptyList());
        Exception exception = null;
        try {
            client.execute("/irrelevant", connection -> null);
        } catch (Exception e) {
            exception = e;
        }
        assertThat(exception).isNull();
    }
}
