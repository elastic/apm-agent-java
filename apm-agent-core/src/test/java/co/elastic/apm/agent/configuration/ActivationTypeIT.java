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
package co.elastic.apm.agent.configuration;

import co.elastic.apm.agent.test.AgentFileAccessor;
import co.elastic.apm.agent.test.JavaExecutable;
import co.elastic.apm.agent.testutils.TestPort;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public class ActivationTypeIT {

    private static String ElasticAgentAttachJarFileLocation;
    private static String ElasticAgentAttachTestJarFileLocation;
    private static String ElasticAgentAttachCliJarFileLocation;
    private static String ElasticAgentJarFileLocation;

    @BeforeAll
    public static void setUp() throws IOException {
        ElasticAgentJarFileLocation = AgentFileAccessor.getPathToJavaagent().toAbsolutePath().toString();
        ElasticAgentAttachJarFileLocation = AgentFileAccessor.getPathToAttacher().toAbsolutePath().toString();
        ElasticAgentAttachTestJarFileLocation = AgentFileAccessor.getArtifactPath(Path.of("apm-agent-attach"), "-tests", ".jar").toAbsolutePath().toString();
        ElasticAgentAttachCliJarFileLocation = AgentFileAccessor.getPathToAttacher().toAbsolutePath().toString();
    }

    public MockServer startServer() throws IOException {
        final MockServer server = new MockServer();
        server.start();
        assertThat(server.port()).isGreaterThan(0);
        return server;
    }

    @Test
    public void testSelfAttach() throws Exception {
        try (MockServer server = startServer()) {
            JvmAgentProcess proc = new JvmAgentProcess(server, "SimpleSelfAttach",
                "co.elastic.apm.attach.ExampleSelfAttachAppWithProvidedJar",
                "programmatic-self-attach");
            proc.prependToClasspath(ElasticAgentAttachJarFileLocation);
            proc.prependToClasspath(ElasticAgentAttachTestJarFileLocation);
            proc.addOption("-DElasticApmAgent.jarfile=" + ElasticAgentJarFileLocation);
            proc.executeCommand();
        }
    }

    @Test
    public void testJavaAgentAttach() throws Exception {
        try (MockServer server = startServer()) {
            JvmAgentProcess proc = new JvmAgentProcess(server, "JavaAgentCLI",
                "co.elastic.apm.agent.configuration.ActivationTestExampleApp",
                "javaagent-flag");
            proc.addOption("-javaagent:" + ElasticAgentJarFileLocation);
            proc.executeCommand();
        }
    }

    @Test
    public void testEnvAttach() throws Exception {
        try (MockServer server = startServer()) {
            JvmAgentProcess proc = new JvmAgentProcess(server, "JavaAgentCLIViaToolEnv",
                "co.elastic.apm.agent.configuration.ActivationTestExampleApp",
                "env-attach");
            proc.addEnv("JAVA_TOOL_OPTIONS", "-javaagent:" + ElasticAgentJarFileLocation);
            proc.executeCommand();
        }
    }

    @Test
    public void testRemoteAttach() throws Exception {
        try (MockServer server = startServer()) {
            JvmAgentProcess proc = new JvmAgentProcess(server, "SimpleRemoteAttached",
                "co.elastic.apm.agent.configuration.ActivationTestExampleApp",
                "apm-agent-attach-cli");
            proc.attachRemotely(true);
            proc.executeCommand();
        }
    }

    @Test
    public void testFleetAttach() throws Exception {
        try (MockServer server = startServer()) {
            JvmAgentProcess proc = new JvmAgentProcess(server, "FleetRemoteAttached",
                "co.elastic.apm.agent.configuration.ActivationTestExampleApp",
                "fleet");
            proc.attachRemotely(true);
            proc.executeCommand();
        }
    }

    @Test
    public void fakeTestLambdaAttach() throws Exception {
        try (MockServer server = startServer()) {
            JvmAgentProcess proc = new JvmAgentProcess(server, "FakeLambdaWithEnv",
                "co.elastic.apm.agent.configuration.ActivationTestExampleApp",
                "aws-lambda-layer");
            proc.addEnv("JAVA_TOOL_OPTIONS", "-javaagent:" + ElasticAgentJarFileLocation);
            proc.addEnv("ELASTIC_APM_ACTIVATION_METHOD", "AWS_LAMBDA_LAYER");
            proc.executeCommand();
        }
    }

    static class ExternalProcess {
        @Nullable
        volatile Process child;
        boolean debug = true;

        private static void pauseSeconds(int seconds) {
            try {
                Thread.sleep(seconds * 1_000L);
            } catch (InterruptedException e) {
            }
        }

        public void executeCommandInNewThread(ProcessBuilder pb, String activationMethod, MockServer mockServer, @Nullable String serviceName) throws IOException {
            ExternalProcess spawnedProcess = new ExternalProcess();
            new Thread(() -> {
                try {
                    spawnedProcess.executeCommandSynchronously(pb);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).start();
            pauseSeconds(1);
            if (serviceName != null) {
                ProcessBuilder pbAttach;
                if ("fleet".equals(activationMethod)) {
                    pbAttach = new ProcessBuilder(JavaExecutable.getBinaryPath(),
                        "-jar", ElasticAgentAttachCliJarFileLocation,
                        "--include-vmarg", serviceName,
                        "-C", "activation_method=FLEET");
                } else {
                    pbAttach = new ProcessBuilder(JavaExecutable.getBinaryPath(),
                        "-jar", ElasticAgentAttachCliJarFileLocation,
                        "--include-vmarg", serviceName);
                }
                executeCommandSynchronously(pbAttach);
            }

            ObjectMapper objectMapper = new ObjectMapper();
            await().until(() -> !mockServer.getReceivedBodyLines().isEmpty());

            List<JsonNode> jsonLines = mockServer.getReceivedBodyLines().stream()
                .map(line -> {
                        try {
                            return objectMapper.readTree(line);
                        } catch (JsonProcessingException e) {
                            throw new RuntimeException(e);
                        }
                    }
                ).collect(Collectors.toList());

            String foundServiceName = null;
            String foundActivationMethod = null;
            for (JsonNode jsonLine : jsonLines) {
                JsonNode service = jsonLine.get("service");
                if (service != null) {
                    JsonNode name = service.get("name");
                    if (name != null && name.isTextual()) {
                        foundServiceName = name.asText();
                    }
                    JsonNode agent = service.get("agent");
                    if (agent != null) {
                        JsonNode am = agent.get("activation_method");
                        if (am != null && am.isTextual()) {
                            foundActivationMethod = am.asText();
                        }
                    }
                }
            }
            if (serviceName != null) {
                assertThat(foundServiceName).isEqualTo(serviceName);
            }

            assertThat(foundActivationMethod).isEqualTo(activationMethod);

            terminate();
            spawnedProcess.terminate();
        }

        private void terminate() {
            if (child != null) {
                if (child.isAlive()) {
                    child.destroy();
                    pauseSeconds(1);
                    if (child.isAlive()) {
                        child.destroyForcibly();
                    }
                }
            }
        }

        public void executeCommandSynchronously(ProcessBuilder pb) throws IOException {
            if (debug) {
                System.out.println("Executing command: " + Arrays.toString(pb.command().toArray()));
            }
            pb.redirectErrorStream(true);
            Process childProcess = pb.start();
            child = childProcess;

            StringBuilder commandOutput = new StringBuilder();

            boolean isAlive = true;
            byte[] buffer = new byte[64 * 1000];
            try (InputStream in = childProcess.getInputStream()) {
                //stop trying if the time elapsed exceeds the timeout
                while (isAlive) {
                    while (in.available() > 0) {
                        int lengthRead = in.read(buffer, 0, buffer.length);
                        commandOutput.append(new String(buffer, 0, lengthRead));
                        if (debug) {
                            System.out.print(commandOutput);
                        }
                        commandOutput.setLength(0);
                    }
                    pauseSeconds(1);
                    //if it's not alive but there is still readable input, then continue reading
                    isAlive = childProcess.isAlive() || in.available() > 0;
                }
            }

            //Cleanup as well as I can
            boolean exited = false;
            try {
                exited = childProcess.waitFor(3, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
            }
            if (!exited) {
                childProcess.destroy();
                pauseSeconds(1);
                if (childProcess.isAlive()) {
                    childProcess.destroyForcibly();
                }
            }
            if (debug) {
                System.out.print(commandOutput);
            }
        }

    }

    static class JvmAgentProcess extends ExternalProcess {
        static final String Classpath = System.getProperty("java.class.path");
        static final String[] TestAgentParams = {"api_request_size=100b", "report_sync=true", "log_level=DEBUG", "instrument=false"};

        MockServer apmServer;
        List<String> command = new ArrayList<>();
        String serviceName;
        String targetClass;
        String activationMethod;
        Map<String, String> env = new HashMap<>();
        boolean attachRemotely;
        List<String> targetParams = new ArrayList<>();

        public void attachRemotely(boolean attachRemotely1) {
            this.attachRemotely = attachRemotely1;
        }

        public JvmAgentProcess(MockServer server, String serviceName, String targetClass, String activationMethod) {
            this.apmServer = server;
            this.serviceName = serviceName;
            this.targetClass = targetClass;
            this.activationMethod = activationMethod;
            init();
        }

        public void addEnv(String key, String value) {
            env.put(key, value);
        }

        public void prependToClasspath(String location) {
            command.set(3, location + System.getProperty("path.separator") + command.get(3));
        }

        public void executeCommand() throws IOException {
            executeCommandInNewThread(buildProcess(), activationMethod, apmServer, attachRemotely ? serviceName : null);
        }

        public void init() {
            command.clear();
            addOption(JavaExecutable.getBinaryPath());
            addOption("-Xmx32m");
            addOption("-classpath");
            addOption(Classpath);
            addAgentOption("server_url=http://localhost:" + apmServer.port());
            for (String keyEqualsValue : TestAgentParams) {
                addAgentOption(keyEqualsValue);
            }
            addAgentOption("service_name=" + serviceName);
        }

        public void addAgentOption(String keyEqualsValue) {
            command.add("-Delastic.apm." + keyEqualsValue);
        }

        public void addOption(String option) {
            command.add(option);
        }

        private ProcessBuilder buildProcess() {
            command.add(targetClass);
            for (String param : targetParams) {
                command.add(param);
            }
            ProcessBuilder pb = new ProcessBuilder(command);
            if (env != null) {
                for (Map.Entry<String, String> entry : env.entrySet()) {
                    pb.environment().put(entry.getKey(), entry.getValue());
                }
            }
            return pb;
        }

    }

    static class MockServer implements AutoCloseable {

        private static final Logger log = LoggerFactory.getLogger(MockServer.class);

        @Nullable
        private HttpServer httpServer;

        private List<String> requestBodyLines = new ArrayList<>();

        public MockServer() {
        }

        public void stop() {
            if (httpServer == null) {
                return;
            }
            httpServer.stop(0);
            httpServer = null;
        }

        public int port() {
            if (httpServer == null) {
                return -1;
            }
            return httpServer.getAddress().getPort();
        }

        public void start() throws IOException {
            if (httpServer != null) {
                throw new IllegalStateException("Ooops, you can't start this instance more than once");
            }

            int serverPort = TestPort.getAvailableRandomPort();
            httpServer = HttpServer.create(new InetSocketAddress(serverPort), 0);
            HttpContext context = httpServer.createContext("/");
            context.setHandler(httpHandler());
            httpServer.start();

            log.info("starting mock APM server on port {}", serverPort);
        }

        public List<String> getReceivedBodyLines() {
            return requestBodyLines;
        }

        @NotNull
        private HttpHandler httpHandler() {
            return exchange -> {

                String response;
                if (exchange.getRequestURI().getPath().equals("/")) { // health check
                    response = "{\"version\" : \"8.7.1\"}";
                } else {
                    InputStream requestBody = exchange.getRequestBody();
                    if (requestBody != null) {
                        try (BufferedReader reader = new BufferedReader(new InputStreamReader(requestBody))) {
                            String line = reader.readLine();
                            if (!line.isEmpty()) {
                                requestBodyLines.add(line);
                            }
                        }
                    }
                    response = "{}";
                }
                exchange.sendResponseHeaders(200, response.getBytes().length);
                exchange.getResponseBody().write(response.getBytes());
                exchange.close();
            };
        }

        @Override
        public void close() throws Exception {
            stop();
        }
    }
}
