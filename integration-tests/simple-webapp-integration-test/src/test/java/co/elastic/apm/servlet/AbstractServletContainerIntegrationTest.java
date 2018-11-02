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

import co.elastic.apm.MockReporter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.logging.HttpLoggingInterceptor;
import org.apache.commons.io.filefilter.WildcardFileFilter;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Test;
import org.mockserver.model.ClearType;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.SocatContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static co.elastic.apm.report.IntakeV2ReportingEventHandler.INTAKE_V2_URL;
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
 * To debug, add a remote debugging configuration for port 5005.
 * Note: not all server are configured for debugging.
 * Currently, {@link TomcatIT} and {@link PayaraIT} have debugging configured
 * </p>
 */
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
        mockServerContainer.getClient().when(request(INTAKE_V2_URL)).respond(HttpResponse.response().withStatusCode(200));
        mockServerContainer.getClient().when(request("/healthcheck")).respond(HttpResponse.response().withStatusCode(200));
        schema = JsonSchemaFactory.getInstance().getSchema(
            AbstractServletContainerIntegrationTest.class.getResourceAsStream("/schema/transactions/payload.json"));
        final HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor(logger::info);
        loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY);
        httpClient = new OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            // set to 0 for debugging
            .readTimeout(10, TimeUnit.SECONDS)
            .build();
        pathToJavaagent = getPathToJavaagent();
        checkFilePresent(pathToWar);
        checkFilePresent(pathToJavaagent);
    }

    private final MockReporter mockReporter = new MockReporter();
    private final GenericContainer servletContainer;
    private final int webPort;
    private final String contextPath;
    @Nullable
    private final GenericContainer<?> debugProxy;
    private final String expectedDefaultServiceName;

    protected AbstractServletContainerIntegrationTest(GenericContainer<?> servletContainer, String expectedDefaultServiceName) {
        this(servletContainer, 8080, "", expectedDefaultServiceName);
    }

    protected AbstractServletContainerIntegrationTest(GenericContainer<?> servletContainer, int webPort, String contextPath, String expectedDefaultServiceName) {
        this.servletContainer = servletContainer;
        this.webPort = webPort;
        this.contextPath = contextPath;
        this.debugProxy = createDebugProxy(servletContainer, 5005);
        this.expectedDefaultServiceName = expectedDefaultServiceName;
        if (debugProxy != null) {
            this.debugProxy.start();
        }
        this.servletContainer.waitingFor(Wait.forHttp(contextPath + "/status.jsp")
            .forPort(webPort)
            // set to a higher value for debugging
            .withStartupTimeout(Duration.ofSeconds(60)));
        this.servletContainer.start();
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

    // makes sure the debugging port is always 5005
    // if the port is not available, the test can still run
    @Nullable
    private GenericContainer<?> createDebugProxy(GenericContainer<?> servletContainer, final int debugPort) {
        try {
            return new SocatContainer() {{
                addFixedExposedPort(debugPort, debugPort);
            }}
                .withNetwork(Network.SHARED)
                .withTarget(debugPort, servletContainer.getNetworkAliases().get(0));
        } catch (Exception e) {
            logger.warn("Starting debug proxy failed");
            return null;
        }
    }

    @After
    public final void stopServer() {
        servletContainer.getDockerClient()
            .stopContainerCmd(servletContainer.getContainerId())
            .exec();
        servletContainer.stop();
        if (debugProxy != null) {
            debugProxy.stop();
        }
    }

    @Test
    public void testTransactionReporting() throws Exception {
        for (String pathToTest : getPathsToTest()) {
            mockServerContainer.getClient().clear(HttpRequest.request(), ClearType.LOG);

            final Response response = httpClient.newCall(new Request.Builder()
                .get()
                .url(getBaseUrl() + pathToTest)
                .build())
                .execute();
            assertThat(response.code()).withFailMessage(response.toString() + getServerLogs()).isEqualTo(200);
            final ResponseBody responseBody = response.body();
            assertThat(responseBody).isNotNull();
            assertThat(responseBody.string()).contains("Hello World");

            final List<JsonNode> reportedTransactions = assertContainsOneEntryReported(500, this::getReportedTransactions);
            assertThat(reportedTransactions.iterator().next().get("context").get("request").get("url").get("pathname").textValue())
                .isEqualTo(contextPath + pathToTest);
            // TODO make that less hacky
            if (!pathToTest.equals("/index.jsp")) {
                assertContainsOneEntryReported(500, this::getReportedSpans);
                assertThat(getServiceName()).isEqualTo(expectedDefaultServiceName);
            }
        }
    }

    @Nonnull
    private List<JsonNode> assertContainsOneEntryReported(int timeoutMs, Supplier<List<JsonNode>> supplier) throws IOException {
        long start = System.currentTimeMillis();
        List<JsonNode> reportedTransactions;
        do {
            reportedTransactions = supplier.get();
        } while (reportedTransactions.size() == 0 && System.currentTimeMillis() - start < timeoutMs);
        assertThat(reportedTransactions.size()).isEqualTo(1);
        return reportedTransactions;
    }

    @Nonnull
    private String getServerLogs() throws IOException, InterruptedException {
        final String serverLogsPath = getServerLogsPath();
        if (serverLogsPath != null) {
            return "\nlogs:\n" +
                servletContainer.execInContainer("bash", "-c", "cat " + serverLogsPath).getStdout();
        } else {
            return "";
        }
    }

    @Nullable
    protected String getServerLogsPath() {
        return null;
    }

    @NotNull
    protected List<String> getPathsToTest() {
        return Arrays.asList("/index.jsp", "/servlet", "/async-servlet");
    }

    private String getBaseUrl() {
        return "http://" + servletContainer.getContainerIpAddress() + ":" + servletContainer.getMappedPort(webPort) + contextPath;
    }

    private List<JsonNode> getReportedTransactions() {
        final List<JsonNode> transactions = getEvents("transaction");
        transactions.forEach(mockReporter::verifyTransactionSchema);
        return transactions;
    }

    private List<JsonNode> getReportedSpans() {
        final List<JsonNode> transactions = getEvents("span");
        transactions.forEach(mockReporter::verifySpanSchema);
        return transactions;
    }

    private List<JsonNode> getEvents(String eventType) {
        try {
            final List<JsonNode> transactions = new ArrayList<>();
            final ObjectMapper objectMapper = new ObjectMapper();
            for (HttpRequest httpRequest : mockServerContainer.getClient().retrieveRecordedRequests(request(INTAKE_V2_URL))) {
                for (String ndJsonLine : httpRequest.getBodyAsString().split("\n")) {
                    final JsonNode ndJson = objectMapper.readTree(ndJsonLine);
                    if (ndJson.get(eventType) != null) {
                        transactions.add(ndJson.get(eventType));
                    }
                }
            }
            return transactions;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String getServiceName() {
        try {
            final ObjectMapper objectMapper = new ObjectMapper();
            final JsonNode payload;
            payload = objectMapper
                .readTree(mockServerContainer.getClient()
                    .retrieveRecordedRequests(request(INTAKE_V2_URL))[0].getBodyAsString().split("\n")[0]);
            return payload.get("metadata").get("service").get("name").textValue();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void addSpans(List<JsonNode> spans, JsonNode payload) {
        final JsonNode jsonSpans = payload.get("spans");
        if (jsonSpans != null) {
            for (JsonNode jsonSpan : jsonSpans) {
                mockReporter.verifyTransactionSchema(jsonSpan);
                spans.add(jsonSpan);
            }
        }
    }
}
