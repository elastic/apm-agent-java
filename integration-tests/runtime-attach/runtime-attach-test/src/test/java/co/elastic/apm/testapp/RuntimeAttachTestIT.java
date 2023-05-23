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
package co.elastic.apm.testapp;

import co.elastic.apm.agent.test.AgentFileAccessor;
import co.elastic.apm.agent.test.JavaExecutable;
import com.sun.tools.attach.VirtualMachine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.annotation.Nullable;
import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class RuntimeAttachTestIT {

    private static final ObjectName JMX_BEAN;

    // set to true for easier debugging
    // disabled by default as process output redirection interferes with JUnit output capture
    private static final boolean IS_DEBUG = false;

    private static final int TIMEOUT_SECONDS = 10;

    private static final int APP_TIMEOUT;
    public static final String CONFIG_ATTACHMENT = "Attachment configuration";
    public static final String ELASTICAPM_PROPERTIES = "elasticapm.properties";

    static {
        try {
            JMX_BEAN = new ObjectName("co.elastic.apm.testapp:type=AppMXBean");
        } catch (MalformedObjectNameException e) {
            throw new IllegalStateException(e);
        }
        APP_TIMEOUT = IS_DEBUG ? 10000 : 100;
    }

    private final List<ProcessHandle> forkedJvms = new ArrayList<>();

    private JMXServiceURL jmxUrl;
    private MBeanServerConnection jmxConnection;
    private JMXConnector jmxConnector;

    @AfterEach
    void after() {
        if (jmxConnector != null) {
            askAppStopJmx();
            try {
                jmxConnector.close();
            } catch (IOException ignored) {
            } finally {
                jmxConnector = null;
                jmxConnection = null;
                jmxUrl = null;
            }
        }

        for (ProcessHandle jvm : forkedJvms) {
            terminateJvm(jvm);
        }
        forkedJvms.clear();
        jmxUrl = null;

        assertThat(Path.of(System.getProperty("java.io.tmpdir"), ELASTICAPM_PROPERTIES))
            .doesNotExist();

    }

    @Test
    void testSimpleAttachCli() {
        testSimpleAttach(false);
    }

    @Test
    void testSimpleAttachSelfAttach() {
        testSimpleAttach(true);
    }

    private void testSimpleAttach(boolean selfAttach) {
        ProcessHandle appJvm = startAppForkedJvm(selfAttach, null);
        long pid = appJvm.pid();

        waitForJmxRegistration(pid);

        await("wait for application to start work")
            .until(() -> getWorkUnitCount(false) > 0);

        if (!selfAttach) {
            assertThat(getWorkUnitCount(true))
                .describedAs("instrumentation should not start before using CLI attacher")
                .isEqualTo(0);

            startAttacherForkedJvm(
                "--include-pid",
                Long.toString(pid),
                "--config",
                "disable_send=true",
                "application_packages=co.elastic.apm.testapp",
                "cloud_provider=NONE");
        }

        waitForInstrumentedWork();
    }

    @Test
    void testExternalConfigurationCli(@TempDir File tmp) throws IOException {
        // only testing with cli-attach for simplicity as it's easier to provide custom parameters
        // should behave in a similar way with api attach.

        String serviceName = "cli-attach-external-config";

        Path logFile = getAgentLog(tmp.toPath());
        Path configFile = writeAgentConfig(tmp.toPath(), "config.properties", Map.of(
            "service_name", serviceName,
            "log_level", "DEBUG"
        ));

        testCliAttachConfig(
            Map.of("log_file", logFile.toString(), // log file provided directly through attach parameters
                "config_file", configFile.toString()),
            logFile,
            Set.of(
                // both should be provided through runtime attachment
                AgentConfig.of("log_file", logFile.toString(), CONFIG_ATTACHMENT),
                AgentConfig.of("config_file", configFile.toString(), CONFIG_ATTACHMENT),
                // those should be loaded from the content if 'config_file', which allows 'log_level' to be dynamic
                AgentConfig.of("service_name", serviceName, configFile.toString()),
                AgentConfig.of("log_level", "DEBUG", configFile.toString())
            ));

    }

    @Test
    void testExternalConfigurationSelfAttach(@TempDir File tmp) throws IOException {

        Path logFile = getAgentLog(tmp.toPath());

        Path configFile = writeAgentConfig(tmp.toPath(), "setup.properties", Map.of(
            // log file location is provided in the external configuration file
            "log_file", logFile.toString(),
            "log_level", "DEBUG"
        ));

        testSelfAttachConfig(logFile, configFile, Set.of(
            // this should come from the runtime attach parameters provided by the application
            AgentConfig.of("service_name", "self-attach-external-config", CONFIG_ATTACHMENT),
            // this should come from the external configuration file
            AgentConfig.of("log_file", logFile.toString(), configFile.toString()),
            AgentConfig.of("log_level", "DEBUG", configFile.toString())
        ));

    }

    @Test
    void testDefaultExternalConfigLocationIgnored(@TempDir File tmp) throws IOException {
        // the 'elasticapm.properties' should be ignored when using runtime attach
        // otherwise it can get confusing for the end-user

        String serviceName = "cli-attach-external-default-config";

        Path logFile = getAgentLog(tmp.toPath());
        Path configFile = writeAgentConfig(tmp.getParentFile().toPath(), ELASTICAPM_PROPERTIES, Map.of(
            "service_name", serviceName,
            "log_level", "DEBUG"
        ));

        try {
            testCliAttachConfig(
                Map.of(
                    "log_file", logFile.toString()),
                logFile,
                Set.of(
                    AgentConfig.of("log_file", logFile.toString(), CONFIG_ATTACHMENT),
                    // the service name we should get is the one from the app classpath
                    // this only works because 'classpath:elasticapm.properties' is in the app system classpath so the
                    // agent can read it.
                    AgentConfig.of("service_name", "classpath-service-name", "classpath:elasticapm.properties"),
                    // should not be set at all
                    AgentConfig.ofMissing("log_level")
                )
            );
        } finally {
            Files.delete(configFile);
        }

        assertThat(configFile).doesNotExist();
    }


    private void waitForInstrumentedWork() {
        await("wait for application work instrumentation")
            .timeout(TIMEOUT_SECONDS * 2, TimeUnit.SECONDS)
            .until(() -> getWorkUnitCount(true) > 0);
    }

    private void waitForJmxRegistration(long pid) {
        initJmx(pid);

        await()
            .timeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .untilAsserted(() -> jmxConnection.getMBeanInfo(JMX_BEAN));

        System.out.println("JMX registration OK for JVM PID = " + pid);

    }

    private void askAppStopJmx() {
        try {
            jmxConnection.invoke(JMX_BEAN, "exit", new Object[0], new String[0]);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private int getWorkUnitCount(boolean instrumented) {
        assertThat(jmxConnection).isNotNull();
        try {
            Object attribute = jmxConnection.getAttribute(JMX_BEAN, instrumented ? "InstrumentedWorkUnitsCount" : "WorkUnitsCount");
            assertThat(attribute).isInstanceOf(Integer.class);
            return (Integer) attribute;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void initJmx(long pid) {
        await("JVM start and JMX connection").timeout(TIMEOUT_SECONDS, TimeUnit.SECONDS).until(() -> {
            String url;
            VirtualMachine vm;
            try {
                vm = VirtualMachine.attach(Long.toString(pid));
                assertThat(vm).describedAs("unable to attach to JVM with PID %d", pid)
                    .isNotNull();
                url = vm.startLocalManagementAgent();

                jmxUrl = new JMXServiceURL(url);
                jmxConnector = JMXConnectorFactory.connect(jmxUrl);
                jmxConnection = jmxConnector.getMBeanServerConnection();

                vm.detach();
                return true;
            } catch (Exception e) {
                // Windows might throw InternalError
                return false;
            }
        });

    }

    private ProcessHandle startAppForkedJvm(boolean selfAttach, @Nullable Path config) {
        ArrayList<String> args = new ArrayList<>();
        args.add(Integer.toString(APP_TIMEOUT));
        if (selfAttach) {
            args.add("self-attach");
        }
        if (null != config) {
            args.add(config.toString());
        }
        return startForkedJvm(getAppJar(), args);
    }

    public ProcessHandle startAttacherForkedJvm(String... args) {
        return startAttacherForkedJvm(Arrays.asList(args));
    }

    public ProcessHandle startAttacherForkedJvm(List<String> args) {
        return startForkedJvm(AgentFileAccessor.getPathToAttacher(), args);
    }

    private Path getAppJar() {
        return AgentFileAccessor.getArtifactPath(
            Path.of("integration-tests", "runtime-attach", "runtime-attach-app"),
            "",
            ".jar");
    }

    private ProcessHandle startForkedJvm(Path executableJar, List<String> args) {
        ArrayList<String> cmd = new ArrayList<>();
        cmd.add(JavaExecutable.getBinaryPath());
        cmd.add("-jar");
        cmd.add(executableJar.toString());
        cmd.addAll(args);

        ProcessBuilder builder = new ProcessBuilder(cmd);

        if (IS_DEBUG) {
            builder.redirectErrorStream(true).inheritIO();
        }
        try {
            ProcessHandle handle = builder.start().toHandle();
            forkedJvms.add(handle);
            System.out.format("Started forked JVM, PID = %d, CMD = %s\n", handle.pid(), String.join(" ", cmd));
            return handle;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void terminateJvm(ProcessHandle jvm) {
        int retryCount = 5;
        while (jvm.isAlive() && retryCount-- > 0) {
            jvm.destroy();
            try {
                Thread.sleep(100);
            } catch (InterruptedException ignored) {
            }
        }
        if (jvm.isAlive()) {
            jvm.destroyForcibly();
        }

    }

    void testCliAttachConfig(Map<String, String> attachConfig,
                             Path agentLog,
                             Set<AgentConfig> expectedConfig) throws IOException {

        ProcessHandle appJvm = startAppForkedJvm(false, null);
        long pid = appJvm.pid();

        waitForJmxRegistration(pid);

        List<String> completeAttachArgs = new ArrayList<>(Arrays.asList("--include-pid", Long.toString(pid)));

        if (!attachConfig.isEmpty()) {
            completeAttachArgs.add("--config");
        }
        completeAttachArgs.addAll(attachConfig.entrySet().stream()
            .map(e -> String.format("%s=%s", e.getKey(), e.getValue()))
            .collect(Collectors.toSet()));

        startAttacherForkedJvm(completeAttachArgs);

        waitForInstrumentedWork();

        checkConfigFromAgentLog(agentLog, expectedConfig);
    }

    void testSelfAttachConfig(Path agentLog, Path selfAttachConfig, Set<AgentConfig> expectedConfig) throws IOException {

        ProcessHandle appJvm = startAppForkedJvm(true, selfAttachConfig);
        long pid = appJvm.pid();

        waitForJmxRegistration(pid);

        // for self-attach it's important to wait for instrumented work to begin
        // otherwise we have no guarantee the agent has been started yet
        waitForInstrumentedWork();

        checkConfigFromAgentLog(agentLog, expectedConfig);

    }

    private Path getAgentLog(Path folder) {
        return folder.resolve("agent.log").toAbsolutePath();
    }

    private Path writeAgentConfig(Path folder, String filename, Map<String, String> config) throws IOException {
        return writeAgentConfig(folder.resolve(filename), config).toAbsolutePath();
    }

    private Path writeAgentConfig(Path filePath, Map<String, String> config) throws IOException {
        Properties externalConfig = new Properties();
        externalConfig.putAll(config);

        Files.createDirectories(filePath.getParent());

        try (BufferedWriter writer = Files.newBufferedWriter(filePath)) {
            externalConfig.store(writer, "agent external config");
        }

        return filePath;
    }

    /**
     * Checks agent configuration by parsing the log file output
     *
     * @param agentLog        agent log file
     * @param expectedConfigs set of expected configuration keys to check
     * @throws IOException when unable to read log file
     */
    private void checkConfigFromAgentLog(Path agentLog, Set<AgentConfig> expectedConfigs) throws IOException {
        // this is definitely weak and might break
        List<String> lines = Files.readAllLines(agentLog);

        String logPrefix = ".StartupInfo - ";

        Map<String, AgentConfig> actualConfig = new HashMap<>();

        lines.stream()
            .filter(l -> l.contains(logPrefix) && l.contains("source:"))
            .map(l -> l.substring(l.indexOf(logPrefix) + logPrefix.length()))
            .map(AgentConfig::parse)
            .forEach(config -> actualConfig.put(config.key, config));

        assertThat(actualConfig)
            .isNotEmpty();

        for (AgentConfig expectedConfig : expectedConfigs) {

            AgentConfig value = actualConfig.get(expectedConfig.key);
            if (expectedConfig.expectedMissing()) {
                assertThat(value)
                    .describedAs("agent configuration should not be set for %s", expectedConfig.key)
                    .isNull();
            } else {
                assertThat(value)
                    .describedAs("agent configuration is missing entry for %s", expectedConfig.key)
                    .isNotNull()
                    .describedAs("agent configuration unexpected value or source")
                    .isEqualTo(expectedConfig);
            }

        }
    }

    private static class AgentConfig {
        private static final Pattern REGEX = Pattern.compile("(?<key>.*): '(?<value>.*)' \\(source: (?<source>.*)\\)");
        private final String key;
        private final String value;
        private final String source;

        private AgentConfig(String key, String value, String source) {
            this.key = Objects.requireNonNull(key);
            this.value = value;
            this.source = source;
        }

        public static AgentConfig of(String key, String value, String source) {
            return new AgentConfig(
                Objects.requireNonNull(key),
                Objects.requireNonNull(value),
                Objects.requireNonNull(source));
        }

        public static AgentConfig ofMissing(String key) {
            return new AgentConfig(key, null, null);
        }

        public boolean expectedMissing() {
            return value == null && source == null;
        }

        public static AgentConfig parse(String logLine) {
            Matcher matcher = REGEX.matcher(logLine);
            if (!matcher.matches() || matcher.groupCount() != 3) {
                throw new IllegalStateException("unexpected log format: " + logLine);
            }

            return AgentConfig.of(matcher.group("key"), matcher.group("value"), matcher.group("source"));
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            AgentConfig that = (AgentConfig) o;
            return key.equals(that.key) && value.equals(that.value) && source.equals(that.source);
        }

        @Override
        public int hashCode() {
            return Objects.hash(key, value, source);
        }

        @Override
        public String toString() {
            return "AgentConfig{" +
                "key='" + key + '\'' +
                ", value='" + value + '\'' +
                ", source='" + source + '\'' +
                '}';
        }
    }
}


