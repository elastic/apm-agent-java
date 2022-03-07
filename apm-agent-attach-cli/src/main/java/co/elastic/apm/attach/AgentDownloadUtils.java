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
import java.util.concurrent.ConcurrentHashMap;

public class AgentDownloadUtils {

    private static final String AGENT_BASE_DIR = "agent";
    private static final ConcurrentHashMap<String, AgentDownloadUtils> agentVersion2utils = new ConcurrentHashMap<>();

    public static AgentDownloadUtils of(String agentVersion) {
        agentVersion2utils.putIfAbsent(agentVersion, new AgentDownloadUtils(agentVersion));
        return agentVersion2utils.get(agentVersion);
    }

    private final String agentVersion;
    private Path agentDir;

    private AgentDownloadUtils(String agentVersion) {
        this.agentVersion = agentVersion;
    }

    public synchronized Path getTargetAgentDir() throws IOException {
        if (agentDir != null) {
            return agentDir;
        }

        URL jarLocation = AgentDownloadUtils.class.getProtectionDomain().getCodeSource().getLocation();
        String jarLocationPath = jarLocation.getPath();
        if (jarLocationPath.matches("/\\w\\:.*")){
            // looks something like /C:/path... - Paths.get() doesn't like that
            // so strip the leading /
            jarLocationPath = jarLocationPath.substring(1);
        }
        Path attacherCliDir = Paths.get(jarLocationPath).getParent();

        Path agentBaseDir;
        if (Files.isWritable(attacherCliDir)) {
            agentBaseDir = verifyReadableWritableDirExists(attacherCliDir, AGENT_BASE_DIR);
        } else {
            agentBaseDir = Files.createTempDirectory(Paths.get(System.getProperty("java.io.tmpdir")), AGENT_BASE_DIR);
        }

        String agentVersionDirName = agentVersion.replace('.', '_');
        agentDir = verifyReadableWritableDirExists(agentBaseDir, agentVersionDirName);
        return agentDir;
    }

    private Path verifyReadableWritableDirExists(Path parent, String childDirName) throws IOException {
        Path childDirPath = parent.resolve(childDirName);
        boolean createChildDir = true;
        if (Files.exists(childDirPath)) {
            if (Files.isReadable(childDirPath) && Files.isWritable(childDirPath)) {
                createChildDir = false;
            } else {
                // possibly an artifact of running with a different user, in which case we create a dedicated dir for the current user
                childDirPath = parent.resolve(UserRegistry.getCurrentUserName()).resolve(childDirName);
                createChildDir = !Files.exists(childDirPath);
            }
        }
        if (createChildDir) {
            Files.createDirectories(childDirPath);
        }
        return childDirPath;
    }

    public Path getTargetLibDir() throws IOException {
        Path targetLibDir = getTargetAgentDir().resolve("lib");
        if (!Files.exists(targetLibDir)) {
            Files.createDirectory(targetLibDir);
        }
        return targetLibDir;
    }
}
