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
import okhttp3.logging.HttpLoggingInterceptor;
import org.junit.Before;
import org.junit.BeforeClass;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.images.builder.ImageFromDockerfile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockserver.model.HttpRequest.request;

/**
 * When you want to execute the test in the IDE, execute {@code mvn clean package} before.
 * This creates the {@code ROOT.war} file,
 * which is bound into the docker container.
 * <p>
 * Whenever you make changes to the application,
 * you have to rerun {@code mvn clean package}.
 * </p>
 * <p>
 * To debug simple-webapp which is deployed to tomcat,
 * add a break point in {@link #setUp()} and evaluate tomcatContainer.getMappedPort(8000).
 * Then create a remote debug configuration in IntelliJ using this port and start debugging.
 * TODO use {@link org.testcontainers.containers.SocatContainer} to always have the same debugging port
 * </p>
 */
public abstract class AbstractTomcatIntegrationTest {

    private static final Logger logger = LoggerFactory.getLogger(ServletIntegrationTest.class);

    protected static GenericContainer tomcatContainer = new GenericContainer<>(
        new ImageFromDockerfile()
            .withDockerfileFromBuilder(builder -> builder
                .from("tomcat:8")
                .env("JPDA_ADDRESS", "8000")
                .env("JPDA_TRANSPORT", "dt_socket")
                .run("rm -rf /usr/local/tomcat/webapps/*")
                .expose(8080, 8000)
                .entryPoint("catalina.sh", "jpda", "run")))
        .withNetwork(Network.SHARED)
        .withEnv("ELASTIC_APM_SERVER_URL", "http://apm-server:1080")
        .withEnv("ELASTIC_APM_SERVICE_NAME", "servlet-test-app")
        .withEnv("ELASTIC_APM_IGNORE_URLS", "/apm/*")
        .withLogConsumer(new Slf4jLogConsumer(logger))
        .withFileSystemBind("../simple-webapp/target/ROOT.war", "/usr/local/tomcat/webapps/ROOT.war")
        .withExposedPorts(8080, 8000);
    protected static MockServerContainer mockServerContainer = new MockServerContainer()
        .withNetworkAliases("apm-server")
        .withNetwork(Network.SHARED);

    static {
        Stream.of(tomcatContainer, mockServerContainer).parallel().forEach(GenericContainer::start);
    }

    protected OkHttpClient httpClient;
    private JsonSchema schema;


    @BeforeClass
    public static void beforeClass() {
        mockServerContainer.getClient().when(request("/v1/transactions")).respond(HttpResponse.response().withStatusCode(200));
        mockServerContainer.getClient().when(request("/v1/errors")).respond(HttpResponse.response().withStatusCode(200));
    }

    @Before
    public void setUp() {
        schema = JsonSchemaFactory.getInstance().getSchema(getClass().getResourceAsStream("/schema/transactions/payload.json"));
        final HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor(logger::info);
        loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY);
        httpClient = new OkHttpClient.Builder().addInterceptor(loggingInterceptor).build();
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
                .host(tomcatContainer.getContainerIpAddress())
                .port(tomcatContainer.getFirstMappedPort())
                .encodedPath("/apm/flush")
                .build())
            .build())
            .execute();
    }
}
