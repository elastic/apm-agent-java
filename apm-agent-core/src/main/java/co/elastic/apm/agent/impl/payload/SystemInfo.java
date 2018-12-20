/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 Elastic and contributors
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package co.elastic.apm.agent.impl.payload;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Information about the system the agent is running on.
 */
public class SystemInfo {
    private static final Logger logger = LoggerFactory.getLogger(SystemInfo.class);

    /**
     * Architecture of the system the agent is running on.
     */
    private final String architecture;
    /**
     * Hostname of the system the agent is running on.
     */
    private final String hostname;
    /**
     * Name of the system platform the agent is running on.
     */
    private final String platform;

    /**
     * Info about the container the agent is running on
     */
    private final Container container;

    public SystemInfo(String architecture, String hostname, String platform) {
        this(architecture, hostname, platform, null);
    }

    public SystemInfo(String architecture, String hostname, String platform, @Nullable String containerId) {
        this.architecture = architecture;
        this.hostname = hostname;
        this.platform = platform;
        this.container = new Container(containerId);
    }

    public static SystemInfo create() {
        return new SystemInfo(System.getProperty("os.arch"), getNameOfLocalHost(), System.getProperty("os.name"), findContainerId());
    }

    static String getNameOfLocalHost() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return getHostNameFromEnv();
        }
    }

    /**
     * Finding the container ID based on the {@code /proc/self/cgroup} file.
     * Each line in this file represents a control group hierarchy of the form
     * <p>
     * {@code \d+:([^:,]+(?:,[^:,]+)?):(/.*)}
     * <p>
     * with the first field representing the hierarchy ID, the second field representing a comma-separated list of the subsystems bound to
     * the hierarchy, and the last field representing the control group.
     *
     * @return container ID parsed from {@code /proc/self/cgroup} file lines, or {@code null} if can't find/read/parse file lines
     */
    private static @Nullable
    String findContainerId() {
        String containerId = null;
        try {
            Path path = FileSystems.getDefault().getPath("/proc/self/cgroup");
            if (path.toFile().exists()) {
                List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
                for (final String line : lines) {
                    containerId = parseContainerId(line);
                    if (containerId != null) {
                        break;
                    }
                }
            }
        } catch (Throwable e) {
            logger.warn("Failed to read/parse container ID from '/proc/self/cgroup'");
        }
        logger.debug("container ID is {}", containerId);
        return containerId;
    }

    /**
     * The virtual file /proc/self/cgroup lists the control groups that the process is a member of. Each line contains
     * three colon-separated fields of the form hierarchy-ID:subsystem-list:cgroup-path.
     *
     * @param line a line from the /proc/self/cgroup file
     * @return container ID if could be parsed
     */
    @Nullable
    static String parseContainerId(String line) {
        String containerId = null;
        final String[] fields = line.split(":");
        if (fields.length == 3) {
            String cGroupPath = fields[2];
            Path idPathPart = Paths.get(cGroupPath).getFileName();

            if (idPathPart != null) {
                String idPart = idPathPart.toString();

                // Legacy: /system.slice/docker-<CID>.scope
                if (idPart.startsWith("docker-")) {
                    idPart = idPart.substring("docker-".length());
                }
                if (idPart.endsWith(".scope")) {
                    idPart = idPart.substring(0, idPart.length() - ".scope".length());
                }

                if (idPart.matches("^[0-9a-fA-F]{64}$")) {
                    containerId = idPart;
                }
            }
        }
        if (containerId == null) {
            logger.info("Could not parse container ID from '/proc/self/cgroup' line: {}", line);
        }
        return containerId;
    }

    private static String getHostNameFromEnv() {
        // try environment properties.
        String host = System.getenv("COMPUTERNAME");
        if (host == null) {
            host = System.getenv("HOSTNAME");
        }
        if (host == null) {
            host = System.getenv("HOST");
        }
        return host;
    }

    /**
     * Architecture of the system the agent is running on.
     */
    public String getArchitecture() {
        return architecture;
    }

    /**
     * Hostname of the system the agent is running on.
     */
    public String getHostname() {
        return hostname;
    }

    /**
     * Name of the system platform the agent is running on.
     */
    public String getPlatform() {
        return platform;
    }

    /**
     * Info about the container this agent is running on
     *
     * @return container info
     */
    public Container getContainerInfo() {
        return container;
    }

    public static class Container {
        @Nullable
        private String id;

        private Container(@Nullable String id) {
            this.id = id;
        }

        public @Nullable
        String getId() {
            return id;
        }

        public boolean hasContent() {
            return id != null;
        }
    }
}
