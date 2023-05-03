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
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import javax.annotation.Nullable;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

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
    // single-jar application path within container
    private static final String APP_JAR_PATH = "/app.jar";
    // security policy path within container
    public static final String SECURITY_POLICY_PATH = "/security.policy";

    private boolean remoteDebug = false;
    private boolean agent = false;
    private boolean appJar = false;

    private final List<String> systemProperties = new ArrayList<>();
    private final List<String> arguments = new ArrayList<>();

    private String jvmEnvironmentVariable;

    /**
     * Generic container subclass without any customization
     */
    public static class Generic extends AgentTestContainer<Generic> {

        public Generic(String dockerImageName) {
            super(dockerImageName);
        }
    }

    protected AgentTestContainer(String dockerImageName) {
        super(DockerImageName.parse(dockerImageName));
    }

    @Override
    public void start() {

        ArrayList<String> args = new ArrayList<>();
        if (hasRemoteDebug()) {
            args.add(getRemoteDebugArgument());
        }
        if (hasJavaAgent()) {
            args.add(getJavaAgentArgument());
        }
        for (String keyValue : systemProperties) {
            args.add("-D" + keyValue);
        }

        if (jvmEnvironmentVariable != null) {
            String value = String.join(" ", args);
            withEnv(jvmEnvironmentVariable, value);
            log.info("starting container with {} = {}", jvmEnvironmentVariable, value);
        }

        if (appJar) {
            // java -jar invocation

            args.add("-jar");
            args.add(APP_JAR_PATH);

            args.addAll(arguments);

            String command = "java " + String.join(" ", args);
            log.info("starting JVM with command line: {}", command);
            withCommand(command);
        }

        try {
            super.start();
        } catch (RuntimeException e) {
            log.error("unable to start container, set breakpoint where this log is generated to debug", e);
        }

        // send container logs to logger for easier debug by default
        followOutput(new Slf4jLogConsumer(log));
    }

    public SELF withJavaAgent() {
        return withJavaAgent(AgentFileAccessor.Variant.STANDARD);
    }

    public SELF withJavaAgent(AgentFileAccessor.Variant variant) {
        Path agentJar = AgentFileAccessor.getPathToJavaagent(variant);
        this.withCopyFileToContainer(MountableFile.forHostPath(agentJar), AGENT_JAR_PATH);
        agent = true;
        return self();
    }

    /**
     * Sets the jar for 'java -jar app.jar' invocation
     *
     * @param appJar path to application jar
     * @return this
     */
    public SELF withExecutableJar(Path appJar) {
        this.withCopyFileToContainer(MountableFile.forHostPath(appJar), APP_JAR_PATH);
        this.appJar = true;
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
     * Program arguments that are passed to {@code main(String[] args)} invocation, only relevant when used with {@link #withExecutableJar(Path)}
     *
     * @param arguments arguments
     * @return this
     */
    public SELF withArguments(String... arguments) {
        this.arguments.addAll(Arrays.asList(arguments));
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

    public boolean hasJavaAgent() {
        return agent;
    }

    public String getJavaAgentArgument() {
        return "-javaagent:" + AGENT_JAR_PATH;
    }

    /**
     * Enables the JVM security manager with an optional policy
     *
     * @param policyFile path to policy file, set to {@literal null} to just enable the security manager
     * @return this
     */
    public SELF withSecurityManager(@Nullable Path policyFile) {
        withSystemProperty("java.security.manager", null);
        if (policyFile != null) {
            withCopyFileToContainer(MountableFile.forHostPath(policyFile), SECURITY_POLICY_PATH);
            withSystemProperty("java.security.policy", SECURITY_POLICY_PATH);
            log.info("using security policy defined in {}", policyFile.toAbsolutePath());
            try {
                Files.readAllLines(policyFile).forEach(log::info);
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
        boolean isDebugging = false;

        // test if the test code is currently being debugged
        List<String> jvmArgs = ManagementFactory.getRuntimeMXBean().getInputArguments();
        for (String jvmArg : jvmArgs) {
            if (jvmArg.contains("-agentlib:jdwp=")) {
                isDebugging = true;
            }
        }
        if (!isDebugging) {
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
}
