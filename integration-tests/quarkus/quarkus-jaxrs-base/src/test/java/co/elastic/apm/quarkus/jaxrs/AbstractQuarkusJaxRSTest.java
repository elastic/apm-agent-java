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
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockserver.client.MockServerClient;
import org.mockserver.model.ClearType;
import org.mockserver.model.HttpRequest;
import org.testcontainers.Testcontainers;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MockServerContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

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

public abstract class AbstractQuarkusJaxRSTest {

    private static final int DEBUG_PORT = -1; // set to something else (e.g. 5005) to enable remote debugging of the quarkus app


    private static MockServerContainer mockServer;

    private static MockServerClient mockServerClient;

    private static GenericContainer<?> app;


    @BeforeAll
    static void setUpAppAndApmServer() {
        Network network = Network.newNetwork();
        mockServer = new MockServerContainer(DockerImageName
            .parse("mockserver/mockserver")
            .withTag("mockserver-" + MockServerClient.class.getPackage().getImplementationVersion()))
            .withNetwork(network)
            .withNetworkAliases("apm-server");
        mockServer.start();
        mockServerClient = new MockServerClient(mockServer.getHost(), mockServer.getServerPort());
        mockServerClient.when(request("/")).respond(response().withStatusCode(200).withBody(json("{\"version\": \"7.13.0\"}")));
        mockServerClient.when(request("/config/v1/agents")).respond(response().withStatusCode(403));
        mockServerClient.when(request("/intake/v2/events")).respond(response().withStatusCode(200));

        if (DEBUG_PORT > 0) {
            Testcontainers.exposeHostPorts(DEBUG_PORT);
        }

        String cmd = new StringBuilder()
            .append("java ")
            .append(DEBUG_PORT <= 0 ? "" : String.format("-agentlib:jdwp=transport=dt_socket,server=n,address=%s:%d,suspend=y ", "host.testcontainers.internal", DEBUG_PORT))
            .append("-javaagent:/tmp/elastic-apm-agent.jar -jar /srv/quarkus-app/quarkus-run.jar")
            .toString();

        app = new GenericContainer<>("openjdk:11")
            .withCommand(cmd)
            .withCopyFileToContainer(MountableFile.forHostPath(AgentFileAccessor.getPathToJavaagent()), "/tmp/elastic-apm-agent.jar")
            .withCopyFileToContainer(MountableFile.forHostPath("target/quarkus-app"), "/srv/quarkus-app")
            .withEnv("ELASTIC_APM_SERVER_URL", "http://apm-server:" + MockServerContainer.PORT)
            .withEnv("ELASTIC_APM_REPORT_SYNC", "true")
            .withEnv("ELASTIC_APM_DISABLE_METRICS", "true")
            .withEnv("ELASTIC_APM_LOG_LEVEL", "DEBUG")
            .withEnv("ELASTIC_APM_APPLICATION_PACKAGES", "co.elastic.apm.quarkus.jaxrs")
            .withEnv("ELASTIC_APM_ENABLE_EXPERIMENTAL_INSTRUMENTATIONS", "true")
            .withEnv("QUARKUS_HTTP_PORT", "8080")
            .withNetwork(network)
            .withExposedPorts(8080)
            .waitingFor(Wait.forLogMessage(".*Installed features.*", 1))
            .dependsOn(mockServer);
        app.start();
    }

    @AfterAll
    public static void destroyContainers() {
        if (app != null) {
            app.stop();
        }
        if (mockServer != null) {
            mockServer.stop();
        }
    }

    @AfterEach
    void clearMockServerLog() {
        mockServerClient.clear(request(), ClearType.LOG);
    }

    private static List<Map<String, Object>> getReportedTransactions() {
        return Arrays.stream(mockServerClient.retrieveRecordedRequests(request("/intake/v2/events")))
            .map(HttpRequest::getBodyAsString)
            .flatMap(s -> Arrays.stream(s.split("\r?\n")))
            .map(JsonPath::parse)
            .flatMap(dc -> ((List<Map<String, Object>>) dc.read("$[?(@.transaction)].transaction")).stream())
            .collect(Collectors.toList());
    }

    @Test
    public void greetingShouldReturnDefaultMessage() {
        given()
            .baseUri("http://" + app.getHost() + ":" + app.getMappedPort(8080))
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
