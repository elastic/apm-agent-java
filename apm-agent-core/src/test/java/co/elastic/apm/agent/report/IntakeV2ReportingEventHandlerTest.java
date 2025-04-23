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

import co.elastic.apm.agent.MockTracer;
import co.elastic.apm.agent.common.util.Version;
import co.elastic.apm.agent.configuration.CoreConfigurationImpl;
import co.elastic.apm.agent.configuration.SpyConfiguration;
import co.elastic.apm.agent.impl.error.ErrorCaptureImpl;
import co.elastic.apm.agent.impl.metadata.MetaDataMock;
import co.elastic.apm.agent.impl.metadata.ProcessInfo;
import co.elastic.apm.agent.impl.metadata.ServiceImpl;
import co.elastic.apm.agent.impl.metadata.SystemInfo;
import co.elastic.apm.agent.impl.transaction.SpanImpl;
import co.elastic.apm.agent.impl.transaction.TransactionImpl;
import co.elastic.apm.agent.report.processor.ProcessorEventHandler;
import co.elastic.apm.agent.report.serialize.DslJsonSerializer;
import com.dslplatform.json.DslJson;
import com.dslplatform.json.JsonWriter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
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
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static co.elastic.apm.agent.report.IntakeV2ReportingEventHandler.INTAKE_V2_URL;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
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
        ResponseDefinitionBuilder versionResponse = ResponseDefinitionBuilder.okForJson(Map.of("ok", Map.of("version", "99.99.99")));
        mockApmServer1.stubFor(get(urlEqualTo("/")).willReturn(versionResponse));
        mockApmServer1.stubFor(post(INTAKE_V2_URL).willReturn(ok()));
        mockApmServer2.stubFor(get(urlEqualTo(APM_SERVER_PATH + "/")).willReturn(versionResponse));
        mockApmServer2.stubFor(post(APM_SERVER_PATH + INTAKE_V2_URL).willReturn(ok()));

        mockApmServer1.start();
        mockApmServer2.start();

        final ConfigurationRegistry configurationRegistry = SpyConfiguration.createSpyConfig();
        final ReporterConfigurationImpl reporterConfiguration = configurationRegistry.getConfig(ReporterConfigurationImpl.class);
        final CoreConfigurationImpl coreConfiguration = configurationRegistry.getConfig(CoreConfigurationImpl.class);
        SystemInfo system = new SystemInfo("x64", "localhost", null, "platform");
        final ProcessInfo title = new ProcessInfo("title");
        final ServiceImpl service = new ServiceImpl();
        apmServerClient = new ApmServerClient(configurationRegistry);
        apmServerClient.start(List.of(
            new URL(HTTP_LOCALHOST + mockApmServer1.port()),
            // testing ability to configure a server url with additional path (ending with "/" in this case)
            new URL(HTTP_LOCALHOST + mockApmServer2.port() + APM_SERVER_PATH + "/")
        ));
        reportingEventHandler = new IntakeV2ReportingEventHandler(
            reporterConfiguration,
            mock(ProcessorEventHandler.class),
            new DslJsonSerializer(
                SpyConfiguration.createSpyConfig(),
                apmServerClient,
                MetaDataMock.create(title, service, system, null, Collections.emptyMap(), null)
            ),
            apmServerClient);

        final ProcessInfo title1 = new ProcessInfo("title");
        final ServiceImpl service1 = new ServiceImpl();
        ApmServerClient nonConnectedApmServerClient = new ApmServerClient(configurationRegistry);
        nonConnectedApmServerClient.start(List.of(new URL("http://non.existing:8080")));
        nonConnectedReportingEventHandler = new IntakeV2ReportingEventHandler(
            reporterConfiguration,
            mock(ProcessorEventHandler.class),
            new DslJsonSerializer(
                SpyConfiguration.createSpyConfig(),
                nonConnectedApmServerClient,
                MetaDataMock.create(title1, service1, system, null, Collections.emptyMap(), null)
            ),
            nonConnectedApmServerClient);

        // ensure server version is set before test start
        Version version = apmServerClient.getApmServerVersion(10, TimeUnit.SECONDS);
        assertThat(version)
            .describedAs("server version should be known in client before test starts")
            .isNotNull();
        assertThat(version.toString()).isEqualTo("99.99.99");
    }

    @AfterEach
    void tearDown() {
        List.of(mockApmServer1, mockApmServer2).forEach(WireMockRule::stop);
    }

    @Test
    void testUrls() throws MalformedURLException {
        URL server1url = apmServerClient.appendPathToCurrentUrl(INTAKE_V2_URL);
        assertThat(server1url).isNotNull();
        assertThat(server1url.toString()).isEqualTo(HTTP_LOCALHOST + mockApmServer1.port() + INTAKE_V2_URL);
        apmServerClient.onConnectionError();
        URL server2url = apmServerClient.appendPathToCurrentUrl(INTAKE_V2_URL);
        assertThat(server2url).isNotNull();
        assertThat(server2url.toString()).isEqualTo(HTTP_LOCALHOST + mockApmServer2.port() + APM_SERVER_PATH + INTAKE_V2_URL);
        // just to restore
        apmServerClient.onConnectionError();
    }

    @Test
    void testReport() throws Exception {
        reportTransaction(reportingEventHandler);
        reportSpan();
        reportError();
        reportLog(); // FIXME: moving 'reportLog' after 'reportMetrics' make the log event to be skipped
        reportMetrics();

        assertThat(reportingEventHandler.getBufferSize()).isGreaterThan(0);
        reportingEventHandler.endRequest();
        assertThat(reportingEventHandler.getBufferSize()).isEqualTo(0);

        final List<JsonNode> ndJsonNodes = getNdJsonNodes();
        assertThat(ndJsonNodes).hasSize(6);
        assertThat(ndJsonNodes.get(0).get("metadata")).isNotNull();
        assertThat(ndJsonNodes.get(1).get("transaction")).isNotNull();
        assertThat(ndJsonNodes.get(2).get("span")).isNotNull();
        assertThat(ndJsonNodes.get(3).get("error")).isNotNull();
        assertThat(ndJsonNodes.get(4).get("log")).isNotNull();
        assertThat(ndJsonNodes.get(5).get("metrics")).isNotNull();
    }

    @Test
    void testNoopWhenNotConnected() throws Exception {
        reportTransaction(nonConnectedReportingEventHandler);
        assertThat(nonConnectedReportingEventHandler.getBufferSize()).isEqualTo(0);
    }

    @Test
    void testShutDown() throws Exception {
        reportTransaction(reportingEventHandler);
        sendShutdownEvent();
        reportSpan();
        reportingEventHandler.endRequest();

        final List<JsonNode> ndJsonNodes = getNdJsonNodes();
        assertThat(ndJsonNodes).hasSize(2);
        assertThat(ndJsonNodes.get(0).get("metadata")).isNotNull();
        assertThat(ndJsonNodes.get(1).get("transaction")).isNotNull();
    }

    @Test
    void testReportRoundRobinOnServerError() throws Exception {
        mockApmServer1.stubFor(post(INTAKE_V2_URL).willReturn(serviceUnavailable()));

        reportTransaction(reportingEventHandler);
        reportingEventHandler.endRequest();
        mockApmServer1.verify(postRequestedFor(urlEqualTo(INTAKE_V2_URL)));
        mockApmServer2.verify(0, postRequestedFor(urlEqualTo(INTAKE_V2_URL)));

        mockApmServer1.resetRequests();
        mockApmServer2.resetRequests();

        reportTransaction(reportingEventHandler);
        reportingEventHandler.endRequest();
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

    private void reportTransaction(IntakeV2ReportingEventHandler reportingEventHandler) throws Exception {
        final ReportingEvent reportingEvent = new ReportingEvent();
        reportingEvent.setTransaction(new TransactionImpl(MockTracer.create()));

        reportingEventHandler.onEvent(reportingEvent, -1, true);
    }

    private void reportSpan() throws Exception {
        final ReportingEvent reportingEvent = new ReportingEvent();
        reportingEvent.setSpan(new SpanImpl(MockTracer.create()));

        reportingEventHandler.onEvent(reportingEvent, -1, true);
    }

    private void reportError() throws Exception {
        final ReportingEvent reportingEvent = new ReportingEvent();
        reportingEvent.setError(new ErrorCaptureImpl(MockTracer.create()));

        reportingEventHandler.onEvent(reportingEvent, -1, true);
    }

    private void reportMetrics() throws Exception {
        final ReportingEvent reportingEvent = new ReportingEvent();
        JsonWriter jw = new DslJson<>().newWriter();
        jw.writeAscii("{\"metrics\":{}}"); // dummy metrics event that is written as-is
        reportingEvent.setMetricSet(jw);
        reportingEventHandler.onEvent(reportingEvent, -1, true);
    }

    private void reportLog() throws Exception {
        final ReportingEvent reportingEvent = new ReportingEvent();
        reportingEvent.setStringLog("{}"); // dummy log event
        reportingEventHandler.onEvent(reportingEvent, -1, true);
    }

    private void sendShutdownEvent() throws Exception {
        final ReportingEvent reportingEvent = new ReportingEvent();
        reportingEvent.shutdownEvent();
        reportingEventHandler.onEvent(reportingEvent, -1, true);
    }

    private List<JsonNode> getNdJsonNodes() {
        return Stream.of(mockApmServer1, mockApmServer2)
            .flatMap(apmServer -> apmServer.findAll(postRequestedFor(urlEqualTo(INTAKE_V2_URL))).stream())
            .findFirst()
            .map(request -> new BufferedReader(new InputStreamReader(new ByteArrayInputStream(request.getBody())))
                .lines()
                .map(IntakeV2ReportingEventHandlerTest::getReadTree)
                .collect(Collectors.toList()))
            .orElseThrow(() -> new IllegalStateException("No matching requests for POST " + INTAKE_V2_URL));
    }

}
