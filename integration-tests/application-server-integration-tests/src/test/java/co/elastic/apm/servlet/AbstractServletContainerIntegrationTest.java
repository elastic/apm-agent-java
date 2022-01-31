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
import co.elastic.apm.agent.testutils.TestContainersUtils;
import co.elastic.apm.servlet.tests.TestApp;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.Headers;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.logging.HttpLoggingInterceptor;
import org.junit.After;
import org.junit.Test;
import org.mockserver.model.ClearType;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.SocatContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
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

import static co.elastic.apm.agent.report.IntakeV2ReportingEventHandler.INTAKE_V2_URL;
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
 * To debug, add a remote debugging configuration for port 5005 and set {@link #ENABLE_DEBUGGING} to {@code true}.
 * </p>
 * <p>
 * To test slim CLI tool with any agent version, set {@link #AGENT_VERSION_TO_DOWNLOAD_FROM_MAVEN} to the desired version.
 * </p>
 * <p>
 * Servlet containers that support runtime attach are being tested with it by default. In order to test those through
 * the `javaagent` route, set {@link #ENABLE_RUNTIME_ATTACH} to {@code false}
 * </p>
 */
public abstract class AbstractServletContainerIntegrationTest {
    private static final Logger logger = LoggerFactory.getLogger(AbstractServletContainerIntegrationTest.class);

    private static final String pathToJavaagent;
    private static final String pathToAttach;
    private static final String pathToSlimAttach;

    static boolean ENABLE_DEBUGGING = false;
    static boolean ENABLE_RUNTIME_ATTACH = true;

    /**
     * Set to a specific version to manually test downloading of agent from maven central using the slim cli tool.
     * Only relevant if {@link #ENABLE_RUNTIME_ATTACH} is set to {@code true} and for Servlet containers for which
     * {@link #runtimeAttachSupported()} returns {@code true}.
     */
    @Nullable
    private static final String AGENT_VERSION_TO_DOWNLOAD_FROM_MAVEN = null;

    private static MockServerContainer mockServerContainer = new MockServerContainer()
        //.withLogConsumer(TestContainersUtils.createSlf4jLogConsumer(MockServerContainer.class))
        .withNetworkAliases("apm-server")
        .withNetwork(Network.SHARED);
    private static OkHttpClient httpClient;

    static {
        mockServerContainer.start();
        mockServerContainer.getClient().when(request(INTAKE_V2_URL)).respond(HttpResponse.response().withStatusCode(200));
        mockServerContainer.getClient().when(request("/")).respond(HttpResponse.response().withStatusCode(200));
        final HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor(logger::info);
        loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY);
        httpClient = new OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .readTimeout(ENABLE_DEBUGGING ? 0 : 10, TimeUnit.SECONDS)
            .build();
        pathToJavaagent = AgentFileAccessor.getPathToJavaagent();
        pathToAttach = AgentFileAccessor.getPathToAttacher();
        pathToSlimAttach = AgentFileAccessor.getPathToSlimAttacher();
        checkFilePresent(pathToJavaagent);
        checkFilePresent(pathToAttach);
        checkFilePresent(pathToSlimAttach);
    }

    private final MockReporter mockReporter = new MockReporter();
    private final GenericContainer<?> servletContainer;
    private final int webPort;
    private final String expectedDefaultServiceName;
    private final String containerName;
    @Nullable
    private GenericContainer<?> debugProxy;
    private TestApp currentTestApp;

    protected AbstractServletContainerIntegrationTest(GenericContainer<?> servletContainer, String expectedDefaultServiceName, String deploymentPath, String containerName) {
        this(servletContainer, 8080, 5005, expectedDefaultServiceName, deploymentPath, containerName);
    }

    protected AbstractServletContainerIntegrationTest(GenericContainer<?> servletContainer, int webPort,
                                                      int debugPort, String expectedDefaultServiceName, String deploymentPath, String containerName) {
        this.servletContainer = servletContainer;
        this.webPort = webPort;
        if (ENABLE_DEBUGGING) {
            enableDebugging(servletContainer);
            this.debugProxy = createDebugProxy(servletContainer, debugPort);
        }
        if (runtimeAttachSupported() && !ENABLE_RUNTIME_ATTACH) {
            // If runtime attach is off for Servlet containers that support that, we need to add the javaagent here
            appendToEnvVariable(servletContainer, getJavaagentEnvVariable(), "-javaagent:/elastic-apm-agent.jar");
        }
        this.expectedDefaultServiceName = expectedDefaultServiceName;
        this.containerName = containerName;

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

        servletContainer
            .withNetwork(Network.SHARED)
            .withEnv("ELASTIC_APM_SERVER_URL", "http://apm-server:1080")
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
            .withLogConsumer(new StandardOutLogConsumer().withPrefix(containerName))
            .withExposedPorts(webPort)
            .withFileSystemBind(pathToJavaagent, "/elastic-apm-agent.jar")
            .withFileSystemBind(pathToAttach, "/apm-agent-attach-cli.jar")
            .withFileSystemBind(pathToSlimAttach, "/apm-agent-attach-cli-slim.jar")
            .withStartupTimeout(Duration.ofMinutes(5));
        for (TestApp testApp : getTestApps()) {
            testApp.getAdditionalEnvVariables().forEach(servletContainer::withEnv);
            try {
                testApp.getAdditionalFilesToBind().forEach((pathToFile, containerPath) -> {
                    checkFilePresent(pathToFile);
                    servletContainer.withFileSystemBind(pathToFile, containerPath);
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        if (isDeployViaFileSystemBind()) {
            for (TestApp testApp : getTestApps()) {
                String pathToAppFile = testApp.getAppFilePath();
                checkFilePresent(pathToAppFile);
                servletContainer.withFileSystemBind(pathToAppFile, deploymentPath + "/" + testApp.getAppFileName());
            }
        }
        this.servletContainer.withCreateContainerCmdModifier(TestContainersUtils.withMemoryLimit(4096));
        this.servletContainer.start();
        if (runtimeAttachSupported() && ENABLE_RUNTIME_ATTACH) {
            try {
                String[] cliArgs;
                if (AGENT_VERSION_TO_DOWNLOAD_FROM_MAVEN != null) {
                    cliArgs = new String[]{"java", "-jar", "/apm-agent-attach-cli-slim.jar", "--download-agent-version", AGENT_VERSION_TO_DOWNLOAD_FROM_MAVEN, "--include-all"};
                } else {
                    cliArgs = new String[]{"java", "-jar", "/apm-agent-attach-cli-slim.jar", "--include-all", "--agent-jar", "/elastic-apm-agent.jar"};
                }
                Container.ExecResult result = this.servletContainer.execInContainer(cliArgs);
                System.out.println(result.getStdout());
                System.out.println(result.getStderr());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        if (!isDeployViaFileSystemBind()) {
            for (TestApp testApp : getTestApps()) {
                String pathToAppFile = testApp.getAppFilePath();
                checkFilePresent(pathToAppFile);
                servletContainer.copyFileToContainer(MountableFile.forHostPath(pathToAppFile), deploymentPath + "/" + testApp.getAppFileName());
            }
        }
    }

    private void appendToEnvVariable(GenericContainer<?> servletContainer, String key, String value) {
        Map<String, String> envMap = servletContainer.getEnvMap();
        String currentValue = envMap.get(key);
        if (currentValue == null) {
            servletContainer.withEnv(key, value);
        } else {
            servletContainer.withEnv(key, currentValue + " " + value);
        }
    }

    public String getContainerName() {
        return containerName;
    }

    public String getImageName() {
        return servletContainer.getDockerImageName();
    }

    /**
     * If set to true, the war files are {@code --mount}ed into the container instead of copied, which is faster.
     */
    protected boolean isDeployViaFileSystemBind() {
        return true;
    }

    /**
     * Change to false in the Servlet-container implementation in order to attach through {@code premain()}
     * See also {@link AbstractServletContainerIntegrationTest#getJavaagentEnvVariable()}
     *
     * @return whether the agent should be attached using the remote attach option or through `javaagent`
     */
    protected boolean runtimeAttachSupported() {
        return false;
    }

    /**
     * Override with the name of the environment variable to which the `javaagent` argument should be appended.
     * See also {@link AbstractServletContainerIntegrationTest#runtimeAttachSupported()}
     *
     * @return the name of the environment variable to which the `javaagent` argument should be appended
     */
    protected String getJavaagentEnvVariable() {
        throw new UnsupportedOperationException("You must provide the proper config that will append the `javaagent` " +
            "argument properly to the command line");
    }

    private static void checkFilePresent(String pathToFile) {
        final File file = new File(pathToFile);
        logger.info("Check file {}", file.getAbsolutePath());
        assertThat(file).exists();
        assertThat(file).isFile();
        assertThat(file.length()).isGreaterThan(0);
    }

    protected void enableDebugging(GenericContainer<?> servletContainer) {
    }

    // makes sure the debugging port is always 5005
    // if the port is not available, the test can still run
    @Nullable
    private GenericContainer<?> createDebugProxy(GenericContainer<?> servletContainer, final int debugPort) {
        try {
            final SocatContainer socatContainer = new SocatContainer() {{
                addFixedExposedPort(debugPort, debugPort);
            }}
                .withNetwork(Network.SHARED)
                .withTarget(debugPort, servletContainer.getNetworkAliases().get(0));
            socatContainer.start();
            return socatContainer;
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

    /**
     * NOTE: This test class should contain a single test method, otherwise multiple instances may coexist and cause port clash due to the
     * debug proxy
     */
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
        mockServerContainer.getClient().clear(HttpRequest.request(), ClearType.LOG);
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

    public String executeAndValidateRequest(String pathToTest, String expectedContent, Integer expectedResponseCode,
                                            Map<String, String> headersMap) throws IOException, InterruptedException {
        Response response = executeRequest(pathToTest, headersMap);
        if (expectedResponseCode != null) {
            assertThat(response.code())
                .withFailMessage(response.toString() + getServerLogs())
                .isEqualTo(expectedResponseCode);
        }
        final ResponseBody responseBody = response.body();
        assertThat(responseBody).isNotNull();
        String responseString = responseBody.string();
        if (expectedContent != null) {
            assertThat(responseString)
                .describedAs("unexpected response content")
                .contains(expectedContent);
        }
        return responseString;
    }

    private void executeStatusRequestAndCheckIgnored(String statusEndpoint) throws IOException {
        Map<String, String> headers = Collections.emptyMap();
        Response response = executeRequest(statusEndpoint, headers);
        assertThat(response.code()).isEqualTo(200);

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

    public String executeAndValidatePostRequest(String pathToTest, RequestBody postBody, String expectedContent, Integer expectedResponseCode) throws IOException, InterruptedException {
        Response response = executePostRequest(pathToTest, postBody);
        if (expectedResponseCode != null) {
            assertThat(response.code())
                .withFailMessage(response.toString() + getServerLogs())
                .isEqualTo(expectedResponseCode);
        }
        final ResponseBody responseBody = response.body();
        assertThat(responseBody).isNotNull();
        String responseString = responseBody.string();
        if (expectedContent != null) {
            assertThat(responseString).contains(expectedContent);
        }
        return responseString;
    }

    public Response executePostRequest(String pathToTest, RequestBody postBody) throws IOException {
        if (!pathToTest.startsWith("/")) {
            pathToTest = "/"+pathToTest;
        }
        return httpClient.newCall(new Request.Builder()
                .post(postBody)
                .url(getBaseUrl() + pathToTest)
                .build())
            .execute();
    }

    public Response executeRequest(String pathToTest, Map<String, String> headersMap) throws IOException {
        Headers headers = Headers.of((headersMap != null) ? headersMap : new HashMap<>());
        if (!pathToTest.startsWith("/")) {
            pathToTest = "/"+pathToTest;
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
        long timeout = ENABLE_DEBUGGING ? 600_000 : 500;
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
        long timeout = ENABLE_DEBUGGING ? 600_000 : 500;
        long start = System.currentTimeMillis();
        List<JsonNode> reportedSpans;
        do {
            reportedSpans = supplier.get();
        } while (reportedSpans.size() == 0 && System.currentTimeMillis() - start < timeout);
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
        } while (reportedErrors.size() == 0 && System.currentTimeMillis() - start < timeoutMs);
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
        return "http://" + servletContainer.getContainerIpAddress() + ":" + servletContainer.getMappedPort(webPort);
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
            for (HttpRequest httpRequest : mockServerContainer.getClient().retrieveRecordedRequests(request(INTAKE_V2_URL))) {
                final String bodyAsString = httpRequest.getBodyAsString();
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
        if (expectedServiceName != null && event != null) {
            JsonNode contextService = event.get("context").get("service");
            assertThat(contextService)
                .withFailMessage("No service name set. Expected '%s'. Event was %s", expectedServiceName, event)
                .isNotNull();
            assertThat(contextService.get("name").textValue())
                .describedAs("Event has unexpected service name %s", event)
                .isEqualTo(expectedServiceName);
        }
    }

    private void validateMetadataEvent(JsonNode metadata) {
        JsonNode service = metadata.get("service");
        assertThat(service.get("name").textValue()).isEqualTo(expectedDefaultServiceName);
        JsonNode agent = service.get("agent");
        assertThat(agent).isNotNull();
        assertThat(agent.get("ephemeral_id")).isNotNull();
        JsonNode container = metadata.get("system").get("container");
        assertThat(container).isNotNull();
        assertThat(container.get("id").textValue()).isEqualTo(servletContainer.getContainerId());
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

    private void waitFor(String path) {
        Wait.forHttp(path)
            .forPort(webPort)
            .forStatusCode(200)
            .withStartupTimeout(Duration.ofMinutes(ENABLE_DEBUGGING ? 1_000 : 5))
            .waitUntilReady(servletContainer);
    }

    public OkHttpClient getHttpClient() {
        return httpClient;
    }

    public boolean isHotSpotBased() {
        return true;
    }
}
