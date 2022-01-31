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

import co.elastic.apm.agent.common.util.AgentInfo;
import org.assertj.core.description.Description;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;

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

    @Test
    void duplicatedClassesCheck() throws IOException {
        // The agent classes are split in three sub-folders.
        // We need to check that there is a proper partition between those to ensure no class is being copied
        // in multiple locations.

        Map<String, String> classLocation = new HashMap<>();

        JarFile jarFile = new JarFile(agentJar.toFile());

        jarFile.stream().map(ZipEntry::getName).forEach(name -> {
            String location = null;
            String path = null;
            if (name.endsWith(".class")) {
                location = "ROOT";
                path = name;
            } else if (name.endsWith(".esclazz")) {
                location = name.substring(0, name.indexOf('/'));
                path = name.substring(location.length() + 1);
            }

            if (path != null) {
                String existingLocation = classLocation.get(path);
                assertThat(existingLocation)
                    .describedAs("duplicated class for path '%s', defined both in '%s' and '%s'", path, existingLocation, location)
                    .isNull();

                classLocation.put(path, location);
            }
        });
    }

    @Test
    void validateDependencyPackages() throws IOException {

        Set<String> agentDependencyPackages = AgentInfo.getAgentDependencyPackages();
        Set<String> packagesAsPaths = agentDependencyPackages.stream()
            .map(packageName -> packageName.replace('.', '/'))
            .collect(Collectors.toSet());
        packagesAsPaths.addAll(AgentInfo.getAgentRootPackages().stream()
            .map(packageName -> packageName.replace('.', '/'))
            .collect(Collectors.toSet()));

        JarFile jarFile = new JarFile(agentJar.toFile());

        String shadedClassesDir = "agent/";
        jarFile.stream()
            .map(ZipEntry::getName)
            .filter(name -> name.startsWith(shadedClassesDir))
            .filter(name -> name.endsWith(".esclazz"))
            .map(name -> name.substring(shadedClassesDir.length()))
            .filter(name -> name.lastIndexOf('/') > 0)
            .forEach(name -> assertThat(packagesAsPaths.stream().anyMatch(name::startsWith))
                .describedAs(new Description() {
                    @Override
                    public String value() {
                        String packageName = name.substring(0, name.lastIndexOf('/')).replace('/', '.');
                        return String.format("Package %s is used by the agent and not declared by co.elastic.apm.agent.premain.Utils.getAgentDependencyPackages", packageName);
                    }
                })
                .isTrue());
    }
}
