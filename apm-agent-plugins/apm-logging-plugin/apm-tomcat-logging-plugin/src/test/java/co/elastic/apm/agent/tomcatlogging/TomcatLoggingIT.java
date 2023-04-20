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
package co.elastic.apm.agent.tomcatlogging;

import co.elastic.apm.agent.test.AgentTestContainer;
import co.elastic.apm.agent.testutils.TestContainersUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.output.WaitingConsumer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.testcontainers.containers.output.OutputFrame.OutputType.STDOUT;

@Disabled // disabled because it's slow and might introduce flakyness
public class TomcatLoggingIT {

    private static final Logger log = LoggerFactory.getLogger(TomcatLoggingIT.class);

    private static final Path testLogsFolder = Path.of("target", "container-logs");

    @BeforeEach
    void before() throws IOException {
        if (Files.exists(testLogsFolder)) {
            Files.walk(testLogsFolder)
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
        }
        Files.createDirectories(testLogsFolder);
    }

    @ParameterizedTest(name = "Tomcat {0}")
    @ValueSource(strings = {
        "10.1",
        "9.0",
        "8.5",
        "8.0",
        "7.0"
    })
    void testLogShading(String version) throws IOException, InterruptedException, TimeoutException {

        try (AgentTestContainer.Generic container = new AgentTestContainer.Generic("tomcat:" + version)
            .withRemoteDebug()
            .withJavaAgent()
            .withJvmArgumentsVariable("CATALINA_OPTS")
            .withEnv("ELASTIC_APM_LOG_ECS_REFORMATTING", "shade")
            .withEnv("ELASTIC_APM_LOG_ECS_REFORMATTING_DIR", "ecs-logs")
            .withEnv("ELASTIC_APM_DISABLE_SEND", "true")
            .withEnv("ELASTIC_APM_CENTRAL_CONFIG", "false")
            .withEnv("ELASTIC_APM_CLOUD_PROVIDER", "none")
            .withCreateContainerCmdModifier(TestContainersUtils.withMemoryLimit(1024))
            // wait for agent startup: waiting for a specific log message in standard output (not sure if we can listen to another log file)
            .waitingFor(Wait.forLogMessage(".*Server startup in.*", 1)
                .withStartupTimeout(Duration.ofSeconds(15)))) {
            container.start();

            // wait for agent to start (which is partially async in tomcat)
            WaitingConsumer consumer = new WaitingConsumer();
            container.followOutput(consumer, STDOUT);
            consumer.waitUntil(frame ->
                frame.getUtf8String().contains("Starting Elastic APM"), 10, TimeUnit.SECONDS);

            Slf4jLogConsumer logConsumer = new Slf4jLogConsumer(log);
            container.followOutput(logConsumer);

            int v = Integer.parseInt(version.replaceAll("\\.", ""));

            // create a fake deploy to ensure something has to be logged when the agent startup is delayed
            String srcWebappFolder = v == 80 ? "webapps" : "webapps.dist";
            Container.ExecResult cpCmd = container.execInContainer("cp", "-r", "/usr/local/tomcat/" + srcWebappFolder + "/manager", "/usr/local/tomcat/webapps/manager-copy");
            assertThat(cpCmd.getExitCode())
                .describedAs(cpCmd.getStderr().trim())
                .isEqualTo(0);

            if (v < 85) {
                Thread.sleep(10000);
            }

            // copy reformatted logs back to docker host (where this test runs)
            String logsFolder = "/usr/local/tomcat/logs";
            Container.ExecResult listCmd = container.execInContainer("/usr/bin/find", "/usr/local/tomcat/logs", "-type", "f");
            assertThat(listCmd.getExitCode())
                .describedAs(listCmd.getStderr().trim())
                .isEqualTo(0);
            String[] files = listCmd.getStdout().split("\\n");
            assertThat(files)
                .describedAs("at least one ECS formatted file expected")
                .isNotEmpty();

            for (String file : files) {
                String path = file.substring(logsFolder.length());
                String[] pathParts = path.split("/");
                assertThat(pathParts.length).isGreaterThanOrEqualTo(1);
                Path targetPath = testLogsFolder;
                for (String pathPart : pathParts) {
                    targetPath = targetPath.resolve(pathPart);
                }
                Files.createDirectories(targetPath.getParent());
                container.copyFileFromContainer(file, targetPath.toString());
            }

            assertThat(testLogsFolder).isNotEmptyDirectory();
            Path ecsLogsDir = testLogsFolder.resolve("ecs-logs");
            assertThat(ecsLogsDir).isDirectory().isNotEmptyDirectory();
            List<Path> ecsJsonFiles = Files.find(ecsLogsDir, 1, new BiPredicate<Path, BasicFileAttributes>() {
                @Override
                public boolean test(Path path, BasicFileAttributes basicFileAttributes) {
                    String fileName = path.getFileName().toString();
                    return fileName.contains("ecs.json") && !fileName.endsWith(".lck");
                }
            }).collect(Collectors.toList());

            assertThat(ecsJsonFiles).isNotEmpty();
        }

    }
}
