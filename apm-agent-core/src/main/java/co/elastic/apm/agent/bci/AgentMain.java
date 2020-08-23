/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2020 Elastic and contributors
 * %%
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
 * #L%
 */
package co.elastic.apm.agent.bci;

import javax.annotation.Nullable;
import java.io.File;
import java.lang.instrument.Instrumentation;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystems;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.jar.JarFile;

/**
 * This class is loaded by the system classloader,
 * and adds the rest of the agent to the bootstrap class loader search.
 * <p>
 * This is required to instrument Java core classes like {@link Runnable}.
 * </p>
 * <p>
 * Note that this relies on the fact that the system classloader is a parent-first classloader and first asks the bootstrap classloader
 * to resolve a class.
 * </p>
 */
public class AgentMain {

    /**
     * Allows the installation of this agent via the {@code javaagent} command line argument.
     *
     * @param agentArguments  The agent arguments.
     * @param instrumentation The instrumentation instance.
     */
    public static void premain(String agentArguments, Instrumentation instrumentation) {
        init(agentArguments, instrumentation, true);
    }

    /**
     * Allows the installation of this agent via the Attach API.
     *
     * @param agentArguments  The agent arguments.
     * @param instrumentation The instrumentation instance.
     */
    @SuppressWarnings("unused")
    public static void agentmain(String agentArguments, Instrumentation instrumentation) {
        init(agentArguments, instrumentation, false);
    }

    public synchronized static void init(String agentArguments, Instrumentation instrumentation, boolean premain) {
        if (Boolean.getBoolean("ElasticApm.attached")) {
            // agent is already attached; don't attach twice
            // don't fail as this is a valid case
            // for example, Spring Boot restarts the application in dev mode
            return;
        }

        String javaVersion = System.getProperty("java.version");
        String javaVmName = System.getProperty("java.vm.name");
        String javaVmVersion = System.getProperty("java.vm.version");
        if (!isJavaVersionSupported(javaVersion, javaVmName, javaVmVersion)) {
            // Gracefully abort agent startup is better than unexpected failure down the road when we known a given JVM
            // version is not supported. Agent might trigger known JVM bugs causing JVM crashes, notably on early Java 8
            // versions (but fixed in later versions), given those versions are obsolete and agent can't have workarounds
            // for JVM internals, there is no other option but to use an up-to-date JVM instead.
            System.err.println(String.format("Failed to start agent - JVM version not supported: %s %s %s", javaVersion, javaVmName, javaVmVersion));
            return;
        }

        try {
            // workaround for classloader deadlock https://bugs.openjdk.java.net/browse/JDK-8194653
            FileSystems.getDefault();

            final File agentJarFile = getAgentJarFile();
            try (JarFile jarFile = new JarFile(agentJarFile)) {
                instrumentation.appendToBootstrapClassLoaderSearch(jarFile);
            }
            // invoking via reflection to make sure the class is not loaded by the system classloader,
            // but only from the bootstrap classloader
            Class.forName("co.elastic.apm.agent.bci.ElasticApmAgent", true, null)
                .getMethod("initialize", String.class, Instrumentation.class, File.class, boolean.class)
                .invoke(null, agentArguments, instrumentation, agentJarFile, premain);
            System.setProperty("ElasticApm.attached", Boolean.TRUE.toString());
        } catch (Exception | LinkageError e) {
            System.err.println("Failed to start agent");
            e.printStackTrace();
        }
    }

    /**
     * Checks if a given version of the JVM is likely supported by this agent.
     * <br>
     * Supports values provided before and after https://openjdk.java.net/jeps/223, in case parsing fails due to an
     * unknown version format, we assume it's supported, thus this method might return false positives, but never false
     * negatives.
     *
     * @param version   jvm version, from {@code System.getProperty("java.version")}
     * @param vmName    jvm name, from {@code System.getProperty("java.vm.name")}
     * @param vmVersion jvm version, from {@code System.getProperty("java.vm.version")}
     * @return true if the version is supported, false otherwise
     */
    // package-protected for testing
    static boolean isJavaVersionSupported(String version, String vmName, @Nullable String vmVersion) {
        // new scheme introduced in java 9, thus we can use it as a shortcut
        int major;
        if (version.startsWith("1.")) {
            major = Character.digit(version.charAt(2), 10);
        } else {
            String majorAsString = version.split("\\.")[0];
            int indexOfDash = majorAsString.indexOf('-');
            if (indexOfDash > 0) {
                majorAsString = majorAsString.substring(0, indexOfDash);
            }
            major = Integer.parseInt(majorAsString);
        }

        boolean isHotSpot = vmName.contains("HotSpot(TM)") || vmName.contains("OpenJDK");
        boolean isIbmJ9 = vmName.contains("IBM J9");
        if (major < 7) {
            // given code is compiled with java 7, this one is unlikely in practice
            return false;
        }
        if (isHotSpot) {
            return isHotSpotVersionSupported(version, major);
        } else if (isIbmJ9) {
            return isIbmJ9VersionSupported(vmVersion, major);
        }
        // innocent until proven guilty
        return true;
    }

    private static boolean isHotSpotVersionSupported(String version, int major) {
        switch (major) {
            case 7:
                // versions prior to that have unreliable invoke dynamic support according to https://groovy-lang.org/indy.html
                return isUpdateVersionAtLeast(version, 60);
            case 8:
                return isUpdateVersionAtLeast(version, 40);
            default:
                return true;
        }
    }

    private static boolean isIbmJ9VersionSupported(@Nullable String vmVersion, int major) {
        switch (major) {
            case 7:
                return false;
            case 8:
                // early versions crash during invokedynamic bootstrap
                // the exact version that fixes that error is currently not known
                // presumably, service refresh 5 (build 2.8) fixes the issue
                return !"2.8".equals(vmVersion);
            default:
                return true;
        }
    }

    private static boolean isUpdateVersionAtLeast(String version, int minimumUpdateVersion) {
        int updateIndex = version.lastIndexOf("_");
        if (updateIndex <= 0) {
            // GA release '1.8.0'
            return false;
        } else {
            int versionSuffixIndex = version.indexOf('-', updateIndex + 1);
            String updateVersion;
            if (versionSuffixIndex <= 0) {
                updateVersion = version.substring(updateIndex + 1);
            } else {
                updateVersion = version.substring(updateIndex + 1, versionSuffixIndex);
            }
            try {
                return Integer.parseInt(updateVersion) >= minimumUpdateVersion;
            } catch (NumberFormatException e) {
                // in case of unknown format, we just support by default
                return true;
            }
        }
    }

    private static File getAgentJarFile() throws URISyntaxException {
        ProtectionDomain protectionDomain = AgentMain.class.getProtectionDomain();
        CodeSource codeSource = protectionDomain.getCodeSource();
        if (codeSource == null) {
            throw new IllegalStateException(String.format("Unable to get agent location, protection domain = %s", protectionDomain));
        }
        URL location = codeSource.getLocation();
        if (location == null) {
            throw new IllegalStateException(String.format("Unable to get agent location, code source = %s", codeSource));
        }
        final File agentJar = new File(location.toURI());
        if (!agentJar.getName().endsWith(".jar")) {
            throw new IllegalStateException("Agent is not a jar file: " + agentJar);
        }
        return agentJar.getAbsoluteFile();
    }
}
