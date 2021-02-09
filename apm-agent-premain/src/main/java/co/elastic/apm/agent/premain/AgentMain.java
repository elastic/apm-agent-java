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
package co.elastic.apm.agent.premain;

import java.io.File;
import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystems;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.Enumeration;
import java.util.jar.JarEntry;
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

        if (!JvmRuntimeInfo.isJavaVersionSupported()) {
            // Gracefully abort agent startup is better than unexpected failure down the road when we known a given JVM
            // version is not supported. Agent might trigger known JVM bugs causing JVM crashes, notably on early Java 8
            // versions (but fixed in later versions), given those versions are obsolete and agent can't have workarounds
            // for JVM internals, there is no other option but to use an up-to-date JVM instead.

            String msgTemplate;

            boolean doDisable;
            if (Boolean.parseBoolean(System.getProperty("elastic.apm.disable_bootstrap_checks"))) {
                // safety check disabled, warn end user that it might
                doDisable = false;
                msgTemplate = "WARNING : JVM version unknown or not supported, safety check disabled - %s %s %s";
            } else {
                doDisable = true;
                msgTemplate = "Failed to start agent - JVM version not supported: %s %s %s.\nTo override Java version verification, set the 'elastic.apm.disable_bootstrap_checks' System property to 'true'.";
            }

            System.err.println(String.format(msgTemplate,
                JvmRuntimeInfo.getJavaVersion(), JvmRuntimeInfo.getJavaVmName(), JvmRuntimeInfo.getJavaVmVersion()));

            if (doDisable) {
                return;
            }
        }

        // workaround for classloader deadlock https://bugs.openjdk.java.net/browse/JDK-8194653
        FileSystems.getDefault();

        long delayAgentInitMs = -1L;
        String delayAgentInitMsProperty = System.getProperty("elastic.apm.delay_agent_premain_ms");
        if (delayAgentInitMsProperty != null) {
            try {
                delayAgentInitMs = Long.parseLong(delayAgentInitMsProperty.trim());
            } catch (NumberFormatException numberFormatException) {
                System.err.println("The value of the \"elastic.apm.delay_agent_premain_ms\" System property must be a number");
            }
        }
        if (premain && shouldDelayOnPremain()) {
            delayAgentInitMs = Math.max(delayAgentInitMs, 3000L);
        }
        if (delayAgentInitMs > 0) {
            delayAndInitAgentAsync(agentArguments, instrumentation, premain, delayAgentInitMs);
        } else {
            loadAndInitializeAgent(agentArguments, instrumentation, premain);
        }
    }

    /**
     * Returns whether agent initialization should be delayed when occurring through the {@code premain} route.
     * This works around a JVM bug (https://bugs.openjdk.java.net/browse/JDK-8041920) causing JIT fatal error if
     * agent code causes the loading of MethodHandles prior to JIT compiler initialization.
     * @return {@code true} for any Java 7 and early Java 8 HotSpot JVMs, {@code false} for all others
     */
    static boolean shouldDelayOnPremain() {
        int majorVersion = JvmRuntimeInfo.getMajorVersion();
        return
            (majorVersion == 7) ||
            // In case bootstrap checks were disabled
            (majorVersion == 8 && JvmRuntimeInfo.isHpUx() && JvmRuntimeInfo.getUpdateVersion() < 2) ||
            (majorVersion == 8 && JvmRuntimeInfo.isHotSpot() && JvmRuntimeInfo.getUpdateVersion() < 40);
    }

    private static void delayAndInitAgentAsync(final String agentArguments, final Instrumentation instrumentation,
                                               final boolean premain, final long delayAgentInitMs) {

        System.out.println("Delaying Elastic APM Agent initialization by " + delayAgentInitMs + " milliseconds.");
        Thread initThread = new Thread(ThreadUtils.addElasticApmThreadPrefix("agent-initialization")) {
            @Override
            public void run() {
                try {
                    synchronized (AgentMain.class) {
                        Thread.sleep(delayAgentInitMs);
                        loadAndInitializeAgent(agentArguments, instrumentation, premain);
                    }
                } catch (InterruptedException e) {
                    System.err.println(getName() + " thread was interrupted, the agent will not be attached to this JVM.");
                    e.printStackTrace();
                } catch (Throwable throwable) {
                    System.err.println("Error during Elastic APM Agent initialization: " + throwable.getMessage());
                    throwable.printStackTrace();
                }
            }
        };
        initThread.setDaemon(true);
        initThread.start();
    }

    private synchronized static void loadAndInitializeAgent(String agentArguments, Instrumentation instrumentation, boolean premain) {
        try {
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
        // verify agent shading at runtime, because tests might not have been always executed (when creating plugin)
        // and failure to properly shade will create unexpected and complex behavior that is hard to diagnose and
        // debug, thus the cost of doing this at runtime is worth it.
        return verifyProperAgentShading(agentJar.getAbsoluteFile());
    }

    public static File verifyProperAgentShading(File file) {
        JarFile jarFile = null;
        try {
            jarFile = new JarFile(file);
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                int ignore = entry.isDirectory() ? 1 : 0;
                ignore += name.startsWith("META-INF/") ? 1 : 0;
                ignore += name.startsWith("co/elastic/apm/agent/") ? 1 : 0;
                ignore += name.startsWith("schema/") ? 1 : 0;
                ignore += name.startsWith("asyncprofiler/") ? 1 : 0;
                ignore += name.startsWith("bootstrap/") ? 1 : 0;
                ignore += name.startsWith("ElasticApmLog4j-") ? 1 : 0;
                ignore += name.startsWith("elasticapmlog4j2.component.properties") ? 1 : 0;
                if (ignore == 0) {
                    throw new IllegalStateException(String.format("unexpected file shaded in agent '%s', please check agent/plugin packaging", name));
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException(String.format("unable to load agent jar file : %s", file.getAbsolutePath()), e);
        } finally {
            if (jarFile != null) {
                try {
                    jarFile.close();
                } catch (IOException e) {
                    // silently ignored
                }
            }
        }
        return file;
    }
}
