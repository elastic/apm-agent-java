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

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.io.File;
import java.io.FileFilter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

public class ExternalPluginOTelIT {

    private static final String DOCKER_IMAGE = "openjdk:11";

    @Test
    void runAppWithTwoExternalPlugins() {

        GenericContainer<?> app = new GenericContainer<>(DockerImageName.parse(DOCKER_IMAGE))
            .withCopyFileToContainer(MountableFile.forHostPath(getAgentJar()), "/tmp/agent.jar")
            .withCopyFileToContainer(MountableFile.forHostPath("target/external-plugin-otel-test-app.jar"), "/tmp/app.jar")
            .withCopyFileToContainer(MountableFile.forHostPath("../external-plugin-otel-test-plugin1/target/external-plugin-otel-test-plugin1.jar"), "/tmp/plugins/plugin1.jar")
            .withCopyFileToContainer(MountableFile.forHostPath("../external-plugin-otel-test-plugin2/target/external-plugin-otel-test-plugin2.jar"), "/tmp/plugins/plugin2.jar")
            .withCommand(String.format("java -javaagent:/tmp/agent.jar %s -jar /tmp/app.jar --wait", getAgentArgs()))
            .waitingFor(Wait.forLogMessage(".*app end.*", 1));

        try {
            app.start();

            List<String> logLines = Arrays.asList(app.getLogs().split("\n"));
            assertThat(logLines).isNotEmpty();

            assertThat(logLines).containsExactly(
                "app start",
                ">> transaction enter", // added by plugin1
                "start transaction",
                ">> span enter", // added by plugin2
                "start span",
                "end span",
                "<< span exit", // added by plugin2
                "end transaction",
                "<< transaction exit", // added by plugin1
                "app end");

        } finally {
            app.stop();
        }
    }

    private static String getAgentArgs() {
        Map<String, String> agentConfig = new HashMap<>();

        // write agent log outside of standard output
        agentConfig.put("log_file", "/tmp/agent.log");

        // don't try to reach to external server as there is none
        agentConfig.put("disable_send", "true");
        agentConfig.put("central_config", "false");


        agentConfig.put("plugins_dir", "/tmp/plugins");

        return agentConfig.entrySet().stream()
            .map(e -> String.format("-Delastic.apm.%s=%s", e.getKey(), e.getValue()))
            .collect(Collectors.joining(" "));
    }

    private static String getAgentJar() {
        // TODO : remove this duplication of getAgentJar method
        File buildDir = new File("../../../elastic-apm-agent/target/");
        FileFilter fileFilter = file -> file.getName().matches("elastic-apm-agent-\\d\\.\\d+\\.\\d+(\\.RC\\d+)?(-SNAPSHOT)?.jar");
        return Arrays.stream(buildDir.listFiles(fileFilter))
            .findFirst()
            .map(File::getAbsolutePath)
            .orElseThrow(() -> new IllegalStateException("Agent jar not found. Execute mvn package to build the agent jar."));
    }
}
