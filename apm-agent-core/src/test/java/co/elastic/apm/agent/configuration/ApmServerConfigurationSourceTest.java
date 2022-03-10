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
package co.elastic.apm.agent.configuration;

import co.elastic.apm.agent.impl.metadata.MetaDataMock;
import co.elastic.apm.agent.impl.stacktrace.StacktraceConfiguration;
import co.elastic.apm.agent.report.ApmServerClient;
import co.elastic.apm.agent.report.ReporterConfiguration;
import co.elastic.apm.agent.report.serialize.DslJsonSerializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.http.RequestMethod;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import co.elastic.apm.agent.sdk.logging.Logger;
import org.stagemonitor.configuration.ConfigurationRegistry;

import java.net.URL;
import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder.okForJson;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.notFound;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.serviceUnavailable;
import static com.github.tomakehurst.wiremock.client.WireMock.status;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.matching.RequestPatternBuilder.newRequestPattern;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class ApmServerConfigurationSourceTest {

    @Rule
    public WireMockRule mockApmServer = new WireMockRule(WireMockConfiguration.wireMockConfig().dynamicPort());
    private ConfigurationRegistry config;
    private ApmServerClient apmServerClient;
    private ApmServerConfigurationSource configurationSource;
    private Logger mockLogger;

    @Before
    public void setUp() throws Exception {
        config = SpyConfiguration.createSpyConfig();
        mockApmServer.stubFor(get(urlEqualTo("/")).willReturn(okForJson(Map.of("version", "7.0.0"))));
        mockApmServer.stubFor(post(urlEqualTo("/config/v1/agents")).willReturn(okForJson(Map.of("foo", "bar")).withHeader("ETag", "foo")));
        mockApmServer.stubFor(post(urlEqualTo("/config/v1/agents")).withHeader("If-None-Match", equalTo("foo")).willReturn(status(304)));
        apmServerClient = new ApmServerClient(config.getConfig(ReporterConfiguration.class), config.getConfig(CoreConfiguration.class));
        apmServerClient.start(List.of(new URL("http", "localhost", mockApmServer.port(), "/")));
        mockLogger = mock(Logger.class);
        configurationSource = new ApmServerConfigurationSource(
            new DslJsonSerializer(mock(StacktraceConfiguration.class), apmServerClient, MetaDataMock.create()),
            apmServerClient,
            mockLogger
        );
    }

    @Test
    public void testLoadRemoteConfig() throws Exception {
        configurationSource.fetchConfig(config);
        assertThat(configurationSource.getValue("foo")).isEqualTo("bar");
        mockApmServer.verify(postRequestedFor(urlEqualTo("/config/v1/agents")));
        configurationSource.fetchConfig(config);
        mockApmServer.verify(postRequestedFor(urlEqualTo("/config/v1/agents")).withHeader("If-None-Match", equalTo("foo")));
        for (LoggedRequest request : WireMock.findAll(newRequestPattern(RequestMethod.POST, urlEqualTo("/config/v1/agents")))) {
            final JsonNode jsonNode = new ObjectMapper().readTree(request.getBodyAsString());
            assertThat(jsonNode.get("service")).isNotNull();
            assertThat(jsonNode.get("system")).isNotNull();
            assertThat(jsonNode.get("process")).isNotNull();
        }
    }

    @Test
    public void testRemoteConfigDisabled() {
        doReturn(false).when(config.getConfig(CoreConfiguration.class)).isCentralConfigEnabled();
        configurationSource.fetchConfig(config);
        assertThat(configurationSource.getValue("foo")).isNull();
        mockApmServer.verify(0, postRequestedFor(urlEqualTo("/config/v1/agents")));
    }

    @Test
    public void testNotFound() {
        mockApmServer.stubFor(post(urlEqualTo("/config/v1/agents")).willReturn(notFound()));
        assertThat(configurationSource.fetchConfig(config)).isNull();
        verify(mockLogger, times(1)).debug(contains("This APM Server does not support central configuration"));
    }

    @Test
    public void configDeleted() {
        configurationSource.fetchConfig(config);
        assertThat(configurationSource.getValue("foo")).isEqualTo("bar");
        mockApmServer.stubFor(post(urlEqualTo("/config/v1/agents")).willReturn(okJson("{}")));
        configurationSource.fetchConfig(config);
        assertThat(configurationSource.getValue("foo")).isNull();
    }

    @Test
    public void testApmServerCantReachKibana() {
        mockApmServer.stubFor(post(urlEqualTo("/config/v1/agents")).willReturn(serviceUnavailable()));
        assertThat(configurationSource.fetchConfig(config)).isNull();
        verify(mockLogger, times(1)).error(contains("Remote configuration is not available"));
    }

    @Test
    public void testApmServerError() {
        mockApmServer.stubFor(post(urlEqualTo("/config/v1/agents")).willReturn(serverError()));
        assertThat(configurationSource.fetchConfig(config)).isNull();
        verify(mockLogger, times(1)).error(contains("Unexpected status 500 while fetching configuration"));
    }

    @Test
    public void testApmServerCentralConfigDisabled() {
        mockApmServer.stubFor(post(urlEqualTo("/config/v1/agents")).willReturn(WireMock.forbidden()));
        assertThat(configurationSource.fetchConfig(config)).isNull();
        verify(mockLogger).debug(contains("Central configuration is disabled"));
    }

    @Test
    public void parseMaxAgeFromCacheControlHeader() {
        assertThat(ApmServerConfigurationSource.parseMaxAge("max-age=1")).isEqualTo(1);
        assertThat(ApmServerConfigurationSource.parseMaxAge("max-age= 1")).isEqualTo(1);
        assertThat(ApmServerConfigurationSource.parseMaxAge("max-age =1")).isEqualTo(1);
        assertThat(ApmServerConfigurationSource.parseMaxAge("max-age = 1")).isEqualTo(1);
        assertThat(ApmServerConfigurationSource.parseMaxAge("public, max-age = 42")).isEqualTo(42);
        assertThat(ApmServerConfigurationSource.parseMaxAge("max-age= 42 , public")).isEqualTo(42);
        assertThat(ApmServerConfigurationSource.parseMaxAge("public")).isNull();
        assertThat(ApmServerConfigurationSource.parseMaxAge(null)).isNull();
    }

}
