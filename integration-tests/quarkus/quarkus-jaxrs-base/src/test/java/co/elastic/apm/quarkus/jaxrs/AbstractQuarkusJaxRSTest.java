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

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockserver.client.MockServerClient;
import org.mockserver.model.ClearType;
import org.mockserver.model.HttpRequest;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.File;
import java.io.FileFilter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.JsonBody.json;

@Testcontainers
public abstract class AbstractQuarkusJaxRSTest {

    private static final Network NETWORK = Network.newNetwork();

    @Container
    private static final GenericContainer<?> MOCK_SERVER = new GenericContainer<>(DockerImageName.parse("mockserver/mockserver:mockserver-5.4.1"))
        .withNetwork(NETWORK)
        .withNetworkAliases("apm-server")
        .withExposedPorts(1080)
        .waitingFor(Wait.forHttp("/mockserver/status").withMethod("PUT").forStatusCode(200));

    private static MockServerClient MOCK_SERVER_CLIENT;

    @Container
    final GenericContainer<?> APP = new GenericContainer<>("openjdk:11")
        .withCommand("java -javaagent:/tmp/elastic-apm-agent.jar -jar /srv/quarkus-app/quarkus-run.jar")
        .withFileSystemBind(getAgentJar(), "/tmp/elastic-apm-agent.jar")
        .withEnv("ELASTIC_APM_SERVER_URL", "http://apm-server:1080")
        .withEnv("ELASTIC_APM_REPORT_SYNC", "true")
        .withEnv("ELASTIC_APM_DISABLE_METRICS", "true")
        .withEnv("ELASTIC_APM_APPLICATION_PACKAGES", "co.elastic.apm.quarkus.jaxrs")
        .withEnv("ELASTIC_APM_ENABLE_EXPERIMENTAL_INSTRUMENTATIONS", "true")
        .withFileSystemBind("target/quarkus-app", "/srv/quarkus-app")
        .withEnv("QUARKUS_HTTP_PORT", "8080")
        .withNetwork(NETWORK)
        .withExposedPorts(8080)
        .waitingFor(Wait.forLogMessage(".*Installed features.*", 1))
        .dependsOn(MOCK_SERVER);

    @BeforeAll
    static void setUpMockServerClient() {
        MOCK_SERVER_CLIENT = new MockServerClient(MOCK_SERVER.getContainerIpAddress(), MOCK_SERVER.getMappedPort(1080));
        MOCK_SERVER_CLIENT.when(request("/")).respond(response().withStatusCode(200).withBody(json("{\"version\": \"7.13.0\"}")));
        MOCK_SERVER_CLIENT.when(request("/config/v1/agents")).respond(response().withStatusCode(403));
        MOCK_SERVER_CLIENT.when(request("/intake/v2/events")).respond(response().withStatusCode(200));
    }

    @AfterEach
    void clearMockServerLog() {
        MOCK_SERVER_CLIENT.clear(request(), ClearType.LOG);
    }

    private static String getAgentJar() {
        File agentBuildDir = new File("../../../elastic-apm-agent/target/");
        FileFilter fileFilter = file -> file.getName().matches("elastic-apm-agent-\\d\\.\\d+\\.\\d+(\\.RC\\d+)?(-SNAPSHOT)?.jar");
        return Arrays.stream(agentBuildDir.listFiles(fileFilter))
            .map(File::getAbsolutePath)
            .findFirst()
            .orElse(null);
    }

    private static List<Map<String, Object>> getReportedTransactions() {
        return Arrays.stream(MOCK_SERVER_CLIENT.retrieveRecordedRequests(request("/intake/v2/events")))
            .map(HttpRequest::getBodyAsString)
            .flatMap(s -> Arrays.stream(s.split("\r?\n")))
            .map(JsonPath::parse)
            .flatMap(dc -> ((List<Map<String, Object>>) dc.read("$[?(@.transaction)].transaction")).stream())
            .collect(Collectors.toList());
    }

    @Test
    public void greetingShouldReturnDefaultMessage() {
        given()
            .baseUri("http://" + APP.getContainerIpAddress() + ":" + APP.getMappedPort(8080))
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
}
