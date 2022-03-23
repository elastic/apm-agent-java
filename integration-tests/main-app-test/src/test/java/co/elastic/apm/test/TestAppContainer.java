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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestAppContainer extends GenericContainer<TestAppContainer> {

    private static final Logger log = LoggerFactory.getLogger(TestAppContainer.class);

    // TODO : add convenient remote debug option

    private static final String JAVAAGENT_PATH = "/tmp/elastic-apm-agent.jar";
    private static final String APP_PATH = "/tmp/app.jar";
    private static final String SECURITY_POLICY = "/tmp/security.policy";


    private boolean appJar;
    private boolean javaAgent;
    private final List<String> jvmProperties;

    private String remoteDebugArg = null;

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
     * Configures remote debugging for the JVM running in the container
     *
     * @param listenPort local port where the debugger is listening to
     * @return this
     */
    public TestAppContainer withRemoteDebug(int listenPort) {
        String dockerHostName = "host.docker.internal";

        // make the docker host IP available for remote debug
        // the 'host-gateway' is automatically translated by docker for all OSes
        withExtraHost(dockerHostName, "host-gateway");

        remoteDebugArg = String.format("-agentlib:jdwp=transport=dt_socket,server=n,address=%s:%d,suspend=y", dockerHostName, listenPort);
        return this;
    }

    @Override
    public void start() {
        ArrayList<String> args = new ArrayList<>();

        args.add("java");

        if (javaAgent) {
            args.add("-javaagent:" + JAVAAGENT_PATH);
        }

        if (remoteDebugArg != null) {
            args.add(remoteDebugArg);
        }

        args.addAll(jvmProperties);

        if (appJar) {
            args.add("-jar");
            args.add(APP_PATH);
        }

        String command = String.join(" ", args);
        log.info("starting JVM with command line: {}", command);
        withCommand(command);

        super.start();

        // send container logs to logger for easier debug by default
        followOutput(new Slf4jLogConsumer(log));
    }
}
