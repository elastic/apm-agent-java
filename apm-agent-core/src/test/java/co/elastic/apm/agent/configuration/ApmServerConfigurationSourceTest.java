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
package co.elastic.apm.agent.configuration;

import co.elastic.apm.agent.impl.MetaData;
import co.elastic.apm.agent.impl.stacktrace.StacktraceConfiguration;
import co.elastic.apm.agent.report.ApmServerClient;
import co.elastic.apm.agent.report.ReporterConfiguration;
import co.elastic.apm.agent.report.serialize.DslJsonSerializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.stagemonitor.configuration.ConfigurationRegistry;

import java.net.URL;
import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.status;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.mock;

public class ApmServerConfigurationSourceTest {

    @Rule
    public WireMockRule mockApmServer = new WireMockRule(WireMockConfiguration.wireMockConfig().dynamicPort());
    private ConfigurationRegistry config;
    private ApmServerClient apmServerClient;
    private ApmServerConfigurationSource configurationSource;

    @Before
    public void setUp() throws Exception {
        config = SpyConfiguration.createSpyConfig();
        apmServerClient = new ApmServerClient(config.getConfig(ReporterConfiguration.class), List.of(new URL("http", "localhost", mockApmServer.port(), "/")));
        mockApmServer.stubFor(post(urlEqualTo("/config")).willReturn(ResponseDefinitionBuilder.okForJson(Map.of("foo", "bar")).withHeader("ETag", "foo")));
        mockApmServer.stubFor(post(urlEqualTo("/config")).withHeader("If-None-Match", equalTo("foo")).willReturn(status(304)));
        configurationSource = new ApmServerConfigurationSource(new DslJsonSerializer(mock(StacktraceConfiguration.class)), MetaData.create(config, null, null), apmServerClient);
    }

    @Test
    public void testLoadRemoteConfig() throws Exception {
        configurationSource.reload();
        assertThat(configurationSource.getValue("foo")).isEqualTo("bar");
        mockApmServer.verify(postRequestedFor(urlEqualTo("/config")));
        configurationSource.reload();
        mockApmServer.verify(postRequestedFor(urlEqualTo("/config")).withHeader("If-None-Match", equalTo("foo")));
        for (LoggedRequest request : WireMock.findAll(RequestPatternBuilder.allRequests())) {
            final JsonNode jsonNode = new ObjectMapper().readTree(request.getBodyAsString());
            assertThat(jsonNode.get("service")).isNotNull();
            assertThat(jsonNode.get("system")).isNotNull();
            assertThat(jsonNode.get("process")).isNotNull();
        }
    }
}
