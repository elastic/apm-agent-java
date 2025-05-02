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

import co.elastic.apm.agent.test.AgentFileAccessor;
import co.elastic.apm.agent.test.AgentTestContainer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.testcontainers.containers.output.OutputFrame;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class AgentSetupIT {

    @ParameterizedTest
    @CsvSource({
        "openjdk:7,STANDARD",
        "openjdk:8,STANDARD",
        "openjdk:8,JAVA8_BUILD",
        "openjdk:11,STANDARD",
        "openjdk:11,JAVA8_BUILD",
        "openjdk:17,STANDARD",
        "openjdk:17,JAVA8_BUILD"
    })
    void testServiceNameAndVersionFromManifest(String image, AgentFileAccessor.Variant agentVariant) {
        try (AgentTestContainer.JarApp app = testAppWithJavaAgent(image, agentVariant)) {

            app.waitingFor(Wait.forLogMessage(".* Starting Elastic APM .*", 1))
                .start();

            assertThat(app.getLogs()).contains(" as My Service Name (My Service Version) on ");
        }
    }

    @Test
    void testSecurityManagerWarning() {
        String expectedMsg = "Security manager without agent grant-all permission";

        try (AgentTestContainer.JarApp app = testAppWithJavaAgent("openjdk:17", AgentFileAccessor.Variant.STANDARD)) {

            app.withSecurityManager(null)
                // using a dummy command since testcontainers 1.21.0 as waiting on stderr msg is not working anymore
                // when the container fails to start. Likely related to https://github.com/testcontainers/testcontainers-java/issues/9956
                .waitingFor(Wait.forSuccessfulCommand("bash --version"))
                // we expect startup to fail fast as JVM should not even properly start
                .withStartupTimeout(Duration.ofSeconds(3));

            app.start();

            assertThat(app.getLogs(OutputFrame.OutputType.STDERR)).contains(expectedMsg);
        }

    }

    @Test
    void testSecurityManagerWithPolicy(@TempDir Path temp) throws IOException {
        Path tempPolicy = temp.resolve("security.policy");

        // use a 'grant all' policy for now
        Files.write(tempPolicy, Arrays.asList(
            "grant codeBase \"file:///tmp/elastic-apm-agent.jar\" {",
            "  permission java.security.AllPermission;",
            "};"), StandardOpenOption.CREATE
        );

        try (AgentTestContainer.JarApp app = testAppWithJavaAgent("openjdk:17", AgentFileAccessor.Variant.STANDARD)) {
            app.withSecurityManager(MountableFile.forHostPath(tempPolicy))
                .withStartupTimeout(Duration.ofSeconds(10))
                .waitingFor(Wait.forLogMessage(".*Hello World!.*", 1))
                .start();
        }
    }

    private AgentTestContainer.JarApp testAppWithJavaAgent(String image, AgentFileAccessor.Variant agentVariant) {
        return AgentTestContainer.jarApp(image)
            .withExecutableJar(Path.of("target/main-app-test.jar"))
            .withArguments("wait") // make test app wait a bit so we can stop it
            .withJavaAgentBinaries()
            .withJavaAgentArgument(agentVariant)
            .withRemoteDebug();
    }

}
