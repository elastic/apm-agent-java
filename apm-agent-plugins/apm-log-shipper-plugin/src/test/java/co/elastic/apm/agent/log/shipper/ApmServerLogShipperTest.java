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
package co.elastic.apm.agent.log.shipper;

import co.elastic.apm.agent.configuration.CoreConfiguration;
import co.elastic.apm.agent.configuration.SpyConfiguration;
import co.elastic.apm.agent.impl.metadata.MetaDataMock;
import co.elastic.apm.agent.impl.stacktrace.StacktraceConfiguration;
import co.elastic.apm.agent.report.ApmServerClient;
import co.elastic.apm.agent.report.ReporterConfiguration;
import co.elastic.apm.agent.report.serialize.DslJsonSerializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.stagemonitor.configuration.ConfigurationRegistry;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

@DisabledOnOs(OS.WINDOWS)
class ApmServerLogShipperTest {

    public WireMockServer mockApmServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
    private TailableFile tailableFile;
    private ApmServerLogShipper logShipper;
    private File logFile;
    private final ByteBuffer buffer = ByteBuffer.allocate(1024);
    private ApmServerClient apmServerClient;

    @BeforeEach
    void setUp() throws Exception {
        ConfigurationRegistry config = SpyConfiguration.createSpyConfig();
        mockApmServer.stubFor(post("/intake/v2/logs").willReturn(ok()));
        mockApmServer.stubFor(get("/").willReturn(ok()));
        mockApmServer.start();

        apmServerClient = new ApmServerClient(config.getConfig(ReporterConfiguration.class), config.getConfig(CoreConfiguration.class));
        startClientWithValidUrls();

        DslJsonSerializer serializer = new DslJsonSerializer(config.getConfig(StacktraceConfiguration.class), apmServerClient, MetaDataMock.create());
        logShipper = new ApmServerLogShipper(apmServerClient, config.getConfig(ReporterConfiguration.class), serializer);
        logFile = File.createTempFile("test", ".log");
        tailableFile = new TailableFile(logFile);
    }

    private void startClientWithValidUrls() throws MalformedURLException {
        apmServerClient.start(List.of(new URL("http", "localhost", mockApmServer.port(), "/")));
    }

    @AfterEach
    void tearDown() {
        mockApmServer.stop();

        if (!logFile.delete()) {
            logFile.deleteOnExit();
        }
    }

    @Test
    void testSendLogs() throws Exception {
        Files.write(logFile.toPath(), List.of("foo"));
        assertThat(tailableFile.tail(buffer, logShipper, 100)).isEqualTo(1);
        logShipper.endRequest();
        List<String> events = getEvents();
        mockApmServer.verify(postRequestedFor(urlEqualTo(ApmServerLogShipper.LOGS_ENDPOINT)));
        assertThat(events).hasSize(3);
        JsonNode fileMetadata = new ObjectMapper().readTree(events.get(1)).get("metadata").get("log").get("file");
        assertThat(fileMetadata.get("name").textValue()).isEqualTo(logFile.getName());
        assertThat(fileMetadata.get("path").textValue()).isEqualTo(logFile.getAbsolutePath());
        assertThat(events.get(2)).isEqualTo("foo");
    }

    @Test
    void testSendLogsAfterServerUrlsSet() throws Exception {
        apmServerClient.start(Collections.emptyList());
        Files.write(logFile.toPath(), List.of("foo"));
        assertThat(logShipper.getErrorCount()).isEqualTo(0);
        Future<Integer> readLinesFuture = Executors.newSingleThreadExecutor().submit(() -> tailableFile.tail(buffer, logShipper, 100));
        // Wait until first failure to send file lines
        Awaitility.await()
            .pollInterval(1, TimeUnit.MILLISECONDS)
            .timeout(500, TimeUnit.MILLISECONDS)
            .untilAsserted(() -> assertThat(logShipper.getErrorCount()).isGreaterThan(0));
        // Set valid APM server URLs
        startClientWithValidUrls();
        // Verify that after backing off, lines are sent to APM server
        assertThat(readLinesFuture.get(1500, TimeUnit.MILLISECONDS)).isEqualTo(1);
        assertThat(logShipper.getErrorCount()).isGreaterThan(0);
        logShipper.endRequest();
        assertThat(logShipper.getErrorCount()).isEqualTo(0);
        List<String> events = getEvents();
        mockApmServer.verify(postRequestedFor(urlEqualTo(ApmServerLogShipper.LOGS_ENDPOINT)));
        assertThat(events).hasSize(3);
        JsonNode fileMetadata = new ObjectMapper().readTree(events.get(1)).get("metadata").get("log").get("file");
        assertThat(fileMetadata.get("name").textValue()).isEqualTo(logFile.getName());
        assertThat(fileMetadata.get("path").textValue()).isEqualTo(logFile.getAbsolutePath());
        assertThat(events.get(2)).isEqualTo("foo");
    }

    private List<String> getEvents() {
        return mockApmServer.findAll(postRequestedFor(urlEqualTo(ApmServerLogShipper.LOGS_ENDPOINT)))
            .stream()
            .flatMap(request -> new BufferedReader(new InputStreamReader(new ByteArrayInputStream(request.getBody()))).lines())
            .collect(Collectors.toList());
    }
}
