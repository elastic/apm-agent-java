/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 the original author or authors
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package co.elastic.apm.servlet;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.ValidationMessage;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.images.builder.ImageFromDockerfile;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockserver.model.HttpRequest.request;

public class AbstractTomcatIntegrationTest {

    private static final Logger logger = LoggerFactory.getLogger(ServletIntegrationTest.class);

    @ClassRule
    public static GenericContainer c = new GenericContainer<>(
        new ImageFromDockerfile()
            .withDockerfileFromBuilder(builder -> builder
                .from("tomcat:9")
                .run("rm -rf /usr/local/tomcat/webapps/*")
                .copy("ROOT.war", "/usr/local/tomcat/webapps/ROOT.war")
                // TODO debugging does not work
                // to get the randomized debugger port, evaluate c.getMappedPort(8000)
                // then create a remote debug configuration in IntelliJ using this port
                // Error running 'Debug': Unable to open debugger port (localhost:33049): java.io.IOException "handshake failed - connection prematurally closed"
                .env("JPDA_ADDRESS", "8000")
                .env("JPDA_TRANSPORT", "dt_socket")
                .env("ELASTIC_APM_SERVER_URL", "http://apm-server:1080")
                .env("ELASTIC_APM_SERVICE_NAME", "servlet-test-app")
                .env("ELASTIC_APM_IGNORE_URLS", "/apm/*")
                .expose(8080, 8000)
                .entryPoint("catalina.sh", "jpda", "run")
            )
            // TODO chicken egg problem here: tests require the war to be present, which is built via mvn package, but mvn package executes the tests
            .withFileFromPath("ROOT.war", Paths.get("target/ROOT.war")))
        .withNetwork(Network.SHARED)
        .withExposedPorts(8080, 8000);
    @ClassRule
    public static MockServerContainer mockServerContainer = new MockServerContainer()
        .withNetworkAliases("apm-server")
        .withNetwork(Network.SHARED);
    protected OkHttpClient httpClient = new OkHttpClient.Builder().build();
    private JsonSchema schema;

    @BeforeClass
    public static void beforeClass() {
        mockServerContainer.getClient().when(request("/v1/transactions")).respond(HttpResponse.response().withStatusCode(200));
        mockServerContainer.getClient().when(request("/v1/errors")).respond(HttpResponse.response().withStatusCode(200));
        c.followOutput(new Slf4jLogConsumer(logger));
    }

    @Before
    public void setUp() {
        schema = JsonSchemaFactory.getInstance().getSchema(getClass().getResourceAsStream("/schema/transactions/payload.json"));
    }

    protected List<JsonNode> getReportedTransactions() throws IOException {
        flushReporterQueue();
        final List<JsonNode> transactions = new ArrayList<>();
        final ObjectMapper objectMapper = new ObjectMapper();
        for (HttpRequest httpRequest : mockServerContainer.getClient().retrieveRecordedRequests(request("/v1/transactions"))) {
            final JsonNode payload = objectMapper.readTree(httpRequest.getBodyAsString());
            validateJsonSchema(payload);
            for (JsonNode transaction : payload.get("transactions")) {
                transactions.add(transaction);
            }
        }
        return transactions;
    }

    private void validateJsonSchema(JsonNode payload) {
        Set<ValidationMessage> errors = schema.validate(payload);
        assertThat(errors).isEmpty();
    }

    /**
     * Makes sure all pending items in the {@link co.elastic.apm.report.Reporter} queue are flushed to the APM server mock
     */
    private void flushReporterQueue() throws IOException {
        httpClient.newCall(new Request.Builder()
            .post(RequestBody.create(null, new byte[0]))
            .url(new HttpUrl.Builder()
                .scheme("http")
                .host(c.getContainerIpAddress())
                .port(c.getFirstMappedPort())
                .encodedPath("/apm/flush")
                .build())
            .build())
            .execute();
    }
}
