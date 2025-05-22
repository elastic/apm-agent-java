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
package co.elastic.apm.awslambda;

import co.elastic.apm.agent.test.AgentFileAccessor;
import co.elastic.apm.agent.test.AgentTestContainer;
import co.elastic.apm.awslambda.fakeserver.FakeApmServer;
import co.elastic.apm.awslambda.fakeserver.IntakeEvent;
import co.elastic.test.TestLambda;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.dockerjava.api.command.StopContainerCmd;
import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.testcontainers.Testcontainers;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.images.builder.dockerfile.statement.RawStatement;
import org.testcontainers.images.builder.dockerfile.statement.Statement;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class AwsLambdaIT {

    private static final String LAMBDA_FUNCTION_NAME = "my-test-lambda";

    private static FakeApmServer apmServer;

    private static GenericContainer<?> lambdaContainer;

    private static final HttpClient client = HttpClient.newBuilder().build();

    @BeforeAll
    public static void init() throws IOException {
        apmServer = new FakeApmServer();
        Testcontainers.exposeHostPorts(apmServer.port());

        String image = createLambdaImage();

        lambdaContainer = AgentTestContainer.generic(image)
            .withRemoteDebug()
            .withJvmArgumentsVariable("JAVA_TOOL_OPTIONS")
            .withEnv("AWS_LAMBDA_FUNCTION_NAME", LAMBDA_FUNCTION_NAME)
            .withEnv("AWS_LAMBDA_EXEC_WRAPPER", "/opt/elastic-apm-handler")
            .withEnv("ELASTIC_APM_LAMBDA_APM_SERVER", "http://host.testcontainers.internal:" + apmServer.port())
            .withEnv("ELASTIC_APM_SEND_STRATEGY", "syncflush")
            .withEnv("ELASTIC_APM_LOG_LEVEL", "DEBUG")
            .withExposedPorts(8080)
            .waitingFor(Wait.forListeningPort());
    }

    @AfterAll
    public static void tearDown() {
        apmServer.close();
        if (lambdaContainer != null && lambdaContainer.isRunning()) {
            lambdaContainer.stop();
        }
    }

    @BeforeEach
    public void startContainer() {
        apmServer.reset();
        if (!lambdaContainer.isRunning()) {
            lambdaContainer.start();
        }
    }

    @Test
    @Order(1)
    public void checkSuccessfulInvocation() {

        invokeLambda("sleep 100");
        flushLambdaData();

        await().atMost(Duration.ofSeconds(10)).until(() -> apmServer.getIntakeEvents().stream()
            .anyMatch(IntakeEvent::isTransaction));
        assertThat(apmServer.getIntakeEvents()).anySatisfy(ev -> {
            assertThat(ev.isTransaction()).isTrue();

            ObjectNode metadata = ev.getMetadata();
            assertThatJson(metadata).inPath("$.service.name").isEqualTo(LAMBDA_FUNCTION_NAME);
            assertThatJson(metadata).inPath("$.service.framework.name").isEqualTo("AWS Lambda");

            ObjectNode transaction = ev.getContent();
            assertThatJson(transaction).inPath("$.faas.name").isEqualTo(LAMBDA_FUNCTION_NAME);
            assertThatJson(transaction).inPath("$.context.custom.command").isEqualTo("sleep 100");
            assertThatJson(transaction).inPath("$.duration").isNumber().isGreaterThan(BigDecimal.valueOf(90.0));
            assertThatJson(transaction).inPath("$.outcome").isEqualTo("success");
        });
    }


    @Test
    @Order(2) //use ordering so that the last test crashes the container (=better performance due to no restart required)
    public void checkLambdaCrash() {

        invokeLambda("die");
        //graceful shutdown to trigger flush from the extension
        try (StopContainerCmd stopContainerCmd = lambdaContainer.getDockerClient().stopContainerCmd(lambdaContainer.getContainerId())) {
            stopContainerCmd.exec();
        }
        lambdaContainer.stop(); //required to mark the container as stopped for testcontainers

        await().atMost(Duration.ofSeconds(10)).until(() -> apmServer.getIntakeEvents().stream()
            .anyMatch(IntakeEvent::isTransaction));
        assertThat(apmServer.getIntakeEvents()).anySatisfy(ev -> {
            assertThat(ev.isTransaction()).isTrue();

            ObjectNode metadata = ev.getMetadata();
            assertThatJson(metadata).inPath("$.service.name").isEqualTo(LAMBDA_FUNCTION_NAME);
            assertThatJson(metadata).inPath("$.service.framework.name").isEqualTo("AWS Lambda");

            ObjectNode transaction = ev.getContent();
            assertThatJson(transaction).inPath("$.faas.name").isEqualTo(LAMBDA_FUNCTION_NAME);
            assertThatJson(transaction).inPath("$.outcome").isEqualTo("failure");
        });
    }

    /**
     * Workaround for https://github.com/elastic/apm-aws-lambda/issues/386.
     * We should be able to remove flush lambda invocations after this issue is fixed.
     */
    private void flushLambdaData() {
        invokeLambda("flush");
    }

    private void invokeLambda(String cmd) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + lambdaContainer.getFirstMappedPort() + "/2015-03-31/functions/function/invocations"))
                .POST(HttpRequest.BodyPublishers.ofString("\"" + cmd + "\""))
                .build();

            client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    private static String createLambdaImage() throws IOException {
        Map<String, Transferable> agentLayerFiles = getAgentLambdaLayerFiles();

        ImageFromDockerfile imageBuilder = new ImageFromDockerfile().withDockerfileFromBuilder(builder ->
                {
                    builder
                        .withStatement(raw("FROM docker.elastic.co/observability/apm-lambda-extension-x86_64:1.5.5 AS lambda-extension"))
                        .withStatement(raw("FROM --platform=linux/amd64 public.ecr.aws/lambda/java:17"))
                        .withStatement(raw("COPY --from=lambda-extension /opt/elastic-apm-extension /opt/extensions/elastic-apm-extension"))
                        .copy("aws-lambda-test.jar", "${LAMBDA_TASK_ROOT}/lib/aws-lambda-test.jar")
                        .cmd("[\"" + TestLambda.class.getName() + "\"]");

                    for (String fileName : agentLayerFiles.keySet()) {
                        builder.copy(fileName, "/opt/" + fileName);
                    }
                    builder.run("chmod +x /opt/elastic-apm-handler");
                }
            )
            .withFileFromPath("aws-lambda-test.jar", Path.of("target/aws-lambda-test.jar"));

        for (String fileName : agentLayerFiles.keySet()) {
            imageBuilder.withFileFromTransferable(fileName, agentLayerFiles.get(fileName));
        }

        return imageBuilder.get();
    }

    @NotNull
    private static Map<String, Transferable> getAgentLambdaLayerFiles() throws IOException {
        Path lambdaZip = AgentFileAccessor.getPathToAwsLambdaLayer();

        Map<String, Transferable> agentLayerFiles = new HashMap<>();

        try (ZipFile file = new ZipFile(new File(lambdaZip.toString()))) {
            Enumeration<? extends ZipEntry> entries = file.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (!entry.isDirectory()) {
                    try (InputStream fileContent = file.getInputStream(entry)) {
                        agentLayerFiles.put(entry.getName(), Transferable.of(IOUtils.toByteArray(fileContent)));
                    }
                }
            }
        }
        return agentLayerFiles;
    }

    private static Statement raw(String command) {
        return new RawStatement("", command);
    }

}
