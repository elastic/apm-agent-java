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
import co.elastic.apm.agent.configuration.CoreConfigurationImpl;
import co.elastic.apm.agent.configuration.ServerlessConfigurationImpl;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static co.elastic.apm.agent.common.util.ProcessExecutionUtil.cmdAsString;

/**
 * Information about the system the agent is running on.
 */
public class SystemInfo {
    private static final Logger logger = LoggerFactory.getLogger(SystemInfo.class);

    private static final String CONTAINER_REGEX_64 = "[0-9a-fA-F]{64}";
    private static final String CONTAINER_UID_REGEX = "^" + CONTAINER_REGEX_64 + "$";
    private static final String SHORTENED_UUID_PATTERN = "^[0-9a-fA-F]{8}\\-[0-9a-fA-F]{4}\\-[0-9a-fA-F]{4}\\-[0-9a-fA-F]{4}\\-[0-9a-fA-F]{4,}";
    private static final String AWS_FARGATE_UID_REGEX = "^[0-9a-fA-F]{32}\\-[0-9]{10}$";
    private static final String POD_REGEX = "(?:^/kubepods[\\S]*/pod([^/]+)$)|(?:kubepods[^/]*-pod([^/]+)\\.slice)";

    private static final String HOST_FILE = "/etc/hostname";
    private static final Pattern CGROUPV2_CONTAINER_PATTERN = Pattern.compile("^.*(" + CONTAINER_REGEX_64 + ").*$");

    private static final String SELF_CGROUP = "/proc/self/cgroup";
    private static final String SELF_MOUNTINFO = "/proc/self/mountinfo";

    /**
     * Architecture of the system the agent is running on.
     */
    private final String architecture;

    /**
     * Hostname configured manually through {@link CoreConfigurationImpl#hostname}.
     */
    @SuppressWarnings("JavadocReference")
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
     *
     * @param configuredHostname      hostname configured through the {@link CoreConfigurationImpl#hostname} config
     * @param timeoutMillis           enables to limit the execution of the system discovery task
     * @param serverlessConfiguration serverless config
     * @return a future from which this system's info can be obtained
     */
    @SuppressWarnings("JavadocReference")
    public static SystemInfo create(final @Nullable String configuredHostname, final long timeoutMillis, ServerlessConfigurationImpl serverlessConfiguration) {
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
        return systemInfo.findContainerDetails();
    }

    public static boolean isWindows(String osName) {
        return osName.startsWith("Windows");
    }

    /**
     * Discover the current host's name. This method separates operating systems only to Windows and non-Windows,
     * both in the executed hostname-discovery-command and the fallback environment variables.
     * It always starts with execution of a command on an external process, so it may block up to the specified timeout.
     *
     * @param isWindows     used to decide how hostname discovery should be executed
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
            } catch (Exception e) {
                logger.debug("Network fallback for hostname discovery of localhost failed", e);
            }
        }
        if (hostname == null || hostname.isEmpty() && !isWindows) {
            try (FileReader fileReader = new FileReader(HOST_FILE);
                 BufferedReader reader = new BufferedReader(fileReader)) {
                hostname = reader.readLine().trim();
            } catch (IOException e) {
                logger.debug(HOST_FILE + " fallback for hostname discovery of localhost failed", e);
            }
        }
        return hostname;
    }

    @Nullable
    static String discoverHostnameThroughCommand(boolean isWindows, long timeoutMillis) {
        String hostname;
        if (isWindows) {
            List<String> powershellCmd = Arrays.asList("powershell.exe",
                "-NoLogo", "-NonInteractive", "-NoProfile", "-ExecutionPolicy", "Bypass",
                "-Command", "[System.Net.Dns]::GetHostEntry($env:computerName).HostName");
            hostname = executeHostnameDiscoveryCommand(powershellCmd, timeoutMillis);
            if (hostname == null || hostname.isEmpty()) {
                hostname = executeHostnameDiscoveryCommand(Arrays.asList("cmd.exe", "/c", "hostname"), timeoutMillis);
            }
        } else {
            hostname = executeHostnameDiscoveryCommand(Arrays.asList("hostname", "-f"), timeoutMillis);
        }
        return hostname;
    }

    /**
     * Tries to discover the current host name by executing the provided command in a spawned process.
     * This method may block up to the specified timeout, waiting for the spawned process to terminate.
     *
     * @param cmd           the hostname discovery command
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
        if (hostname != null) {
            hostname = hostname.toLowerCase(Locale.ROOT);
        }
        return hostname;
    }

    /**
     * Returns the hostname from environment variables.
     * <br/>
     * Note for Windows: the Windows implementation relies on the COMPUTERNAME environment variable that does not
     * 100% matches the computer name: the returned value is the "netbios name" in upper-case and limited to the first
     * 15 characters of the complete computer name returned by {@code hostname} command.
     *
     * @param isWindows {@literal true} for Windows
     * @return computer/host name from environment variables.
     */
    @Nullable
    static String discoverHostnameThroughEnv(boolean isWindows) {
        String hostname;
        if (isWindows) {
            // Windows implementation will always return an upper-case name
            // limited to the 15 first characters of the actual computer name
            hostname = System.getenv("COMPUTERNAME");
        } else {
            hostname = System.getenv("HOSTNAME");
            if (hostname == null || hostname.isEmpty()) {
                hostname = System.getenv("HOST");
            }
        }
        if (hostname != null) {
            hostname = hostname.toLowerCase(Locale.ROOT);
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
        parseCgroupsFile(FileSystems.getDefault().getPath(SELF_CGROUP));
        if (container == null) {
            parseMountInfo(FileSystems.getDefault().getPath(SELF_MOUNTINFO));
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

        logger.debug("container ID is {}", container != null ? container.getId() : null);
        return this;
    }

    @Nullable
    private void parseMountInfo(Path path) {
        if (!Files.isRegularFile(path)) {
            logger.debug("Could not parse container ID from '{}'", path);
            return;
        }
        try {
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            parseCgroupsV2ContainerId(lines);
            if (container != null) {
                return;
            }
            logger.debug("Could not parse container ID from '{}' lines: {}", path, lines);
        } catch (Throwable e) {
            logger.warn(String.format("Failed to read/parse container ID from '%s'", path), e);
        }
    }

    @Nullable
    private void parseCgroupsFile(Path path) {
        if (!Files.isRegularFile(path)) {
            logger.debug("Could not parse container ID from '{}'", path);
            return;
        }
        try {
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            for (String line : lines) {
                parseCgroupsLine(line);
                if (container != null) {
                    return;
                }
            }
        } catch (Throwable e) {
            logger.warn(String.format("Failed to read/parse container ID from '%s'", path), e);
        }
    }

    /**
     * The virtual file /proc/self/cgroup lists the control groups that the process is a member of. Each line contains
     * three colon-separated fields of the form hierarchy-ID:subsystem-list:cgroup-path.
     * <p>
     * Depending on the filesystem driver used for cgroup management, the cgroup-path will have
     * one of the following formats in a Docker container:
     * </p>
     * <pre>
     *   systemd: /system.slice/docker-<container-ID>.scope
     *   cgroupfs: /docker/<container-ID>
     * </pre>
     * In a Kubernetes pod, the cgroup path will look like:
     * <pre>
     *   systemd: /kubepods.slice/kubepods-<QoS-class>.slice/kubepods-<QoS-class>-pod<pod-UID>.slice/<container-iD>.scope
     *   cgroupfs: /kubepods/<QoS-class>/pod<pod-UID>/<container-iD>
     * </pre
     *
     * @param line a line from the /proc/self/cgroup file
     * @return this SystemInfo object after parsing
     */
    SystemInfo parseCgroupsLine(String line) {
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
                    idPart = idPart.substring(0, idPart.length() - ".scope".length()).substring(idPart.lastIndexOf("-") + 1);
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
                if (kubernetes != null ||
                    idPart.matches(CONTAINER_UID_REGEX) ||
                    idPart.matches(SHORTENED_UUID_PATTERN) ||
                    idPart.matches(AWS_FARGATE_UID_REGEX)) {
                    container = new Container(idPart);
                }
            }
        }
        if (container == null) {
            logger.debug("Could not parse container ID from line: {}", line);
        }
        return this;
    }

    /**
     * @param lines lines from the /proc/self/mountinfo file
     * @return this SystemInfo object after parsing
     */
    SystemInfo parseCgroupsV2ContainerId(List<String> lines) {
        for (String line : lines) {
            int index = line.indexOf(HOST_FILE);
            if (index > 0) {
                String[] parts = line.split(" ");
                if (parts.length > 3) {
                    Matcher matcher = CGROUPV2_CONTAINER_PATTERN.matcher(parts[3]);
                    if (matcher.matches() && matcher.groupCount() == 1) {
                        container = new Container(matcher.group(1));
                    }
                }
            }
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
     *
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
     * The hostname manually configured through {@link CoreConfigurationImpl#hostname}
     *
     * @return the manually configured hostname
     */
    @SuppressWarnings("JavadocReference")
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
        private final String id;

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
