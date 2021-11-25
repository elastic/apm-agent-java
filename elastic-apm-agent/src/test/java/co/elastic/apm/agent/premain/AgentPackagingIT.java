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
package co.elastic.apm.agent.premain;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import static org.assertj.core.api.Assertions.assertThat;

public class AgentPackagingIT {

    private static Path agentJar;
    private static String agentVersion;

    @BeforeAll
    static void before() throws URISyntaxException, IOException {
        // find packaged jar location

        String classResource = AgentPackagingIT.class.getCanonicalName().replace(".", "/") + ".class";
        URL url = AgentPackagingIT.class.getClassLoader().getResource(classResource);
        assertThat(url).isNotNull();

        Path path = Paths.get(url.toURI());
        Path targetFolder = null;
        while (path != null && targetFolder == null) {
            if (path.endsWith("target")) {
                targetFolder = path;
            }
            path = path.getParent();
        }
        assertThat(targetFolder).isNotNull();

        Path mavenPropertiesFile = targetFolder.resolve("maven-archiver").resolve("pom.properties");
        assertThat(mavenPropertiesFile).isRegularFile();

        Properties mavenProperties = new Properties();
        try (BufferedReader reader = Files.newBufferedReader(mavenPropertiesFile)) {
            mavenProperties.load(reader);
        }

        String artifactId = mavenProperties.getProperty("artifactId");
        assertThat(artifactId).isEqualTo("elastic-apm-agent");

        agentVersion = mavenProperties.getProperty("version");
        assertThat(agentVersion).isNotNull();

        agentJar = targetFolder.resolve(String.format("%s-%s.jar", artifactId, agentVersion));
        assertThat(agentJar).isRegularFile();
    }

    @Test
    void agentManifest() throws IOException {

        JarFile jarFile = new JarFile(agentJar.toFile());

        Manifest manifest = jarFile.getManifest();

        Attributes attributes = manifest.getMainAttributes();
        assertThat(attributes.getValue("Implementation-Version")).isEqualTo(agentVersion);
        String agentClass = attributes.getValue("Agent-Class");
        assertThat(agentClass).isNotEmpty();
        assertThat(attributes.getValue("Premain-Class")).isEqualTo(agentClass);
        assertThat(attributes.getValue("SCM-Revision")).isNotEmpty();
    }

}
