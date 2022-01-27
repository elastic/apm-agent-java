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
package co.elastic.apm.agent.impl.metadata;


import co.elastic.apm.agent.common.util.ProcessExecutionUtil;
import co.elastic.apm.agent.configuration.ServerlessConfiguration;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;

import javax.annotation.Nullable;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static co.elastic.apm.agent.common.util.ProcessExecutionUtil.cmdAsString;

/**
 * Information about the system the agent is running on.
 */
public class SystemInfo {
    private static final Logger logger = LoggerFactory.getLogger(SystemInfo.class);

    private static final String CONTAINER_UID_REGEX = "^[0-9a-fA-F]{64}$";
    private static final String SHORTENED_UUID_PATTERN = "^[0-9a-fA-F]{8}\\-[0-9a-fA-F]{4}\\-[0-9a-fA-F]{4}\\-[0-9a-fA-F]{4}\\-[0-9a-fA-F]{4,}";
    private static final String POD_REGEX = "(?:^/kubepods[\\S]*/pod([^/]+)$)|(?:kubepods[^/]*-pod([^/]+)\\.slice)";

    /**
     * Architecture of the system the agent is running on.
     */
    private final String architecture;

    /**
     * Hostname configured manually through {@link co.elastic.apm.agent.configuration.CoreConfiguration#hostname}.
     */
    @Nullable
    private final String configuredHostname;

    /**
     * Hostname detected automatically.
     */
    @Nullable
    private final String detectedHostname;

    /**
     * Name of the system platform the agent is running on.
     */
    private final String platform;

    /**
     * Info about the container the agent is running on, where applies
     */
    @Nullable
    private Container container;

    /**
     * Info about the Kubernetes pod/node the agent is running on, where applies
     */
    @Nullable
    private Kubernetes kubernetes;

    public SystemInfo(String architecture, @Nullable String configuredHostname, @Nullable String detectedHostname, String platform) {
        this(architecture, configuredHostname, detectedHostname, platform, null, null);
    }

    SystemInfo(String architecture, @Nullable String configuredHostname, @Nullable String detectedHostname,
               String platform, @Nullable Container container, @Nullable Kubernetes kubernetes) {
        this.architecture = architecture;
        this.configuredHostname = configuredHostname;
        this.detectedHostname = detectedHostname;
        this.platform = platform;
        this.container = container;
        this.kubernetes = kubernetes;
    }

    /**
     * Creates a {@link SystemInfo} containing auto-discovered info about the system.
     * This method may block on reading files and executing external processes.
     * @param configuredHostname hostname configured through the {@link co.elastic.apm.agent.configuration.CoreConfiguration#hostname} config
     * @param timeoutMillis enables to limit the execution of the system discovery task
     * @param serverlessConfiguration serverless config
     * @return a future from which this system's info can be obtained
     */
    public static SystemInfo create(final @Nullable String configuredHostname, final long timeoutMillis, ServerlessConfiguration serverlessConfiguration) {
        final String osName = System.getProperty("os.name");
        final String osArch = System.getProperty("os.arch");

        if (serverlessConfiguration.runsOnAwsLambda()) {
            return new SystemInfo(osArch, null, null, osName);
        }

        SystemInfo systemInfo;
        if (configuredHostname != null && !configuredHostname.isEmpty()) {
            systemInfo = new SystemInfo(osArch, configuredHostname, null, osName);
        } else {
            // this call is invoking external commands
            String detectedHostname = discoverHostname(isWindows(osName), timeoutMillis);
            systemInfo = new SystemInfo(osArch, configuredHostname, detectedHostname, osName);
        }
        // this call reads and parses files
        systemInfo.findContainerDetails();
        return systemInfo;

    }

    static boolean isWindows(String osName) {
        return osName.startsWith("Windows");
    }

    /**
     * Discover the current host's name. This method separates operating systems only to Windows and non-Windows,
     * both in the executed hostname-discovery-command and the fallback environment variables.
     * It always starts with execution of a command on an external process, so it may block up to the specified timeout.
     * @param isWindows used to decide how hostname discovery should be executed
     * @param timeoutMillis limits the time this method may block on executing external commands
     * @return the discovered hostname
     */
    @Nullable
    static String discoverHostname(boolean isWindows, long timeoutMillis) {
        String hostname = discoverHostnameThroughCommand(isWindows, timeoutMillis);
        if (hostname == null || hostname.isEmpty()) {
            hostname = fallbackHostnameDiscovery(isWindows);
        }
        if (hostname == null || hostname.isEmpty()) {
            logger.warn("Unable to discover hostname, set log_level to debug for more details");
        }
        return hostname;
    }

    @Nullable
    static String fallbackHostnameDiscovery(boolean isWindows) {
        String hostname = discoverHostnameThroughEnv(isWindows);
        if (hostname == null || hostname.isEmpty()) {
            try {
                hostname = InetAddress.getLocalHost().getHostName();
                if (hostname != null) {
                    hostname = removeDomain(hostname);
                }
            } catch (Exception e) {
                logger.warn("Last fallback for hostname discovery of localhost failed", e);
            }
        }
        return hostname;
    }

    @Nullable
    static String discoverHostnameThroughCommand(boolean isWindows, long timeoutMillis) {
        String hostname;
        List<String> cmd;
        if (isWindows) {
            cmd = new ArrayList<>();
            cmd.add("cmd");
            cmd.add("/c");
            cmd.add("hostname");
            hostname = executeHostnameDiscoveryCommand(cmd, timeoutMillis);
        } else {
            cmd = new ArrayList<>();
            cmd.add("uname");
            cmd.add("-n");
            hostname = executeHostnameDiscoveryCommand(cmd, timeoutMillis);
            if (hostname == null || hostname.isEmpty()) {
                cmd = new ArrayList<>();
                cmd.add("hostname");
                hostname = executeHostnameDiscoveryCommand(cmd, timeoutMillis);
            }
        }
        return hostname;
    }

    /**
     * Tries to discover the current host name by executing the provided command in a spawned process.
     * This method may block up to the specified timeout, waiting for the spawned process to terminate.
     * @param cmd the hostname discovery command
     * @param timeoutMillis maximum time to allow to the provided command to execute
     * @return the discovered hostname
     */
    @Nullable
    private static String executeHostnameDiscoveryCommand(List<String> cmd, long timeoutMillis) {
        String hostname = null;
        ProcessExecutionUtil.CommandOutput commandOutput = ProcessExecutionUtil.executeCommand(cmd, timeoutMillis);
        if (commandOutput.exitedNormally()) {
            hostname = commandOutput.getOutput().toString().trim();
            if (logger.isDebugEnabled()) {
                logger.debug("hostname obtained by executing command {}: {}", cmdAsString(cmd), hostname);
            }
        } else {
            logger.info("Failed to execute command {} with exit code {}", cmdAsString(cmd), commandOutput.getExitCode());
            logger.debug("Command execution error", commandOutput.getExceptionThrown());
        }
        return hostname;
    }

    @Nullable
    static String discoverHostnameThroughEnv(boolean isWindows) {
        String hostname;
        if (isWindows) {
            hostname = System.getenv("COMPUTERNAME");
        } else {
            hostname = System.getenv("HOSTNAME");
            if (hostname == null || hostname.isEmpty()) {
                hostname = System.getenv("HOST");
            }
        }
        return hostname;
    }

    static String removeDomain(String hostname) {
        int indexOfDot = hostname.indexOf('.');
        if (indexOfDot > 0) {
            hostname = hostname.substring(0, indexOfDot);
        }
        return hostname;
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
    SystemInfo findContainerDetails() {
        String containerId = null;
        try {
            Path path = FileSystems.getDefault().getPath("/proc/self/cgroup");
            if (path.toFile().exists()) {
                List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
                for (final String line : lines) {
                    parseContainerId(line);
                    if (container != null) {
                        containerId = container.getId();
                        break;
                    }
                }
            }
        } catch (Throwable e) {
            logger.warn("Failed to read/parse container ID from '/proc/self/cgroup'", e);
        }

        try {
            // Kubernetes Downward API enables setting environment variables. We are looking for the relevant ones to this discovery
            String podUid = System.getenv("KUBERNETES_POD_UID");
            String podName = System.getenv("KUBERNETES_POD_NAME");
            String nodeName = System.getenv("KUBERNETES_NODE_NAME");
            String namespace = System.getenv("KUBERNETES_NAMESPACE");
            if (podUid != null || podName != null || nodeName != null || namespace != null) {
                // avoid overriding valid info with invalid info
                if (kubernetes != null) {
                    if (kubernetes.getPod() != null) {
                        podUid = (podUid != null) ? podUid : kubernetes.getPod().getUid();
                        podName = (podName != null) ? podName : kubernetes.getPod().getName();
                    }
                }

                kubernetes = new Kubernetes(podName, nodeName, namespace, podUid);
            }
        } catch (Throwable e) {
            logger.warn("Failed to read environment variables for Kubernetes Downward API discovery", e);
        }

        logger.debug("container ID is {}", containerId);
        return this;
    }

    /**
     * The virtual file /proc/self/cgroup lists the control groups that the process is a member of. Each line contains
     * three colon-separated fields of the form hierarchy-ID:subsystem-list:cgroup-path.
     *
     * Depending on the filesystem driver used for cgroup management, the cgroup-path will have
     * one of the following formats in a Docker container:
     *
     * 		systemd: /system.slice/docker-<container-ID>.scope
     * 		cgroupfs: /docker/<container-ID>
     *
     * 	In a Kubernetes pod, the cgroup path will look like:
     *
     * 		systemd: /kubepods.slice/kubepods-<QoS-class>.slice/kubepods-<QoS-class>-pod<pod-UID>.slice/<container-iD>.scope
     * 		cgroupfs: /kubepods/<QoS-class>/pod<pod-UID>/<container-iD>
     *
     * @param line a line from the /proc/self/cgroup file
     * @return this SystemInfo object after parsing
     */
    SystemInfo parseContainerId(String line) {
        final String[] fields = line.split(":", 3);
        if (fields.length == 3) {
            String cGroupPath = fields[2];

            // Looking whether the cgroup path part is delimited with `:`, e.g. in containerd cri
            int indexOfIdSeparator = cGroupPath.lastIndexOf(':');
            if (indexOfIdSeparator < 0) {
                indexOfIdSeparator = cGroupPath.lastIndexOf('/');
            }

            if (indexOfIdSeparator >= 0) {
                String idPart = cGroupPath.substring(indexOfIdSeparator + 1);

                // Legacy, e.g.: /system.slice/docker-<CID>.scope
                if (idPart.endsWith(".scope")) {
                    idPart = idPart.substring(0, idPart.length() - ".scope".length()).substring(idPart.indexOf("-") + 1);
                }

                // Looking for kubernetes info
                String dir = cGroupPath.substring(0, indexOfIdSeparator);
                if (dir.length() > 0) {
                    final Pattern pattern = Pattern.compile(POD_REGEX);
                    final Matcher matcher = pattern.matcher(dir);
                    if (matcher.find()) {
                        for (int i = 1; i <= matcher.groupCount(); i++) {
                            String podUid = matcher.group(i);
                            if (podUid != null && !podUid.isEmpty()) {
                                // systemd cgroup driver is being used, so we need to unescape '_' back to '-'.
                                podUid = podUid.replace('_', '-');
                                logger.debug("Found Kubernetes pod UID: {}", podUid);
                                // By default, Kubernetes will set the hostname of the pod containers to the pod name. Users that override
                                // the name should use the Downward API to override the pod name or override the hostname through the hostname config.
                                kubernetes = new Kubernetes(getHostname(), null, null, podUid);
                                break;
                            }
                        }
                    }
                }

                // If the line matched the one of the kubernetes patterns, we assume that the last part is always the container ID.
                // Otherwise we validate that it is a 64-length hex string
                if (kubernetes != null || idPart.matches(CONTAINER_UID_REGEX) || idPart.matches(SHORTENED_UUID_PATTERN)) {
                    container = new Container(idPart);
                }
            }
        }
        if (container == null) {
            logger.debug("Could not parse container ID from '/proc/self/cgroup' line: {}", line);
        }
        return this;
    }

    /**
     * Architecture of the system the agent is running on.
     */
    public String getArchitecture() {
        return architecture;
    }

    /**
     * Returns the hostname. If a non-empty hostname was configured manually, it will be returned.
     * Otherwise, the automatically discovered hostname will be returned.
     * If both are null or empty, this method returns {@code <unknown>}.
     * @deprecated should only be used when communicating to APM Server of version lower than 7.4
     */
     @Deprecated
    public String getHostname() {
         if (configuredHostname != null && !configuredHostname.isEmpty()) {
             return configuredHostname;
         }
         if (detectedHostname != null && !detectedHostname.isEmpty()) {
             return detectedHostname;
         }
         return "<unknown>";
    }

    /**
     * The hostname manually configured through {@link co.elastic.apm.agent.configuration.CoreConfiguration#hostname}
     * @return the manually configured hostname
     */
    @Nullable
    public String getConfiguredHostname() {
        return configuredHostname;
    }

    /**
     * @return the automatically discovered hostname
     */
    @Nullable
    public String getDetectedHostname() {
        return detectedHostname;
    }

    /**
     * Name of the system platform the agent is running on.
     */
    public String getPlatform() {
        return platform;
    }

    /**
     * Info about the container this agent is running on, where applies
     *
     * @return container info
     */
    @Nullable
    public Container getContainerInfo() {
        return container;
    }

    /**
     * Info about the kubernetes Pod and Node this agent is running on, where applies
     *
     * @return container info
     */
    @Nullable
    public Kubernetes getKubernetesInfo() {
        return kubernetes;
    }

    public static class Container {
        private String id;

        Container(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }
    }

    public static class Kubernetes {
        @Nullable
        Pod pod;

        @Nullable
        Node node;

        @Nullable
        private String namespace;

        Kubernetes(@Nullable String podName, @Nullable String nodeName, @Nullable String namespace, @Nullable String podUid) {
            if (podName != null || podUid != null) {
                pod = new Pod(podName, podUid);
            }
            if (nodeName != null) {
                node = new Node(nodeName);
            }
            this.namespace = namespace;
        }

        @Nullable
        public Pod getPod() {
            return pod;
        }

        @Nullable
        public Node getNode() {
            return node;
        }

        @Nullable
        public String getNamespace() {
            return namespace;
        }

        public boolean hasContent() {
            return pod != null || node != null || namespace != null;
        }

        public static class Pod {
            @Nullable
            private String name;
            @Nullable
            private String uid;

            Pod(@Nullable String name, @Nullable String uid) {
                this.name = name;
                this.uid = uid;
            }

            @Nullable
            public String getName() {
                return name;
            }

            @Nullable
            public String getUid() {
                return uid;
            }
        }

        public static class Node {
            private String name;

            Node(String name) {
                this.name = name;
            }

            public String getName() {
                return name;
            }
        }
    }
}
