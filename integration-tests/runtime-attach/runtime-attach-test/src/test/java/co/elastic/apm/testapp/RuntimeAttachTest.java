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

import com.sun.tools.attach.AttachNotSupportedException;
import com.sun.tools.attach.VirtualMachine;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import java.io.IOException;
import java.net.URL;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class RuntimeAttachTest {

    private static final ObjectName JMX_BEAN;

    // set to true for easier debugging
    private static final boolean IS_DEBUG = false;

    private static final int TIMEOUT_SECONDS = 10;

    private static final int APP_TIMEOUT;

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
    }

    @Test
    void cliAttach() {
        ProcessHandle appJvm = startAppForkedJvm(false);
        long pid = appJvm.pid();

        waitForJmxRegistration(pid);

        await().until(() -> getWorkUnitCount(false) > 0);
        assertThat(getWorkUnitCount(true)).isEqualTo(0);

        startAttacherForkedJvm(
            "--include-pid",
            Long.toString(pid),
            "--config",
            "disable_send=true",
            "application_packages=co.elastic.apm.testapp",
            "cloud_provider=NONE");

        await()
            .timeout(TIMEOUT_SECONDS * 2, TimeUnit.SECONDS)
            .until(() -> getWorkUnitCount(true) > 0);
    }

    @Test
    void selfAttach() {
        ProcessHandle appJvm = startAppForkedJvm(true);
        long pid = appJvm.pid();

        waitForJmxRegistration(pid);

        await().until(() -> getWorkUnitCount(false) > 0);

        // both should be equal, but a 1 offset is expected as updates are not atomic
        assertThat(getWorkUnitCount(true))
            .isCloseTo(getWorkUnitCount(false), Offset.offset(1));
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

    private ProcessHandle startAppForkedJvm(boolean selfAttach) {
        ArrayList<String> args = new ArrayList<>();
        args.add(Integer.toString(APP_TIMEOUT));
        if (selfAttach) {
            args.add("self-attach");
        }
        return startForkedJvm(getAppJar(), args);
    }

    public ProcessHandle startAttacherForkedJvm(String... args) {
        return startForkedJvm(getCliAttachJar(), Arrays.asList(args));
    }

    private Path getAppJar() {
        return getClassJarLocation("runtime-attach-app", "co.elastic.apm.testapp.AppMain");
    }

    private Path getCliAttachJar() {
        return getClassJarLocation("apm-agent-attach-cli", "co.elastic.apm.attach.AgentAttacher");
    }

    // might be later refactored and merged with AgentFileAccessor as it provides similar features
    private Path getClassJarLocation(String jarPrefix, String className) {
        Class<?> main;
        try {
            main = Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
        URL location = main.getProtectionDomain().getCodeSource().getLocation();
        Path path = null;
        try {
            // get path through URI is required on Windows because raw path starts with drive letter '/C:/'
            path = Paths.get(location.toURI());
        } catch (URISyntaxException e){
            throw new IllegalStateException(e);
        }

        if (path.endsWith(Paths.get("target", "classes"))) {
            return findJar(path.getParent(), jarPrefix);
        } else {
            return path;
        }

    }

    private ProcessHandle startForkedJvm(Path executableJar, List<String> args) {
        ArrayList<String> cmd = new ArrayList<>();
        cmd.add(getJavaBinaryPath());
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

    private static String getJavaBinaryPath() {
        boolean isWindows = System.getProperty("os.name").startsWith("Windows");
        String executable = isWindows ? "java.exe" : "java";
        Path path = Paths.get(System.getProperty("java.home"), "bin", executable);
        if (!Files.isExecutable(path)) {
            throw new IllegalStateException("unable to find java path");
        }
        return path.toAbsolutePath().toString();
    }

    private static Path findJar(Path folder, String jarPrefix) {
        Stream<Path> found;
        try {
            found = Files.find(folder, 1,
                (path, basicFileAttributes) -> {
                    String fileName = path.getFileName().toString();
                    return fileName.startsWith(jarPrefix)
                        && fileName.endsWith(".jar")
                        && !fileName.endsWith("-javadoc.jar")
                        && !fileName.endsWith("-sources.jar")
                        && !fileName.endsWith("-slim.jar");
                }
            );
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return found.findFirst().orElseThrow(() -> {
            throw new IllegalStateException("Unable to find packaged test application in folder :" + folder.toAbsolutePath());
        });
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
}


