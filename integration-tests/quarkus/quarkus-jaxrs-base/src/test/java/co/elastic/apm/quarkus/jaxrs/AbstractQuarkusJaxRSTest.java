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
package co.elastic.apm.quarkus.jaxrs;

import co.elastic.apm.agent.test.AgentFileAccessor;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.Testcontainers;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

import static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder.responseDefinition;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

public abstract class AbstractQuarkusJaxRSTest {

    // set to something else (e.g. 5005) to enable remote debugging of the quarkus app
    private static final int DEBUG_PORT = -1;
    // set to true to enable verbose container debug logging
    private static final boolean DEBUG_LOG = false;

    private static final int APM_SERVER_PORT = 8080;
    private static final String APM_SERVER_HOST = "apm-server";
    private static final int APP_PORT = 8080;
    private static final String INTAKE_V2_EVENTS = "/intake/v2/events";
    private static GenericContainer<?> mockApmServer;
    private static WireMock wireMock;

    private static GenericContainer<?> app;

    @BeforeAll
    static void setUpAppAndApmServer() {
        Network network = Network.newNetwork();
        mockApmServer = new GenericContainer<>("wiremock/wiremock:3.13.2")
            .withNetwork(network)
            .withExposedPorts(APM_SERVER_PORT)
            .waitingFor(Wait.forHealthcheck())
            .withNetworkAliases(APM_SERVER_HOST);

        if (DEBUG_LOG) {
            mockApmServer.withLogConsumer(outputFrame -> System.out.print(outputFrame.getUtf8String()));
        }

        mockApmServer.start();

        wireMock = WireMock.create()
            .host(mockApmServer.getHost())
            .port(mockApmServer.getMappedPort(APM_SERVER_PORT))
            .build();

        wireMock.register(any(urlEqualTo("/")).willReturn(responseDefinition().withBody("{\"version\": \"7.13.0\"}").withStatus(200)));
        wireMock.register(any(urlEqualTo("/config/v1/agents")).willReturn(responseDefinition().withStatus(403)));
        wireMock.register(any(urlEqualTo(INTAKE_V2_EVENTS)).willReturn(responseDefinition().withStatus(200)));

        if (DEBUG_PORT > 0) {
            Testcontainers.exposeHostPorts(DEBUG_PORT);
        }

        String cmd = new StringBuilder()
            .append("java ")
            .append(DEBUG_PORT <= 0 ? "" : String.format("-agentlib:jdwp=transport=dt_socket,server=n,address=%s:%d,suspend=y ", "host.testcontainers.internal", DEBUG_PORT))
            .append("-javaagent:/tmp/elastic-apm-agent.jar -jar /srv/quarkus-app/quarkus-run.jar")
            .toString();

        app = new GenericContainer<>("azul/zulu-openjdk:11-latest")
            .withCommand(cmd)
            .withCopyFileToContainer(MountableFile.forHostPath(AgentFileAccessor.getPathToJavaagent()), "/tmp/elastic-apm-agent.jar")
            .withCopyFileToContainer(MountableFile.forHostPath("target/quarkus-app"), "/srv/quarkus-app")
            .withEnv("ELASTIC_APM_SERVER_URL", "http://" + APM_SERVER_HOST + ":" + APM_SERVER_PORT)
            .withEnv("ELASTIC_APM_REPORT_SYNC", "true")
            .withEnv("ELASTIC_APM_CLOUD_PROVIDER", "none")
            .withEnv("ELASTIC_APM_DISABLE_METRICS", "true")
            .withEnv("ELASTIC_APM_LOG_LEVEL", "DEBUG")
            .withEnv("ELASTIC_APM_APPLICATION_PACKAGES", "co.elastic.apm.quarkus.jaxrs")
            .withEnv("ELASTIC_APM_ENABLE_EXPERIMENTAL_INSTRUMENTATIONS", "true")
            .withEnv("QUARKUS_HTTP_PORT", Integer.toString(APP_PORT))
            .withNetwork(network)
            .withExposedPorts(APP_PORT)
            .waitingFor(Wait.forLogMessage(".*Installed features.*", 1))
            .dependsOn(mockApmServer);

        if (DEBUG_LOG) {
            app.withLogConsumer(outputFrame -> System.out.print(outputFrame.getUtf8String()));
        }

        app.start();
    }

    @AfterAll
    public static void destroyContainers() {
        if (app != null) {
            app.stop();
        }
        if (mockApmServer != null) {
            mockApmServer.stop();
        }
    }

    @AfterEach
    void clearMockServerLog() {
        wireMock.resetRequests();
    }

    private static List<Map<String, Object>> getReportedTransactions() {
        return wireMock.find(RequestPatternBuilder.newRequestPattern().withUrl(INTAKE_V2_EVENTS)).stream()
            // wiremock provides the raw request body without any decompression so we have to do it explicitly ourselves
            .map(action -> decompressZlib(action.getBody()))
            .flatMap(requestBody -> Arrays.stream(requestBody.split("\r?\n")))
            .map(JsonPath::parse)
            .flatMap(dc -> ((List<Map<String, Object>>) dc.read("$[?(@.transaction)].transaction")).stream())
            .collect(Collectors.toList());
    }

    @Test
    public void
    greetingShouldReturnDefaultMessage() {
        given()
            .baseUri("http://" + app.getHost() + ":" + app.getMappedPort(APP_PORT))
            .when()
            .get("/")
            .then()
            .statusCode(200)
            .body(is("Hello World"));

        List<Map<String, Object>> transactions = getReportedTransactions();
        assertThat(transactions).hasSize(1);

        Map<String, Object> transaction = transactions.get(0);
        assertThat((String) JsonPath.read(transaction, "$.name")).isEqualTo("TestApp#greeting");
        assertThat((String) JsonPath.read(transaction, "$.context.user.id")).isEqualTo("id");
        assertThat((String) JsonPath.read(transaction, "$.context.user.email")).isEqualTo("email");
        assertThat((String) JsonPath.read(transaction, "$.context.user.username")).isEqualTo("username");
        assertThat((String) JsonPath.read(transaction, "$.context.service.framework.name")).isEqualTo("JAX-RS");
        assertThat((String) JsonPath.read(transaction, "$.context.service.framework.version")).isEqualTo("2.0.1.Final");
    }

    private static String decompressZlib(byte[] input) {
        Inflater inflater = new Inflater();
        inflater.setInput(input);
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            while (!inflater.finished()) {
                int count = inflater.inflate(buffer);
                output.write(buffer, 0, count);
            }
            inflater.end();
            return output.toString(StandardCharsets.UTF_8);
        } catch (DataFormatException e) {
            throw new IllegalStateException(e);
        }
    }
}
