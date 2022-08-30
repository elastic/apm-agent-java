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
package co.elastic.apm.agent.test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Provides access to packaged agent artifacts
 */
public class AgentFileAccessor {

    public static Path getPathToJavaagent() {
        return getArtifactPath(
            Path.of("elastic-apm-agent"),
            "",
            ".jar");
    }

    public static Path getPathToAttacher() {
        return getArtifactPath(
            Path.of("apm-agent-attach-cli"),
            "",
            ".jar");
    }

    public static Path getPathToSlimAttacher() {
        return getArtifactPath(
            Path.of("apm-agent-attach-cli"),
            "-slim",
            ".jar");
    }

    static Path getProjectRoot() {
        URL location = AgentFileAccessor.class.getProtectionDomain().getCodeSource().getLocation();

        Path path;
        try {
            // get path through URI is required on Windows because raw path starts with drive letter '/C:/'
            path = Paths.get(location.toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }

        switch (location.getProtocol()) {
            // direct file access when this class is not packaged in a jar
            case "file":
                return path.getParent().getParent().getParent();
            default:
                throw new IllegalStateException("unknown location protocol");
        }

    }

    public static Path getArtifactPath(Path modulePath, String artifactSuffix, String extension) {
        Path moduleRoot = getProjectRoot().resolve(modulePath);
        String artifactName = modulePath.getFileName().toString(); // by convention artifact name the last part of the path
        try {
            Path targetFolder = moduleRoot.resolve("target");
            return Files.find(targetFolder, 1, (path, attr) -> path.getFileName().toString()
                    .matches(artifactName + "-\\d\\.\\d+\\.\\d+(\\.RC\\d+)?(-SNAPSHOT)?" + artifactSuffix + extension))
                .findFirst()
                .map(Path::toAbsolutePath)
                .orElseThrow(() -> new IllegalStateException(String.format("unable to find artifact %s%s-{version}%s in folder %s, make sure to run 'mvn package' in folder '%s' first", artifactName, artifactSuffix, extension, targetFolder.toAbsolutePath(), moduleRoot)));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

}
