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
package co.elastic.apm.agent.premain;

import co.elastic.apm.agent.common.JvmRuntimeInfo;
import co.elastic.apm.agent.common.ThreadUtils;

import java.io.File;
import java.lang.instrument.Instrumentation;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.FileSystems;
import java.security.CodeSource;
import java.security.ProtectionDomain;

/**
 * This class is loaded by the system classloader,
 * It extracts the apm-agent.jar and loads it in an isolated class loader hierarchy.
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

        if (!BootstrapChecks.defaults().isPassing()) {
            return;
        }

        // workaround for classloader deadlock https://bugs.openjdk.java.net/browse/JDK-8194653
        FileSystems.getDefault();

        long delayAgentInitMs = -1L;
        String delayAgentInitMsProperty = System.getProperty("elastic.apm.delay_agent_premain_ms");
        if (delayAgentInitMsProperty != null) {
            try {
                delayAgentInitMs = Long.parseLong(delayAgentInitMsProperty.trim());
            } catch (NumberFormatException numberFormatException) {
                System.err.println("[elastic-apm-agent] WARN The value of the \"elastic.apm.delay_agent_premain_ms\" System property must be a number");
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
        JvmRuntimeInfo runtimeInfo = JvmRuntimeInfo.ofCurrentVM();
        int majorVersion = runtimeInfo.getMajorVersion();
        return
            (majorVersion == 7) ||
            // In case bootstrap checks were disabled
            (majorVersion == 8 && runtimeInfo.isHotSpot() && runtimeInfo.getUpdateVersion() < 2) ||
            (majorVersion == 8 && runtimeInfo.isHotSpot() && runtimeInfo.getUpdateVersion() < 40);
    }

    private static void delayAndInitAgentAsync(final String agentArguments, final Instrumentation instrumentation,
                                               final boolean premain, final long delayAgentInitMs) {

        System.out.println("[elastic-apm-agent] INFO Delaying Elastic APM Agent initialization by " + delayAgentInitMs + " milliseconds.");
        Thread initThread = new Thread(ThreadUtils.addElasticApmThreadPrefix("agent-initialization")) {
            @Override
            public void run() {
                try {
                    synchronized (AgentMain.class) {
                        Thread.sleep(delayAgentInitMs);
                        loadAndInitializeAgent(agentArguments, instrumentation, premain);
                    }
                } catch (InterruptedException e) {
                    System.err.println("[elastic-apm-agent] ERROR " + getName() + " thread was interrupted, the agent will not be attached to this JVM.");
                    e.printStackTrace();
                } catch (Throwable throwable) {
                    System.err.println("[elastic-apm-agent] ERROR Elastic APM Agent initialization failed: " + throwable.getMessage());
                    throwable.printStackTrace();
                }
            }
        };
        initThread.setDaemon(true);
        initThread.start();
    }

    private synchronized static void loadAndInitializeAgent(String agentArguments, Instrumentation instrumentation, boolean premain) {
        try {
            String agentVersion = AgentMain.class.getPackage().getImplementationVersion();
            if (agentVersion != null) {
                agentVersion = "-" + agentVersion;
            } else {
                agentVersion = "";
            }
            File agentJar = getAgentJarFile();
            URLClassLoader agentClassLoader = new ShadedClassLoader(agentJar, getAgentClassLoaderParent(), "agent/", ".esclass");
            Class.forName("co.elastic.apm.agent.bci.ElasticApmAgent", true, agentClassLoader)
                .getMethod("initialize", String.class, Instrumentation.class, File.class, boolean.class)
                .invoke(null, agentArguments, instrumentation, agentJar, premain);
            System.setProperty("ElasticApm.attached", Boolean.TRUE.toString());
        } catch (Exception | LinkageError e) {
            System.err.println("[elastic-apm-agent] ERROR Failed to start agent");
            e.printStackTrace();
        }
    }

    private static ClassLoader getAgentClassLoaderParent() {
        try {
            return (ClassLoader) ClassLoader.class.getDeclaredMethod("getPlatformClassLoader").invoke(null);
        } catch (Exception e) {
            return null;
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
