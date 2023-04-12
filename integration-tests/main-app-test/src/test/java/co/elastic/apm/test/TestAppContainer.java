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
package co.elastic.apm.test;

import co.elastic.apm.agent.test.JavaExecutable;
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
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class TestAppContainer extends GenericContainer<TestAppContainer> {

    private static final Logger log = LoggerFactory.getLogger(TestAppContainer.class);

    private static final String JAVAAGENT_PATH = "/tmp/elastic-apm-agent.jar";
    private static final String APP_PATH = "/tmp/app.jar";
    private static final String SECURITY_POLICY = "/tmp/security.policy";

    private boolean appJar;
    private boolean javaAgent;
    private final List<String> jvmProperties;

    private String remoteDebugArg = null;
    private List<String> arguments = new ArrayList<>();

    TestAppContainer(String image) {
        super(DockerImageName.parse(image));
        this.jvmProperties = new ArrayList<>();
    }

    public TestAppContainer withAppJar(Path appJar) {
        assertThat(appJar).isRegularFile();
        this.withCopyFileToContainer(MountableFile.forHostPath(appJar), APP_PATH);
        this.appJar = true;
        return this;
    }

    TestAppContainer withJavaAgent(Path agentJar) {
        assertThat(agentJar).isRegularFile();
        this.withCopyFileToContainer(MountableFile.forHostPath(agentJar), JAVAAGENT_PATH);
        this.javaAgent = true;
        return this;
    }

    public TestAppContainer withSystemProperty(String key) {
        return withSystemProperty(key, null);
    }

    public TestAppContainer withSystemProperty(String key, @Nullable String value) {

        StringBuilder sb = new StringBuilder();
        sb.append("-D").append(key);
        if (value != null) {
            sb.append("=");
            sb.append(value);
        }
        jvmProperties.add(sb.toString());

        return this;
    }

    public TestAppContainer withSecurityManager() {
        return withSecurityManager(null);
    }

    public TestAppContainer withSecurityManager(@Nullable Path policyFile) {
        withSystemProperty("java.security.manager");
        if (policyFile != null) {
            withCopyFileToContainer(MountableFile.forHostPath(policyFile), SECURITY_POLICY);
            withSystemProperty("java.security.policy", SECURITY_POLICY);
            log.info("using security policy defined in {}", policyFile.toAbsolutePath());
            try {
                Files.readAllLines(policyFile).forEach(log::info);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
        return this;
    }

    /**
     * Configures remote debugging automatically for the JVM running in the container.
     * On the IDE side, all is required is to add debugger listening for incoming connections on port 5005
     *
     * @return this
     */
    public TestAppContainer withRemoteDebug() {

        int port = 5005;
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
            return this;
        }

        if (!probeDebugger(port)) {
            log.error("Unable to detect debugger listening on port {}, remote debugging JVM within container will be disabled", port);
            return this;
        }

        String dockerHostName = "host.docker.internal";

        // make the docker host IP available for remote debug
        // the 'host-gateway' is automatically translated by docker for all OSes
        withExtraHost(dockerHostName, "host-gateway");

        remoteDebugArg = debuggerArgument(port, dockerHostName);
        return this;
    }

    private static String debuggerArgument(int port, String hostname) {
        return String.format("-agentlib:jdwp=transport=dt_socket,server=n,address=%s:%d,suspend=y", hostname, port);
    }

    private boolean probeDebugger(int port) {
        // the most straightforward way to probe for an active debugger listening on port is to start another JVM
        // with the debug options and check the process exit status. Trying to probe for open network port messes with
        // the debugger and makes IDEA stop it. The only downside of this is that the debugger will first attach to this
        // probe JVM, then the one running in a docker container we are aiming to debug.
        try {
            Process process = new ProcessBuilder()
                .command(JavaExecutable.getBinaryPath().toString(), debuggerArgument(port, "localhost"), "-version")
                .start();
            process.waitFor(5, TimeUnit.SECONDS);
            return process.exitValue() == 0;
        } catch (InterruptedException | IOException e) {
            return false;
        }
    }

    @Override
    public void start() {
        ArrayList<String> args = new ArrayList<>();

        args.add("java");

        if (remoteDebugArg != null) {
            args.add(remoteDebugArg);
        }

        if (javaAgent) {
            args.add("-javaagent:" + JAVAAGENT_PATH);
        }

        args.addAll(jvmProperties);

        if (appJar) {
            args.add("-jar");
            args.add(APP_PATH);
        }

        if(!arguments.isEmpty()){
            args.addAll(arguments);
        }

        String command = String.join(" ", args);
        log.info("starting JVM with command line: {}", command);
        withCommand(command);

        try {

            super.start();
        } catch (RuntimeException e) {
            log.error("unable to start container, set breakpoint where this log is generated to debug", e);
        }

        // send container logs to logger for easier debug by default
        followOutput(new Slf4jLogConsumer(log));
    }

    public TestAppContainer withArguments(String... args) {
        this.arguments.addAll(Arrays.asList(args));
        return this;
    }
}
