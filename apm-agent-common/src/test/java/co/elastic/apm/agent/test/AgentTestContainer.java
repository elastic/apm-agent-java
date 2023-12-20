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
package co.elastic.apm.agent.test;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class AgentTestContainer<SELF extends GenericContainer<SELF>> extends GenericContainer<SELF> {

    private static final Logger log = LoggerFactory.getLogger(AgentTestContainer.class);

    /**
     * The port that the IDE will listen to, set to IDEA default value
     */
    private static final int DEBUG_PORT = 5005;
    private static final String LOCAL_DEBUG_HOST = "localhost";
    private static final String REMOTE_DEBUG_HOST = "remote-localhost";

    // agent path within container
    private static final String AGENT_JAR_PATH = "/agent.jar";
    private static final String AGENT_JAR_JAVA8_PATH = "/agent-java8.jar";
    // agent cli slim attacher
    private static final String AGENT_SLIM_ATTACHER_JAR = "/agent-cli-slim-attacher.jar";
    // agent cli attacher
    private static final String AGENT_ATTACHER_JAR = "/agent-cli-attacher.jar";

    // single-jar application path within container
    private static final String APP_JAR_PATH = "/app.jar";
    // security policy path within container
    public static final String SECURITY_POLICY_PATH = "/security.policy";

    private boolean remoteDebug = false;
    private AgentFileAccessor.Variant javaAgentArgumentVariant;

    private final List<String> systemProperties = new ArrayList<>();

    private String jvmEnvironmentVariable;


    public static class Generic extends AgentTestContainer<Generic> {

        private Generic(String dockerImageName) {
            super(dockerImageName);
        }

        @Override
        protected void beforeStart(ArrayList<String> args) {
        }
    }

    /**
     * Simple generic test container
     *
     * @param image image
     */
    public static Generic generic(String image) {
        return new Generic(image);
    }

    /**
     * Executable jar application test container
     *
     * @param image image
     * @return test container
     */
    public static JarApp jarApp(String image) {
        return new JarApp(image);
    }

    public static class JarApp extends AgentTestContainer<JarApp> {

        private boolean appJar;

        private final List<String> arguments = new ArrayList<>();

        protected JarApp(String image) {
            super(image);
        }

        /**
         * Sets the jar for 'java -jar app.jar' invocation
         *
         * @param appJar path to application jar
         * @return this
         */
        public JarApp withExecutableJar(Path appJar) {
            withCopyFileToContainer(MountableFile.forHostPath(appJar), APP_JAR_PATH);
            this.appJar = true;
            return self();
        }

        /**
         * Program arguments that are passed to {@code main(String[] args)} invocation
         *
         * @param arguments arguments
         * @return this
         */
        public JarApp withArguments(String... arguments) {
            this.arguments.addAll(Arrays.asList(arguments));
            return self();
        }

        @Override
        protected void beforeStart(ArrayList<String> args) {
            assertThat(appJar)
                .describedAs("missing executable jar")
                .isTrue();

            args.add("-jar");
            args.add(APP_JAR_PATH);

            args.addAll(arguments);

            String command = "java " + String.join(" ", args);
            log.info("starting JVM with command line: {}", command);
            withCommand(command);

        }
    }

    /**
     * Application server test container
     *
     * @param image image
     * @return test container
     */
    public static AppServer appServer(String image) {
        return new AppServer(image);
    }

    /**
     * Application server test container
     *
     * @param image image
     * @return test container
     */
    public static AppServer appServer(Future<String> image) {
        return new AppServer(image);
    }

    /**
     * Application server test container
     */
    public static class AppServer extends AgentTestContainer<AppServer> {

        private String deploymentPath;
        private String containerName;
        private int webPort = -1;
        private String logsPath;

        private AppServer(Future<String> image) {
            super(image);
        }

        private AppServer(String image) {
            super(image);
        }

        public AppServer withContainerName(String containerName) {
            this.containerName = containerName;
            return self();
        }

        public String getContainerName() {
            return containerName;
        }

        public AppServer withDeploymentPath(String deploymentPath) {
            this.deploymentPath = deploymentPath;
            return self();
        }

        public AppServer withHttpPort(int port) {
            this.webPort = port;
            withExposedPorts(port);
            return self();
        }

        public String getBaseUrl() {
            return String.format("http://%s:%d", getContainerIpAddress(), getMappedPort(webPort));
        }

        public int getHttpPort() {
            return webPort;
        }

        public AppServer withLogsPath(String logsPath) {
            this.logsPath = logsPath;
            return self();
        }

        public String getLogsPath() {
            return logsPath;
        }

        @Override
        protected void beforeStart(ArrayList<String> args) {
            assertThat(webPort)
                .describedAs("HTTP port must be explicitly set")
                .isGreaterThan(0);
        }

        public void deploy(Path path) {
            withCopyFileToContainer(MountableFile.forHostPath(path), deploymentPath + "/" + path.getFileName().toString());
        }
    }

    protected AgentTestContainer(Future<String> image) {
        super(image);
    }

    protected AgentTestContainer(String image) {
        super(DockerImageName.parse(image));
    }

    @Override
    public void start() {

        ArrayList<String> args = new ArrayList<>();
        if (hasRemoteDebug()) {
            args.add(getRemoteDebugArgument());
        }
        if (javaAgentArgumentVariant != null) {
            args.add(javaAgentArg(javaAgentArgumentVariant));
        }
        for (String keyValue : systemProperties) {
            args.add("-D" + keyValue);
        }

        beforeStart(args);

        if (jvmEnvironmentVariable != null) {
            String value = String.join(" ", args);
            withEnv(jvmEnvironmentVariable, value);
            log.info("starting container with {} = {}", jvmEnvironmentVariable, value);
        }

        // log app server output for easier debugging
        withLogConsumer(new Slf4jLogConsumer(log));

        try {
            super.start();
        } catch (RuntimeException e) {
            log.error("unable to start container, set breakpoint where this log is generated to debug", e);
        }


    }

    private static String javaAgentArg(AgentFileAccessor.Variant javaAgentArgumentVariant) {
        String path;
        switch (javaAgentArgumentVariant) {
            case STANDARD:
                path = AGENT_JAR_PATH;
                break;
            case JAVA8_BUILD:
                path = AGENT_JAR_JAVA8_PATH;
                break;
            default:
                throw new IllegalStateException();
        }

        return "-javaagent:" + path;
    }

    protected abstract void beforeStart(ArrayList<String> args);

    /**
     * Use the '-javaagent' JVM command line argument
     * @param variant agent variant to use
     * @return this
     */
    public SELF withJavaAgentArgument(AgentFileAccessor.Variant variant) {
        this.javaAgentArgumentVariant = variant;
        return self();
    }

    /**
     * Copy the agent binaries to container
     *
     * @return this
     */
    public SELF withJavaAgentBinaries() {
        withCopyFileToContainer(MountableFile.forHostPath(AgentFileAccessor.getPathToJavaagent(AgentFileAccessor.Variant.STANDARD)), AGENT_JAR_PATH);
        withCopyFileToContainer(MountableFile.forHostPath(AgentFileAccessor.getPathToJavaagent(AgentFileAccessor.Variant.JAVA8_BUILD)), AGENT_JAR_JAVA8_PATH);
        withCopyFileToContainer(MountableFile.forHostPath(AgentFileAccessor.getPathToSlimAttacher()), AGENT_SLIM_ATTACHER_JAR);
        withCopyFileToContainer(MountableFile.forHostPath(AgentFileAccessor.getPathToAttacher()), AGENT_ATTACHER_JAR);
        return self();
    }

    /**
     * Sets the environment variable that will be used to set the '-javaagent', JVM System properties and remote debug
     *
     * @param name environment variable name
     * @return this
     */
    public SELF withJvmArgumentsVariable(String name) {
        jvmEnvironmentVariable = name;
        return self();
    }

    /**
     * Sets a system property
     *
     * @param key   key
     * @param value value, {@literal null} indicates no value is provided.
     * @return this
     */
    public SELF withSystemProperty(String key, @Nullable String value) {
        StringBuilder sb = new StringBuilder();
        sb.append(key);
        if (value != null) {
            sb.append("=");
            sb.append(value);
        }
        systemProperties.add(sb.toString());
        return self();
    }

    /**
     * Enables the JVM security manager with an optional policy
     *
     * @param policyFile path to policy file, set to {@literal null} to just enable the security manager
     * @return this
     */
    public SELF withSecurityManager(@Nullable MountableFile policyFile) {
        withSystemProperty("java.security.manager", null);
        if (policyFile != null) {
            withCopyFileToContainer(policyFile, SECURITY_POLICY_PATH);
            withSystemProperty("java.security.policy", SECURITY_POLICY_PATH);
            withSystemProperty("java.security.debug", "failure"); // makes debugging easier

            Path policyPath = Paths.get(policyFile.getFilesystemPath());
            log.info("using security policy defined in {}", policyPath);
            try {
                Files.readAllLines(policyPath).forEach(log::info);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
        return self();
    }

    /**
     * Configures remote debugging automatically for the JVM running in the container.
     * On the IDE side, all is required is to add debugger listening for incoming connections on port 5005
     */
    public SELF withRemoteDebug() {
        if (!JavaExecutable.isDebugging()) {
            // not debugging
            return self();
        }

        if (!probeDebugger()) {
            log.error("Unable to detect debugger listening on port {}, remote debugging JVM within container will be disabled", DEBUG_PORT);
            return self();
        }

        // make the docker host IP available for remote debug
        // the 'host-gateway' is automatically translated by docker for all OSes
        withExtraHost(REMOTE_DEBUG_HOST, "host-gateway");
        remoteDebug = true;
        return self();
    }

    /**
     * @return {@literal true} if remote debug is enabled and debugger is listening
     */
    public boolean hasRemoteDebug() {
        return remoteDebug;
    }

    public String getRemoteDebugArgument() {
        return remoteDebugArgument(REMOTE_DEBUG_HOST);
    }

    private String remoteDebugArgument(String host) {
        return String.format("-agentlib:jdwp=transport=dt_socket,server=n,address=%s:%d,suspend=y", host, DEBUG_PORT);
    }

    private boolean probeDebugger() {
        // the most straightforward way to probe for an active debugger listening on port is to start another JVM
        // with the debug options and check the process exit status. Trying to probe for open network port messes with
        // the debugger and makes IDEA stop it. The only downside of this is that the debugger will first attach to this
        // probe JVM, then the one running in a docker container we are aiming to debug.
        try {
            Process process = new ProcessBuilder()
                .command(JavaExecutable.getBinaryPath().toString(), remoteDebugArgument(LOCAL_DEBUG_HOST), "-version")
                .start();
            process.waitFor(5, TimeUnit.SECONDS);
            return process.exitValue() == 0;
        } catch (InterruptedException | IOException e) {
            return false;
        }
    }

    public SELF withMemoryLimit(int limitMb) {
        return withCreateContainerCmdModifier(cmd -> Objects.requireNonNull(cmd.getHostConfig()).withMemory(limitMb * 1024 * 1024L));
    }

    public void startCliRuntimeAttach(@Nullable String version) {
        if (!isRunning()) {
            throw new IllegalStateException("can't start runtime attach before container is started");
        }

        try {
            String[] cliArgs;
            if (version != null) {
                cliArgs = new String[]{"java", "-jar", AGENT_SLIM_ATTACHER_JAR, "--download-agent-version", version, "--include-all"};
            } else {
                cliArgs = new String[]{"java", "-jar", AGENT_SLIM_ATTACHER_JAR, "--include-all", "--agent-jar", AGENT_JAR_PATH};
            }
            Container.ExecResult result = execInContainer(cliArgs);

            if (result.getExitCode() != 0) {
                System.out.println(result.getStdout());
                System.out.println(result.getStderr());
                throw new IllegalStateException("unable to attach at runtime");
            }

        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }
}
