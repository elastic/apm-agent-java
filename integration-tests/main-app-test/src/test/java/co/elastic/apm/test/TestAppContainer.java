/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package co.elastic.apm.test;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import javax.annotation.Nullable;
import java.nio.file.Path;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestAppContainer extends GenericContainer<TestAppContainer> {

    // TODO : add convenient remote debug option

    private static final String JAVAAGENT_PATH = "/tmp/elastic-apm-agent.jar";
    private static final String APP_PATH = "/tmp/app.jar";
    private static final String SECURITY_POLICY = "/tmp/security.policy";

    private boolean appJar;
    private boolean javaAgent;
    private final List<String> jvmProperties;

    TestAppContainer(String image) {
        super(DockerImageName.parse(image));
        this.jvmProperties = new ArrayList<>();

        // try to fail fast
        withStartupAttempts(1);
        withStartupTimeout(Duration.of(5, ChronoUnit.SECONDS));
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
        if(policyFile != null) {
            withCopyFileToContainer(MountableFile.forHostPath(policyFile), SECURITY_POLICY);
            withSystemProperty("java.security.policy", SECURITY_POLICY);
        }
        return this;
    }

    @Override
    public void start() {
        ArrayList<String> args = new ArrayList<>();

        args.add("java");

        if (javaAgent) {
            args.add("-javaagent:" + JAVAAGENT_PATH);
        }

        args.addAll(jvmProperties);

        if (appJar) {
            args.add("-jar");
            args.add(APP_PATH);
        }

        withCommand(String.join(" ", args));
        super.start();
    }
}
