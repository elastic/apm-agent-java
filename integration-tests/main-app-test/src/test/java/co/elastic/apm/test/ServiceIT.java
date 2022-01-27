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

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.io.File;
import java.io.FileFilter;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class ServiceIT {

    @ParameterizedTest
    @ValueSource(strings = {"openjdk:8", "openjdk:11", "openjdk:17"})
    void testServiceNameAndVersionFromManifest(String image) {
        assertThat(new File("target/main-app-test.jar")).exists();
        GenericContainer<?> app = new GenericContainer<>(DockerImageName.parse(image))
            .withFileSystemBind(getAgentJar(), "/tmp/elastic-apm-agent.jar")
            .withFileSystemBind("target/main-app-test.jar", "/tmp/main-app.jar")
            .withCommand("java -javaagent:/tmp/elastic-apm-agent.jar -jar /tmp/main-app.jar")
            .waitingFor(Wait.forLogMessage(".* Starting Elastic APM .*", 1));
        app.start();

        try {
            assertThat(app.getLogs()).contains(" as My Service Name (My Service Version) on ");
        } finally {
            app.stop();
        }
    }

    private static String getAgentJar() {
        File buildDir = new File("../../elastic-apm-agent/target/");
        FileFilter fileFilter = file -> file.getName().matches("elastic-apm-agent-\\d\\.\\d+\\.\\d+(\\.RC\\d+)?(-SNAPSHOT)?.jar");
        return Arrays.stream(buildDir.listFiles(fileFilter))
            .findFirst()
            .map(File::getAbsolutePath)
            .orElseThrow(() -> new IllegalStateException("Agent jar not found. Execute mvn package to build the agent jar."));
    }
}
