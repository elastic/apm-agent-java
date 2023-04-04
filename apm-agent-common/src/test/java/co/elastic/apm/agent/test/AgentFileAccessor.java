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
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Provides access to packaged agent artifacts
 */
public class AgentFileAccessor {

    public enum Variant {
        STANDARD,
        JAVA8_BUILD
    }

    public static Path getPathToJavaagent() {
        return getPathToJavaagent(Variant.STANDARD);
    }

    public static Path getPathToJavaagent(Variant agentBuild) {
        String project = "elastic-apm-agent";
        switch (agentBuild) {
            case STANDARD:
                project = "elastic-apm-agent";
                break;
            case JAVA8_BUILD:
                project = "elastic-apm-agent-java8";
                break;
        }
        return getArtifactPath(
            Paths.get(project),
            "",
            ".jar");
    }

    public static Path getPathToAttacher() {
        return getArtifactPath(
            Paths.get("apm-agent-attach-cli"),
            "",
            ".jar");
    }

    public static Path getPathToSlimAttacher() {
        return getArtifactPath(
            Paths.get("apm-agent-attach-cli"),
            "-slim",
            ".jar");
    }

    static Path getProjectRoot() {

        // We have to rely on the current directory, which means this method will only work when called from within
        // code that is run from within a project subfolder. As this common code will be packaged in a jar, we can't
        // resolve using the actual jar location using 'getProtectionDomain().getCodeSource().getLocation()' as it will
        // be the maven repository when run from command line, unlike the IDE that will resolve it to the built artifact
        // in the 'target' folder
        Path current = Paths.get(".").toAbsolutePath();
        boolean found = false;
        while (!found && current != null) {
            found = Files.exists(current.resolve("pom.xml"))
                && Files.isDirectory(current.resolve("docs"));
            if (!found) {
                current = current.getParent();
            }
        }

        Objects.requireNonNull(current);
        return current;
    }

    public static Path getArtifactPath(Path modulePath, String artifactSuffix, String extension) {
        Path moduleRoot = getProjectRoot().resolve(modulePath);
        String artifactName = modulePath.getFileName().toString(); // by convention artifact name the last part of the path
        try {
            Path targetFolder = moduleRoot.resolve("target");

            String errorMsg = String.format("unable to find artifact '%s%s-{version}%s' in folder '%s', make sure to run 'mvn package' in folder '%s' first", artifactName, artifactSuffix, extension, targetFolder.toAbsolutePath(), moduleRoot);
            if (!Files.isDirectory(targetFolder)) {
                throw new IllegalStateException(errorMsg);
            }

            Pattern pattern = Pattern.compile(artifactName + "-\\d\\.\\d+\\.\\d+(\\.RC\\d+)?(-SNAPSHOT)?" + artifactSuffix + extension);
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(targetFolder)) {
                for (Path filePath : stream) {
                    if (pattern.matcher(filePath.getFileName().getFileName().toString()).matches()) {
                        return filePath.toAbsolutePath();
                    }
                }
            }

        } catch (IOException e){
            throw new IllegalStateException(e);
        }

        throw new IllegalStateException();
    }

}
