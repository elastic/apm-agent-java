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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.io.File;
import java.io.FileFilter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ServiceIT { // TODO : maybe rename/move this test 'AgentSetupTest' seems a reasonable test to cover most use-cases

    @ParameterizedTest
    @ValueSource(strings = {"openjdk:8", "openjdk:11", "openjdk:17"})
    void testServiceNameAndVersionFromManifest(String image) {
        Path appJar = new File("target/main-app-test.jar").toPath();
        Path agent = Paths.get(getAgentJar());
        GenericContainer<TestAppContainer> app = new TestAppContainer(image)
            .withAppJar(appJar)
            .withJavaAgent(agent)
            .waitingFor(Wait.forLogMessage(".* Starting Elastic APM .*", 1));
        app.start();

        try {
            assertThat(app.getLogs()).contains(" as My Service Name (My Service Version) on ");
        } finally {
            app.stop();
        }
    }

    @Test
    void testSecurityManagerWarning() {
        String image = "openjdk:17";

        GenericContainer<TestAppContainer> app = new TestAppContainer(image)
            .withAppJar(Paths.get("target/main-app-test.jar"))
            .withJavaAgent(Paths.get(getAgentJar()))
            .withSecurityManager()
            .waitingFor(Wait.forLogMessage("Security manager without agent grant-all permission", 1));

        // we expect startup to fail fast as JVM should not even properly start
        app.withStartupTimeout(Duration.ofSeconds(1));

        assertThatThrownBy(app::start, "app startup is expected to fail");
    }

    private static String getAgentJar() { // TODO : use common code to do this
        File buildDir = new File("../../elastic-apm-agent/target/");
        FileFilter fileFilter = file -> file.getName().matches("elastic-apm-agent-\\d\\.\\d+\\.\\d+(\\.RC\\d+)?(-SNAPSHOT)?.jar");
        return Arrays.stream(buildDir.listFiles(fileFilter))
            .findFirst()
            .map(File::getAbsolutePath)
            .orElseThrow(() -> new IllegalStateException("Agent jar not found. Execute mvn package to build the agent jar."));
    }
}
