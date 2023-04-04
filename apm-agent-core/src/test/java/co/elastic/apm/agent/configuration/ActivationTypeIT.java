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
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.lang.ref.Cleaner;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Execution(ExecutionMode.CONCURRENT)
public class ActivationTypeIT {
    // Activation is about how an external config or process activates the agent,
    // so tests here spawn a full JVM with an agent to test the activation method
    private static final int TIMEOUT_IN_SECONDS = 200;

    private static String ElasticAgentAttachJarFileLocation;
    private static String ElasticAgentAttachTestJarFileLocation;
    private static String ElasticAgentAttachCliJarFileLocation;
    private static String ElasticAgentJarFileLocation;
    private static final Cleaner MockCleaner = Cleaner.create();

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
        assertThat(server.waitUntilStarted(500)).isTrue();
        assertThat(server.port()).isGreaterThan(0);
        MockCleaner.register(server, () -> server.stop());
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
    public void testCLIAttach() throws Exception {
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
        volatile Process child;
        boolean debug = true;

        private static void pauseSeconds(int seconds) {
            try {Thread.sleep(seconds*1_000L);} catch (InterruptedException e) {}
        }

        public void executeCommandInNewThread(ProcessBuilder pb, ActivationHandler handler, String activationMethod, String serviceName) throws IOException, InterruptedException {
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
                    pbAttach = new ProcessBuilder(JavaExecutable.getBinaryPath().toString(),
                        "-jar", ElasticAgentAttachCliJarFileLocation,
                        "--include-vmarg", serviceName,
                        "-C", "activation_method=FLEET");
                } else {
                    pbAttach = new ProcessBuilder(JavaExecutable.getBinaryPath().toString(),
                        "-jar", ElasticAgentAttachCliJarFileLocation,
                        "--include-vmarg", serviceName);
                }
                executeCommandSynchronously(pbAttach);
            }
            waitForActivationMethod(handler, TIMEOUT_IN_SECONDS*1000);
            assertThat(handler.found()).isTrue();
            terminate();
            spawnedProcess.terminate();
        }

        private static void waitForActivationMethod(ActivationHandler handler, long timeoutInMillis) {
            long start = System.currentTimeMillis();
            while (System.currentTimeMillis() - start < timeoutInMillis) {
                if (handler.found()) {
                    return;
                }
                try {Thread.sleep(5);} catch (InterruptedException e) {}
            }
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
                System.out.println("Executing command: "+ Arrays.toString(pb.command().toArray()));
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
            try {exited = childProcess.waitFor(3, TimeUnit.SECONDS);}catch (InterruptedException e) {}
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
        Map<String,String> env;
        boolean attachRemotely;
        List<String> targetParams = new ArrayList<>();

        public void attachRemotely(boolean attachRemotely1) {
            this.attachRemotely = attachRemotely1;
        }

        public JvmAgentProcess(MockServer server, String serviceName1, String targetClass1, String activationMethod1) {
            apmServer = server;
            serviceName = serviceName1;
            targetClass = targetClass1;
            activationMethod = activationMethod1;
            init();
        }

        public void addEnv(String key, String value) {
            if(env == null) {
                env = new HashMap<>();
            }
            env.put(key, value);
        }

        public void prependToClasspath(String location) {
            command.set(3, location+System.getProperty("path.separator")+command.get(3));
        }

        public void executeCommand() throws IOException, InterruptedException {
            executeCommandInNewThread(buildProcess(), apmServer.getHandler(),
                activationMethod, attachRemotely? serviceName : null);
        }

        public void init() {
            command.clear();
            addOption(JavaExecutable.getBinaryPath());
            addOption("-Xmx32m");
            addOption("-classpath");
            addOption(Classpath);
            addAgentOption("server_url=http://localhost:"+apmServer.port());
            for (String keyEqualsValue : TestAgentParams) {
                addAgentOption(keyEqualsValue);
            }
            addAgentOption("service_name="+serviceName);
            apmServer.getHandler().setActivationToWaitFor(serviceName, activationMethod);
        }

        public void addAgentOption(String keyEqualsValue) {
            command.add("-Delastic.apm."+keyEqualsValue);
        }

        public void addOption(String option) {
            command.add(option);
        }

        public void addTargetParam(String param) {
            targetParams.add(param);
        }

        private ProcessBuilder buildProcess() {
            command.add(targetClass);
            for (String param :targetParams) {
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

    static class ActivationHandler {

        private volatile String serviceNameToWaitFor;
        private volatile String activationMethodToWaitFor;
        private volatile boolean found;
        private final ObjectMapper objectMapper = new ObjectMapper();

        public boolean found() {
            return found;
        }

        public void setActivationToWaitFor(String serviceNameToWaitFor1, String activationMethodToWaitFor1) {
            this.serviceNameToWaitFor = serviceNameToWaitFor1;
            this.activationMethodToWaitFor = activationMethodToWaitFor1;
            found = false;
        }

        public void handle(String line) {
            try {
                report(line);
            } catch (JsonProcessingException e) {
                e.printStackTrace();
            }
        }

        private void report(String line) throws JsonProcessingException {
            System.out.println("MockServer line read: "+line);
            JsonNode messageRootNode = objectMapper.readTree(line);
            JsonNode metadataNode = messageRootNode.get("metadata");
            if (metadataNode != null) {
                JsonNode serviceNode = metadataNode.get("service");
                if (serviceNode != null) {
                    String name = serviceNode.get("name").asText();
                    JsonNode agentNode = serviceNode.get("agent");
                    if (agentNode != null) {
                        JsonNode activationNode = agentNode.get("activation_method");
                        if(activationNode != null) {
                            String activationMethod = activationNode.asText();
                            if (name.equals(serviceNameToWaitFor) && activationMethod.equals(activationMethodToWaitFor)) {
                                found = true;
                            }
                        }
                    }
                }
            }
        }

    }

    class MockServer implements AutoCloseable {

        private static final String HTTP_HEADER ="HTTP/1.0 200 OK\nContent-Type: text/html; charset=utf-8\nServer: MockApmServer\n\n";

        private volatile ServerSocket server;
        private volatile boolean keepGoing = true;
        private final ActivationHandler handler = new ActivationHandler();


        public MockServer() {
        }

        public ActivationHandler getHandler() {
            return handler;
        }

        public void stop() {
            keepGoing = false;
            try {
                if (this.server != null) {
                    this.server.close();
                }
            } catch (IOException e) {
                System.out.println("MockApmServer: Unsuccessfully called stop(), stack trace follows, error is:"+e.getLocalizedMessage());
                e.printStackTrace(System.out);
            }
        }

        public int port() {
            if (this.server != null) {
                return this.server.getLocalPort();
            } else {
                return -1;
            }
        }

        public boolean waitUntilStarted(long timeoutInMillis) {
            long start = System.currentTimeMillis();
            while((System.currentTimeMillis() - start < timeoutInMillis) && server == null) {
                try {Thread.sleep(1);} catch (InterruptedException e) {}
            }
            return server != null;
        }

        public void start() throws IOException {
            if (this.server != null) {
                throw new IOException("MockApmServer: Ooops, you can't start this instance more than once");
            }
            new Thread(() -> {
                try {
                    _start();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).start();
        }

        private synchronized void _start() throws IOException {
            if (this.server != null) {
                throw new IOException("MockApmServer: Ooops, you can't start this instance more than once");
            }
            this.server = new ServerSocket(0);
            System.out.println("MockApmServer: Successfully called start(), now listening for requests on port "+this.server.getLocalPort());
            while(keepGoing) {
                try(Socket client = this.server.accept()) {
                    while(!client.isClosed() && !client.isInputShutdown() && !client.isOutputShutdown()) {
                        try (BufferedReader clientInput = new BufferedReader(new InputStreamReader(client.getInputStream()))) {
                            String line = clientInput.readLine();
                            if(line == null) {
                                //hmmm, try again
                                try {Thread.sleep(10);} catch (InterruptedException e) {}
                                line = clientInput.readLine();
                                if (line == null) {
                                    clientInput.close();
                                    break;
                                }
                            }
                            if (line.startsWith("GET /exit")) {
                                keepGoing = false;
                            }
                            while ( (line = clientInput.readLine()) != null) {
                                if (line.strip().startsWith("{")) {
                                    try {
                                        handler.handle(line.strip());
                                    } catch (Throwable e) {
                                        //ignore, the report() is responsible to have log it
                                    }
                                }
                            }
                            PrintWriter outputToClient = new PrintWriter(client.getOutputStream());
                            outputToClient.println(HTTP_HEADER);
                            outputToClient.println("{}");
                            outputToClient.flush();
                            outputToClient.close();
                        } catch (IOException e) {
                            if (!e.getMessage().equals("Connection reset")) {
                                e.printStackTrace();
                            }
                        }
                    }
                } catch (SocketException e) {
                    //ignore, we exit regardless and stop() at the end of the method
                }
            }
            stop();
        }

        @Override
        public void close() throws Exception {
            stop();
        }
    }
}
