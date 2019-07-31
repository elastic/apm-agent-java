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

import co.elastic.apm.agent.MockTracer;
import co.elastic.apm.agent.configuration.SpyConfiguration;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.MetaData;
import co.elastic.apm.agent.impl.error.ErrorCapture;
import co.elastic.apm.agent.impl.payload.ProcessInfo;
import co.elastic.apm.agent.impl.payload.Service;
import co.elastic.apm.agent.impl.payload.SystemInfo;
import co.elastic.apm.agent.impl.stacktrace.StacktraceConfiguration;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.report.processor.ProcessorEventHandler;
import co.elastic.apm.agent.report.serialize.DslJsonSerializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import org.junit.Rule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.stagemonitor.configuration.ConfigurationRegistry;

import javax.annotation.Nonnull;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.InflaterInputStream;

import static co.elastic.apm.agent.report.IntakeV2ReportingEventHandler.INTAKE_V2_URL;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.serviceUnavailable;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class IntakeV2ReportingEventHandlerTest {

    private static final String HTTP_LOCALHOST = "http://localhost:";
    private static final String APM_SERVER_PATH = "/apm-server";
    private static final ObjectMapper objectMapper = new ObjectMapper();
    @Rule
    public WireMockRule mockApmServer1 = new WireMockRule(WireMockConfiguration.wireMockConfig().dynamicPort());
    @Rule
    public WireMockRule mockApmServer2 = new WireMockRule(WireMockConfiguration.wireMockConfig().dynamicPort());
    private IntakeV2ReportingEventHandler reportingEventHandler;
    private IntakeV2ReportingEventHandler nonConnectedReportingEventHandler;
    private ApmServerClient apmServerClient;

    @Nonnull
    private static JsonNode getReadTree(String s) {
        try {
            return objectMapper.readTree(s);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        List.of(mockApmServer1, mockApmServer2).forEach(WireMockServer::start);
        mockApmServer1.stubFor(post(INTAKE_V2_URL).willReturn(ok()));
        mockApmServer2.stubFor(post(APM_SERVER_PATH + INTAKE_V2_URL).willReturn(ok()));
        final ConfigurationRegistry configurationRegistry = SpyConfiguration.createSpyConfig();
        final ReporterConfiguration reporterConfiguration = configurationRegistry.getConfig(ReporterConfiguration.class);
        SystemInfo system = new SystemInfo("x64", "localhost", "platform");
        final ProcessInfo title = new ProcessInfo("title");
        final Service service = new Service();
        apmServerClient = new ApmServerClient(reporterConfiguration, List.of(
            new URL(HTTP_LOCALHOST + mockApmServer1.port()),
            // testing ability to configure a server url with additional path (ending with "/" in this case)
            new URL(HTTP_LOCALHOST + mockApmServer2.port() + APM_SERVER_PATH + "/")
        ));
        reportingEventHandler = new IntakeV2ReportingEventHandler(
            reporterConfiguration,
            mock(ProcessorEventHandler.class),
            new DslJsonSerializer(mock(StacktraceConfiguration.class), apmServerClient),
            new MetaData(title, service, system, Collections.emptyMap()), apmServerClient);
        final ProcessInfo title1 = new ProcessInfo("title");
        final Service service1 = new Service();
        nonConnectedReportingEventHandler = new IntakeV2ReportingEventHandler(
            reporterConfiguration,
            mock(ProcessorEventHandler.class),
            new DslJsonSerializer(mock(StacktraceConfiguration.class), apmServerClient),
            new MetaData(title1, service1, system, Collections.emptyMap()),
            new ApmServerClient(reporterConfiguration, List.of(new URL("http://non.existing:8080"))));
    }

    @AfterEach
    void tearDown() {
        List.of(mockApmServer1, mockApmServer2).forEach(WireMockRule::stop);
    }

    @Test
    void testUrls() throws MalformedURLException {
        URL server1url = apmServerClient.appendPathToCurrentUrl(INTAKE_V2_URL);
        assertThat(server1url.toString()).isEqualTo(HTTP_LOCALHOST + mockApmServer1.port() + INTAKE_V2_URL);
        apmServerClient.onConnectionError();
        URL server2url = apmServerClient.appendPathToCurrentUrl(INTAKE_V2_URL);
        assertThat(server2url.toString()).isEqualTo(HTTP_LOCALHOST + mockApmServer2.port() + APM_SERVER_PATH + INTAKE_V2_URL);
        // just to restore
        apmServerClient.onConnectionError();
    }

    @Test
    void testReport() {
        reportTransaction(reportingEventHandler);
        reportSpan();
        reportError();
        assertThat(reportingEventHandler.getBufferSize()).isGreaterThan(0);
        reportingEventHandler.flush();
        assertThat(reportingEventHandler.getBufferSize()).isEqualTo(0);

        final List<JsonNode> ndJsonNodes = getNdJsonNodes();
        assertThat(ndJsonNodes).hasSize(4);
        assertThat(ndJsonNodes.get(0).get("metadata")).isNotNull();
        assertThat(ndJsonNodes.get(1).get("transaction")).isNotNull();
        assertThat(ndJsonNodes.get(2).get("span")).isNotNull();
        assertThat(ndJsonNodes.get(3).get("error")).isNotNull();
    }

    @Test
    void testNoopWhenNotConnected() {
        reportTransaction(nonConnectedReportingEventHandler);
        assertThat(nonConnectedReportingEventHandler.getBufferSize()).isEqualTo(0);
    }

    @Test
    void testShutDown() {
        reportTransaction(reportingEventHandler);
        sendShutdownEvent();
        reportSpan();
        reportingEventHandler.flush();

        final List<JsonNode> ndJsonNodes = getNdJsonNodes();
        assertThat(ndJsonNodes).hasSize(2);
        assertThat(ndJsonNodes.get(0).get("metadata")).isNotNull();
        assertThat(ndJsonNodes.get(1).get("transaction")).isNotNull();
    }

    @Test
    void testReportRoundRobinOnServerError() {
        mockApmServer1.stubFor(post(INTAKE_V2_URL).willReturn(serviceUnavailable()));

        reportTransaction(reportingEventHandler);
        reportingEventHandler.flush();
        mockApmServer1.verify(postRequestedFor(urlEqualTo(INTAKE_V2_URL)));
        mockApmServer2.verify(0, postRequestedFor(urlEqualTo(INTAKE_V2_URL)));

        mockApmServer1.resetRequests();
        mockApmServer2.resetRequests();

        reportTransaction(reportingEventHandler);
        reportingEventHandler.flush();
        mockApmServer1.verify(0, postRequestedFor(urlEqualTo(INTAKE_V2_URL)));
        mockApmServer2.verify(postRequestedFor(urlEqualTo(APM_SERVER_PATH + INTAKE_V2_URL)));
    }

    @Test
    void testExponentialBackoff() {
        assertThat(IntakeV2ReportingEventHandler.getBackoffTimeSeconds(0)).isEqualTo(0);
        assertThat(IntakeV2ReportingEventHandler.getBackoffTimeSeconds(1)).isEqualTo(1);
        assertThat(IntakeV2ReportingEventHandler.getBackoffTimeSeconds(2)).isEqualTo(4);
        assertThat(IntakeV2ReportingEventHandler.getBackoffTimeSeconds(3)).isEqualTo(9);
        assertThat(IntakeV2ReportingEventHandler.getBackoffTimeSeconds(4)).isEqualTo(16);
        assertThat(IntakeV2ReportingEventHandler.getBackoffTimeSeconds(5)).isEqualTo(25);
        assertThat(IntakeV2ReportingEventHandler.getBackoffTimeSeconds(6)).isEqualTo(36);
        assertThat(IntakeV2ReportingEventHandler.getBackoffTimeSeconds(7)).isEqualTo(36);
    }

    @Test
    void testRandomJitter() {
        assertThat(IntakeV2ReportingEventHandler.getRandomJitter(0)).isZero();
        assertThat(IntakeV2ReportingEventHandler.getRandomJitter(1)).isZero();
        assertThat(IntakeV2ReportingEventHandler.getRandomJitter(100)).isBetween(-10L, 10L);
    }

    private void reportTransaction(IntakeV2ReportingEventHandler reportingEventHandler) {
        final ReportingEvent reportingEvent = new ReportingEvent();
        reportingEvent.setTransaction(new Transaction(MockTracer.create()));

        reportingEventHandler.onEvent(reportingEvent, -1, true);
    }

    private void reportSpan() {
        final ReportingEvent reportingEvent = new ReportingEvent();
        reportingEvent.setSpan(new Span(MockTracer.create()));

        reportingEventHandler.onEvent(reportingEvent, -1, true);
    }

    private void reportError() {
        final ReportingEvent reportingEvent = new ReportingEvent();
        reportingEvent.setError(new ErrorCapture(MockTracer.create()));

        reportingEventHandler.onEvent(reportingEvent, -1, true);
    }

    private void sendShutdownEvent() {
        final ReportingEvent reportingEvent = new ReportingEvent();
        reportingEvent.shutdownEvent();
        reportingEventHandler.onEvent(reportingEvent, -1, true);
    }

    private List<JsonNode> getNdJsonNodes() {
        return Stream.of(mockApmServer1, mockApmServer2)
            .flatMap(apmServer -> apmServer.findAll(postRequestedFor(urlEqualTo(INTAKE_V2_URL))).stream())
            .findFirst()
            .map(request -> new BufferedReader(new InputStreamReader(new InflaterInputStream(new ByteArrayInputStream(request.getBody()))))
                .lines()
                .map(IntakeV2ReportingEventHandlerTest::getReadTree)
                .collect(Collectors.toList()))
            .orElseThrow(() -> new IllegalStateException("No matching requests for POST " + INTAKE_V2_URL));
    }

}
