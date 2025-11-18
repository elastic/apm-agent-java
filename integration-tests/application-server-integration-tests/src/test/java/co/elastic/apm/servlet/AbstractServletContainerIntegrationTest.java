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
package co.elastic.apm.servlet;

import co.elastic.apm.agent.MockReporter;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;
import co.elastic.apm.agent.test.AgentFileAccessor;
import co.elastic.apm.agent.test.AgentTestContainer;
import co.elastic.apm.agent.test.JavaExecutable;
import co.elastic.apm.agent.testutils.TestContainersUtils;
import co.elastic.apm.servlet.tests.TestApp;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dockerjava.api.command.StopContainerCmd;
import okhttp3.Headers;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;
import org.junit.After;
import org.junit.Test;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * When you want to execute the test in the IDE, execute {@code mvn clean package} before.
 * This creates the {@code ROOT.war} file, which is bound into the docker container.
 * <p>
 * Whenever you make changes to the application, you have to rerun {@code mvn clean package}.
 * </p>
 * <p>
 * To debug, run a 'listen' configuration on port 5005 (instead of the usual attach), then debug the integration test
 * as usual. See {@link AgentTestContainer#withRemoteDebug()} for details.
 * </p>
 */
public abstract class AbstractServletContainerIntegrationTest {
    private static final Logger logger = LoggerFactory.getLogger(AbstractServletContainerIntegrationTest.class);

    /**
     * Set to a specific version to manually test downloading of agent from maven central using the slim cli tool.
     * Only relevant for Servlet containers for which {@link #runtimeAttachSupported()} returns {@code true}.
     */
    @Nullable
    private static final String AGENT_VERSION_TO_DOWNLOAD_FROM_MAVEN = null;

    private static final WiremockServerContainer mockServerContainer;

    private static final OkHttpClient httpClient;

    static {
        mockServerContainer = MockApmServerContainer.wiremock()
            .withNetworkAliases("apm-server")
            .withNetwork(Network.SHARED);

        if (JavaExecutable.isDebugging()) {
            mockServerContainer.withLogConsumer(TestContainersUtils.createSlf4jLogConsumer(MockApmServerContainer.class));
        }

        final HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor(logger::info);
        loggingInterceptor.setLevel(JavaExecutable.isDebugging() ? HttpLoggingInterceptor.Level.BODY : HttpLoggingInterceptor.Level.BASIC);
        httpClient = new OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .readTimeout(JavaExecutable.isDebugging() ? 0 : 10, TimeUnit.SECONDS)
            .build();

        mockServerContainer.start();
    }

    private final MockReporter mockReporter = new MockReporter();
    private final int webPort;
    private final String expectedDefaultServiceName;
    private final String containerName;
    private final AgentTestContainer.AppServer container;
    private TestApp currentTestApp;

    protected AbstractServletContainerIntegrationTest(AgentTestContainer.AppServer container, String expectedDefaultServiceName) {
        this.container = container;
        this.webPort = container.getHttpPort();

        // automatic remote debug for all
        container.withRemoteDebug();

        // copy java agent binaries
        container.withJavaAgentBinaries();

        if (!runtimeAttachSupported()) {
            // use the -javaagent argument when not using runtime attach
            container.withJavaAgentArgument(AgentFileAccessor.Variant.STANDARD);
        }

        this.expectedDefaultServiceName = expectedDefaultServiceName;
        this.containerName = container.getContainerName();

        List<String> ignoreUrls = new ArrayList<>();
        for (TestApp app : getTestApps()) {
            ignoreUrls.add(String.format("/%s/status*", app.getDeploymentContext()));
            for (String ignorePath : app.getPathsToIgnore()) {
                if (ignorePath.startsWith("/")) {
                    ignorePath = ignorePath.substring(1);
                }
                ignoreUrls.add(String.format("/%s/%s", app.getDeploymentContext(), ignorePath));
            }
        }
        ignoreUrls.add("/favicon.ico");
        String ignoreUrlConfig = String.join(",", ignoreUrls);

        container
            .withNetwork(Network.SHARED)
            .withEnv("ELASTIC_APM_SERVER_URL", "http://apm-server:8080")
            .withEnv("ELASTIC_APM_IGNORE_URLS", ignoreUrlConfig)
            .withEnv("ELASTIC_APM_REPORT_SYNC", "true")
            .withEnv("ELASTIC_APM_LOG_LEVEL", "DEBUG")
            .withEnv("ELASTIC_APM_METRICS_INTERVAL", "1s")
            .withEnv("ELASTIC_APM_CAPTURE_JMX_METRICS", "object_name[java.lang:type=Memory] attribute[HeapMemoryUsage:metric_name=test_heap_metric]")
            .withEnv("ELASTIC_APM_CAPTURE_BODY", "all")
            .withEnv("ELASTIC_APM_CIRCUIT_BREAKER_ENABLED", "true")
            .withEnv("ELASTIC_APM_TRACE_METHODS", "public @@javax.enterprise.context.NormalScope co.elastic.*, public @@jakarta.enterprise.context.NormalScope co.elastic.*")
            .withEnv("ELASTIC_APM_DISABLED_INSTRUMENTATIONS", "") // enable all instrumentations for integration tests
            .withEnv("ELASTIC_APM_PROFILING_SPANS_ENABLED", "true")
            .withEnv("ELASTIC_APM_APPLICATION_PACKAGES", "co.elastic") // allows to use API annotations, we have to use a broad package due to multiple apps
            .withEnv("ELASTIC_APM_SPAN_COMPRESSION_ENABLED", "false")
            .withStartupTimeout(Duration.ofMinutes(5));

        for (TestApp testApp : getTestApps()) {
            testApp.getAdditionalEnvVariables()
                .forEach(container::withEnv);
            testApp.getAdditionalFilesToBind()
                .forEach((pathToFile, containerPath) -> container.withCopyFileToContainer(MountableFile.forHostPath(Paths.get(pathToFile)), containerPath));

            container.deploy(Paths.get(testApp.getAppFilePath()));
        }

        container.withMemoryLimit(4096);

        beforeContainerStart(container);

        container.start();

        if (runtimeAttachSupported()) {
            container.startCliRuntimeAttach(AGENT_VERSION_TO_DOWNLOAD_FROM_MAVEN);
        }
    }

    protected void beforeContainerStart(AgentTestContainer.AppServer container) {

    }

    public String getContainerName() {
        return containerName;
    }

    public String getImageName() {
        return container.getDockerImageName();
    }

    /**
     * Change to false in the Servlet-container implementation in order to attach through {@code premain()}
     *
     * @return whether the agent should be attached using the remote attach option or through `javaagent`
     */
    protected boolean runtimeAttachSupported() {
        return false;
    }

    @After
    public final void stopServer() {
        if (!container.isRunning()) {
            return; // prevent NPE on container.getContainerId()
        }
        // allow graceful shutdown before killing and removing the container
        try (StopContainerCmd stopContainerCmd = container.getDockerClient().stopContainerCmd(container.getContainerId())) {
            stopContainerCmd.exec();
        }
        container.stop();
    }

    protected Iterable<TestApp> getTestApps() {
        List<TestApp> testApps = new ArrayList<>();
        for (Class<? extends TestApp> testClass : getTestClasses()) {
            try {
                testApps.add(testClass.getConstructor().newInstance());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return testApps;
    }

    protected Iterable<Class<? extends TestApp>> getTestClasses() {
        return Collections.emptyList();
    }

    @Test
    public void testAllScenarios() throws Exception {
        for (TestApp testApp : getTestApps()) {
            this.currentTestApp = testApp;
            waitFor(testApp.getStatusEndpoint());
            clearMockServerLog();
            executeStatusRequestAndCheckIgnored(testApp.getStatusEndpoint());
            clearMockServerLog();
            testApp.test(this);
        }
    }

    public void clearMockServerLog() {
        mockServerContainer.clearRecorded();
    }

    public JsonNode assertTransactionReported(String pathToTest, int expectedResponseCode) {
        final List<JsonNode> reportedTransactions = getAllReported(this::getReportedTransactions, 1);
        JsonNode transaction = reportedTransactions.iterator().next();
        // TODO ignore leading slash for now as it's become inconsistent, needs fixing
        String pathname1 = transaction.get("context").get("request").get("url").get("pathname").textValue();
        String pathname2 = pathToTest;
        while (pathname1.startsWith("/")) {
            pathname1 = pathname1.substring(1);
        }
        while (pathname2.startsWith("/")) {
            pathname2 = pathname2.substring(1);
        }
        assertThat(pathname1).isEqualTo(pathname2);
        assertThat(transaction.get("context").get("response").get("status_code").intValue()).isEqualTo(expectedResponseCode);
        return transaction;
    }

    public void executeAndValidateRequest(String pathToTest, String expectedContent, Integer expectedResponseCode,
                                            Map<String, String> headersMap) throws IOException, InterruptedException {
        final String responseString;
        try (Response response = executeRequest(pathToTest, headersMap)) {
            if (expectedResponseCode != null) {
                assertThat(response.code())
                    .withFailMessage(response + getServerLogs())
                    .isEqualTo(expectedResponseCode);
            }
            responseString = response.body().string();
        }
        if (expectedContent != null) {
            assertThat(responseString)
                .describedAs("unexpected response content")
                .contains(expectedContent);
        }
    }

    private void executeStatusRequestAndCheckIgnored(String statusEndpoint) throws IOException {
        Map<String, String> headers = Collections.emptyMap();
        try (Response response = executeRequest(statusEndpoint, headers)) {
            assertThat(response.code()).isEqualTo(200);
        }

        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            // ignored
        }

        List<JsonNode> transactions = getReportedTransactions();
        assertThat(transactions.isEmpty())
            .describedAs("status transaction should be ignored by configuration %s", transactions)
            .isTrue();

    }

    public Response executeRequest(String pathToTest, Map<String, String> headersMap) throws IOException {
        Headers headers = Headers.of((headersMap != null) ? headersMap : new HashMap<>());
        if (!pathToTest.startsWith("/")) {
            pathToTest = "/" + pathToTest;
        }

        return httpClient.newCall(new Request.Builder()
                .get()
                .url(getBaseUrl() + pathToTest)
                .headers(headers)
                .build())
            .execute();
    }

    @Nonnull
    public List<JsonNode> getAllReported(Supplier<List<JsonNode>> supplier, int expected) {
        long timeout = JavaExecutable.isDebugging() ? 600_000 : 500;
        long start = System.currentTimeMillis();
        List<JsonNode> reportedTransactions;
        do {
            reportedTransactions = supplier.get();
        } while (reportedTransactions.size() != expected && System.currentTimeMillis() - start < timeout);
        assertThat(reportedTransactions).hasSize(expected);
        return reportedTransactions;
    }

    @Nonnull
    public List<JsonNode> assertSpansTransactionId(Supplier<List<JsonNode>> supplier, String transactionId) {
        long timeout = JavaExecutable.isDebugging() ? 600_000 : 1000;
        long start = System.currentTimeMillis();
        List<JsonNode> reportedSpans;
        do {
            reportedSpans = supplier.get();
        } while (reportedSpans.isEmpty() && System.currentTimeMillis() - start < timeout);
        assertThat(reportedSpans)
            .describedAs("at least one span is expected")
            .isNotEmpty();
        for (JsonNode span : reportedSpans) {
            assertThat(span.get("transaction_id").textValue())
                .describedAs("Unexpected transaction id for span %s", span)
                .isEqualTo(transactionId);
        }
        return reportedSpans;
    }

    @Nonnull
    public List<JsonNode> assertErrorContent(int timeoutMs, Supplier<List<JsonNode>> supplier, String transactionId, String errorMessage) {
        long start = System.currentTimeMillis();
        List<JsonNode> reportedErrors;
        do {
            reportedErrors = supplier.get();
        } while (reportedErrors.isEmpty() && System.currentTimeMillis() - start < timeoutMs);
        assertThat(reportedErrors.size()).isEqualTo(1);
        for (JsonNode error : reportedErrors) {
            assertThat(error.get("transaction_id").textValue()).isEqualTo(transactionId);
            assertThat(error.get("exception").get("message").textValue()).contains(errorMessage);
            assertThat(error.get("exception").get("stacktrace").size()).isGreaterThanOrEqualTo(1);
        }
        return reportedErrors;
    }

    @Nonnull
    private String getServerLogs() throws IOException, InterruptedException {
        String serverLogsPath = container.getLogsPath();
        if (serverLogsPath != null) {
            return "\nlogs:\n" +
                container.execInContainer("bash", "-c", "cat " + serverLogsPath + "*").getStdout();
        } else {
            return "";
        }
    }

    public List<String> getPathsToTest() {
        return Arrays.asList("/index.jsp", "/servlet", "/async-dispatch-servlet", "/async-start-servlet");
    }

    public List<String> getPathsToTestErrors() {
        return Arrays.asList("/index.jsp", "/servlet", "/async-dispatch-servlet", "/async-start-servlet");
    }

    public boolean isExpectedStacktrace(String path) {
        return !path.equals("/async-start-servlet");
    }

    public String getBaseUrl() {
        return container.getBaseUrl();
    }

    public List<JsonNode> getReportedTransactions() {
        final List<JsonNode> transactions = getEvents("transaction");
        transactions.forEach(mockReporter::verifyTransactionSchema);
        return transactions;
    }

    public List<JsonNode> getReportedSpans() {
        List<JsonNode> spans = getEvents("span").stream()
            .filter(s -> !isInferredSpan(s))
            .collect(Collectors.toList());
        spans.forEach(mockReporter::verifySpanSchema);
        return spans;
    }

    private boolean isInferredSpan(JsonNode s) {
        return Optional.ofNullable(s.get("type"))
            .map(JsonNode::textValue)
            .filter(type -> type.endsWith("inferred"))
            .isPresent();
    }

    public List<JsonNode> getReportedErrors() {
        final List<JsonNode> transactions = getEvents("error");
        transactions.forEach(mockReporter::verifyErrorSchema);
        return transactions;
    }

    public List<JsonNode> getEvents(String eventType) {
        try {
            final List<JsonNode> events = new ArrayList<>();
            final ObjectMapper objectMapper = new ObjectMapper();
            for (String bodyAsString : mockServerContainer.getRecordedRequestBodies()) {
                for (String ndJsonLine : bodyAsString.split("\n")) {
                    final JsonNode ndJson = objectMapper.readTree(ndJsonLine);
                    if (ndJson.get(eventType) != null) {
                        // as inferred spans are created only after the profiling session ends
                        // they can leak into another test
                        if (!isInferredSpan(ndJson.get(eventType))) {
                            validateEventMetadata(bodyAsString);
                        }
                        events.add(ndJson.get(eventType));
                    }
                }
            }
            return events;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void validateEventMetadata(String bodyAsString) {
        try {
            final ObjectMapper objectMapper = new ObjectMapper();
            for (String line : bodyAsString.split("\n")) {
                final JsonNode event = objectMapper.readTree(line);
                final JsonNode metadata = event.get("metadata");
                if (metadata != null) {
                    validateMetadataEvent(metadata);
                } else {
                    validateServiceName(event.get("error"));
                    validateServiceName(event.get("span"));
                    validateServiceName(event.get("transaction"));
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void validateServiceName(JsonNode event) {
        String expectedServiceName = currentTestApp.getExpectedServiceName();
        String expectedServiceVersion = currentTestApp.getExpectedServiceVersion();
        if (event == null || (expectedServiceName == null && expectedServiceVersion == null)) {
            return;
        }
        JsonNode contextService = event.get("context").get("service");
        assertThat(contextService)
            .describedAs("No service context available.")
            .isNotNull();
        if (expectedServiceName != null) {
            assertThat(contextService.get("name"))
                .describedAs("Event has missing service name %s", event)
                .isNotNull();
            assertThat(contextService.get("name").asText())
                .describedAs("Event has unexpected service name %s", event)
                .isEqualTo(expectedServiceName);
        }
        if (expectedServiceVersion != null) {
            assertThat(contextService.get("version").textValue())
                .describedAs("Event has no service version %s", event)
                .isEqualTo(expectedServiceVersion);
        }
    }

    private void validateMetadataEvent(JsonNode metadata) {
        JsonNode service = metadata.get("service");
        assertThat(service.get("name").textValue()).isEqualTo(expectedDefaultServiceName);
        JsonNode agent = service.get("agent");
        assertThat(agent).isNotNull();
        assertThat(agent.get("ephemeral_id")).isNotNull();
        JsonNode jsonContainer = metadata.get("system").get("container");
        assertThat(jsonContainer).isNotNull();
        assertThat(jsonContainer.get("id").textValue()).isEqualTo(container.getContainerId());
    }

    private void waitFor(String path) {
        Wait.forHttp(path)
            .forPort(webPort)
            .forStatusCode(200)
            .withStartupTimeout(Duration.ofMinutes(JavaExecutable.isDebugging() ? 1_000 : 5))
            .waitUntilReady(container);
    }

    public OkHttpClient getHttpClient() {
        return httpClient;
    }
}
