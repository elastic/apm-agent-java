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
package co.elastic.apm.attach;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Utils {

    private static final String AGENT_BASE_DIR = "agent";

    private static Path agentDir = null;

    public static Path getTargetAgentDir(String agentVersion) throws IOException {
        // also checks if dir exists
        if (agentDir != null && Files.isWritable(agentDir) && Files.isReadable(agentDir)) {
            return agentDir;
        }

        if (agentDir != null && Files.exists(agentDir)) {
            // probably a different user is running the CLI, we will just create a new one
            Files.delete(agentDir);
        }

        URL jarLocation = Utils.class.getProtectionDomain().getCodeSource().getLocation();
        Path attacherCliDir = Paths.get(jarLocation.getPath()).getParent();

        Path agentBaseDir;
        if (Files.isWritable(attacherCliDir)) {
            agentBaseDir = attacherCliDir.resolve(AGENT_BASE_DIR);
            if (!Files.exists(agentBaseDir)) {
                Files.createDirectory(agentBaseDir);
            }
        } else {
            agentBaseDir = Files.createTempDirectory(Paths.get(System.getProperty("java.io.tmpdir")), AGENT_BASE_DIR);
        }

        Path agentVersionDir = agentBaseDir.resolve(agentVersion.replace('.', '_'));
        if (!Files.exists(agentVersionDir)) {
            Files.createDirectory(agentVersionDir);
        }
        agentDir = agentVersionDir;
        return agentDir;
    }

    public static Path getTargetLibDir(String agentVersion) throws IOException {
        Path targetLibDir = getTargetAgentDir(agentVersion).resolve("lib");
        if (!Files.exists(targetLibDir)) {
            Files.createDirectory(targetLibDir);
        }
        return targetLibDir;
    }
}
