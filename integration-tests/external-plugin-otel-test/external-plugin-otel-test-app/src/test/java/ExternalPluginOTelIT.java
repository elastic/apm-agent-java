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

import co.elastic.apm.agent.test.AgentFileAccessor;
import org.junit.jupiter.api.Test;
import org.testcontainers.Testcontainers;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

public class ExternalPluginOTelIT {

    private static final String DOCKER_IMAGE = "openjdk:11";

    @Test
    void runAppWithTwoExternalPlugins() {

        // remote debug port for container, IDE should be listening to this port
        int debugPort = 5005;
        debugPort = 0;

        if (debugPort > 0) {
            Testcontainers.exposeHostPorts(debugPort);
        }


        String agentJar = "/tmp/agent.jar";
        String appJar = "/tmp/app.jar";

        String cmd = new StringBuilder()
            .append("java ")
            .append(debugPort <= 0 ? "" : String.format("-agentlib:jdwp=transport=dt_socket,server=n,address=%s:%d,suspend=y ", "host.testcontainers.internal", debugPort))
            .append(String.format("-javaagent:%s %s ", agentJar, getAgentArgs()))
            .append(String.format("-jar %s ", appJar))
            .append("--wait")
            .toString();

        GenericContainer<?> app = new GenericContainer<>(DockerImageName.parse(DOCKER_IMAGE))
            .withCopyFileToContainer(MountableFile.forHostPath(AgentFileAccessor.getPathToJavaagent()), agentJar)
            .withCopyFileToContainer(MountableFile.forHostPath("target/external-plugin-otel-test-app.jar"), appJar)
            .withCopyFileToContainer(MountableFile.forHostPath("../external-plugin-otel-test-plugin1/target/external-plugin-otel-test-plugin1.jar"), "/tmp/plugins/plugin1.jar")
            .withCopyFileToContainer(MountableFile.forHostPath("../external-plugin-otel-test-plugin2/target/external-plugin-otel-test-plugin2.jar"), "/tmp/plugins/plugin2.jar")
            .withCommand(cmd)
            .waitingFor(Wait.forLogMessage(".*app start.*", 1));

        try {
            app.start();

            while (app.isRunning()) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    throw new IllegalStateException(e);
                }
            }

            List<String> logLines = Arrays.asList(app.getLogs().split("\n"));
            assertThat(logLines).isNotEmpty();

            String transactionId = null;
            String spanId = null;
            String traceId = null;
            List<String> otelApiLines = logLines.stream().filter(l -> l.startsWith("active span ID =")).collect(Collectors.toList());
            assertThat(otelApiLines).hasSize(3);
            // first and last should be within transaction thus equal
            assertThat(otelApiLines.get(0)).isEqualTo(otelApiLines.get(2));

            Pattern idPattern = Pattern.compile("active span ID = ([a-z0-9]+), trace ID = ([a-z0-9]+)");
            Matcher matcher = idPattern.matcher(otelApiLines.get(0));
            assertThat(matcher.matches()).isTrue();
            assertThat(matcher.groupCount()).isEqualTo(2);
            transactionId = matcher.group(1);
            traceId = matcher.group(2);
            matcher = idPattern.matcher(otelApiLines.get(1));
            assertThat(matcher.matches()).isTrue();
            assertThat(matcher.groupCount()).isEqualTo(2);
            spanId = matcher.group(1);

            assertThat(logLines).containsExactly(
                "app start",
                ">> transaction enter", // added by plugin1
                ">> gauge class co.elastic.apm.agent.opentelemetry.metrics.bridge.v1_14.BridgeObservableLongGauge",
                "start transaction",
                String.format("active span ID = %s, trace ID = %s", transactionId, traceId), // app OTel API
                ">> span enter", // added by plugin2
                "start span",
                String.format("active span ID = %s, trace ID = %s", spanId, traceId), // app OTel API
                "end span",
                "<< span exit", // added by plugin2
                String.format("active span ID = %s, trace ID = %s", transactionId, traceId), // app OTel API
                "end transaction",
                "<< transaction exit", // added by plugin1
                "app end");

        } finally {

            if (debugPort > 0) {
                app.copyFileFromContainer("/tmp/agent.log", "/tmp/agent.log");
            }

            if (app.isRunning()) {
                app.stop();
            }
        }
    }

    private static String getAgentArgs() {
        Map<String, String> agentConfig = new HashMap<>();

        //Required for Otel-Metrics support at the time of writing
        agentConfig.put("enable_experimental_instrumentations", "true");

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

}
