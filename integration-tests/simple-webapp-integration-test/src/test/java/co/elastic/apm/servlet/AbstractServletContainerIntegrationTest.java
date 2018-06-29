/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 Elastic and contributors
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
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.logging.HttpLoggingInterceptor;
import org.apache.commons.io.filefilter.WildcardFileFilter;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockserver.model.HttpRequest.request;

public abstract class AbstractServletContainerIntegrationTest {
    protected static final String pathToWar = "../simple-webapp/target/ROOT.war";
    protected static final String pathToJavaagent;
    private static final Logger logger = LoggerFactory.getLogger(AbstractServletContainerIntegrationTest.class);
    protected static MockServerContainer mockServerContainer = new MockServerContainer()
        .withNetworkAliases("apm-server")
        .withNetwork(Network.SHARED);
    protected static OkHttpClient httpClient;
    protected static JsonSchema schema;

    static {
        mockServerContainer.start();
        schema = JsonSchemaFactory.getInstance().getSchema(
            TomcatIT.class.getResourceAsStream("/schema/transactions/payload.json"));
        final HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor(logger::info);
        loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY);
        httpClient = new OkHttpClient.Builder().addInterceptor(loggingInterceptor).build();
        pathToJavaagent = getPathToJavaagent();
        checkFilePresent(pathToWar);
        checkFilePresent(pathToJavaagent);
    }

    private final GenericContainer servletContainer;
    private final int webPort;
    private final String contextPath;

    protected AbstractServletContainerIntegrationTest(GenericContainer servletContainer) {
        this(servletContainer, 8080, "");
    }

    protected AbstractServletContainerIntegrationTest(GenericContainer servletContainer, int webPort, String contextPath) {
        this.servletContainer = servletContainer;
        this.webPort = webPort;
        this.contextPath = contextPath;
    }

    private static String getPathToJavaagent() {
        File agentBuildDir = new File("../../elastic-apm-agent/target/");
        FileFilter fileFilter = new WildcardFileFilter("elastic-apm-agent-*.jar");
        for (File file : agentBuildDir.listFiles(fileFilter)) {
            if (!file.getAbsolutePath().endsWith("javadoc.jar") && !file.getAbsolutePath().endsWith("sources.jar")) {
                return file.getAbsolutePath();
            }
        }
        return null;
    }

    private static void checkFilePresent(String pathToWar) {
        final File warFile = new File(pathToWar);
        logger.info("Check file {}", warFile.getAbsolutePath());
        assertThat(warFile).exists();
        assertThat(warFile).isFile();
        assertThat(warFile.length()).isGreaterThan(0);
    }

    @Before
    public final void setUpMockServer() {
        mockServerContainer.getClient().when(request("/v1/transactions")).respond(HttpResponse.response().withStatusCode(200));
        mockServerContainer.getClient().when(request("/v1/errors")).respond(HttpResponse.response().withStatusCode(200));
        mockServerContainer.getClient().when(request("/healthcheck")).respond(HttpResponse.response().withStatusCode(200));
        servletContainer.waitingFor(Wait.forHttp(contextPath + "/status.jsp").forPort(webPort));
        servletContainer.start();
    }

    @After
    public final void clearMockServer() {
        mockServerContainer.getClient().clear(HttpRequest.request());
        servletContainer.stop();
    }

    private void validateJsonSchema(JsonNode payload) {
        Set<ValidationMessage> errors = schema.validate(payload);
        assertThat(errors).isEmpty();
    }

    @Test
    public void testTransactionReporting() throws Exception {
        final Response response = httpClient.newCall(new Request.Builder()
            .get()
            .url(getBaseUrl() + getPathToTest())
            .build())
            .execute();

        assertThat(response.code()).withFailMessage(response.toString()).isEqualTo(200);
        final ResponseBody responseBody = response.body();
        assertThat(responseBody).isNotNull();
        assertThat(responseBody.string()).contains("Hello World");

        final List<JsonNode> reportedTransactions = getReportedTransactions();
        assertThat(reportedTransactions.size()).isEqualTo(1);
        assertThat(reportedTransactions.iterator().next().get("context").get("request").get("url").get("pathname").textValue())
            .isEqualTo(contextPath + getPathToTest());
    }

    @NotNull
    protected String getPathToTest() {
        return "/index.jsp";
    }

    private String getBaseUrl() {
        return "http://" + servletContainer.getContainerIpAddress() + ":" + servletContainer.getMappedPort(webPort) + contextPath;
    }

    private List<JsonNode> getReportedTransactions() throws IOException {
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
}
