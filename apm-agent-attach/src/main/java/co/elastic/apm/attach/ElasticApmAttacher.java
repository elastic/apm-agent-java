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

import co.elastic.apm.agent.common.util.ResourceExtractionUtil;
import co.elastic.apm.agent.common.util.SystemStandardOutputLogger;
import net.bytebuddy.agent.ByteBuddyAgent;

import javax.annotation.Nullable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Attaches the Elastic Apm agent to the current or a remote JVM
 */
public class ElasticApmAttacher {

    /**
     * This key is very short on purpose.
     * The longer the agent argument ({@code -javaagent:<path>=<args>}), the greater the chance that the max length of the agent argument is reached.
     * Because of a bug in the {@linkplain ByteBuddyAgent.AttachmentProvider.ForEmulatedAttachment emulated attachment},
     * this can even lead to segfaults.
     */
    private static final String TEMP_PROPERTIES_FILE_KEY = "c";

    /**
     * Attaches the Elastic Apm agent to the current JVM.
     * <p>
     * This method may only be invoked once.
     * </p>
     * <p>
     * Tries to load {@code elasticapm.properties} from the classpath, if exists.
     * </p>
     *
     * @throws IllegalStateException if there was a problem while attaching the agent to this VM
     */
    public static void attach() {
        attach(loadPropertiesFromClasspath("elasticapm.properties"));
    }

    /**
     * Attaches the Elastic Apm agent to the current JVM.
     * <p>
     * This method may only be invoked once.
     * </p>
     *
     * @param propertiesLocation the location within the classpath which contains the agent configuration properties file
     * @throws IllegalStateException if there was a problem while attaching the agent to this VM
     * @since 1.11.0
     */
    public static void attach(String propertiesLocation) {
        attach(loadPropertiesFromClasspath(propertiesLocation));
    }

    private static Map<String, String> loadPropertiesFromClasspath(String propertiesLocation) {
        Map<String, String> propertyMap = new HashMap<>();
        final Properties props = new Properties();
        try (InputStream resourceStream = ElasticApmAttacher.class.getClassLoader().getResourceAsStream(propertiesLocation)) {
            if (resourceStream != null) {
                props.load(resourceStream);
                for (String propertyName : props.stringPropertyNames()) {
                    propertyMap.put(propertyName, props.getProperty(propertyName));
                }
            }
        } catch (IOException e) {
            SystemStandardOutputLogger.printStackTrace(e);
        }
        return propertyMap;
    }

    /**
     * Attaches the Elastic Apm agent to the current JVM.
     * <p>
     * This method may only be invoked once.
     * </p>
     *
     * @param configuration the agent configuration
     * @throws IllegalStateException if there was a problem while attaching the agent to this VM
     */
    public static void attach(Map<String, String> configuration) {
        // optimization, this is checked in AgentMain#init again
        if (Boolean.getBoolean("ElasticApm.attached")) {
            return;
        }
        attach(ByteBuddyAgent.ProcessProvider.ForCurrentVm.INSTANCE.resolve(), configuration);
    }

    /**
     * Store configuration to a temporary file
     *
     * @param configuration agent configuration
     * @param folder        temporary folder, use {@literal null} to use default
     * @return created file if any, {@literal null} if none was created
     */
    static File createTempProperties(Map<String, String> configuration, @Nullable File folder) {
        File tempFile = null;
        if (!configuration.isEmpty()) {
            Properties properties = new Properties();
            properties.putAll(configuration);

            try {
                tempFile = File.createTempFile("elstcapm", ".tmp", folder);
                try (FileOutputStream outputStream = new FileOutputStream(tempFile)) {
                    properties.store(outputStream, null);
                }
            } catch (IOException e) {
                SystemStandardOutputLogger.printStackTrace(e);
            }
        }
        return tempFile;
    }

    /**
     * Attaches the agent to a remote JVM
     *
     * @param pid           the PID of the JVM the agent should be attached on
     * @param configuration the agent configuration
     */
    public static void attach(String pid, Map<String, String> configuration) {
        attach(pid, configuration, AgentJarFileHolder.INSTANCE.agentJarFile);
    }

    /**
     * Attaches the agent to a remote JVM
     *
     * @param pid           the PID of the JVM the agent should be attached on
     * @param configuration the agent configuration
     * @param agentJarFile  the agent jar file
     */
    public static void attach(String pid, Map<String, String> configuration, File agentJarFile) {
        if (!configuration.containsKey("activation_method")) {
            configuration.put("activation_method", "PROGRAMMATIC_SELF_ATTACH");
        }
        File tempFile = createTempProperties(configuration, null);
        String agentArgs = tempFile == null ? null : TEMP_PROPERTIES_FILE_KEY + "=" + tempFile.getAbsolutePath();

        attachWithFallback(agentJarFile, pid, agentArgs);
        if (tempFile != null) {
            if (!tempFile.delete()) {
                tempFile.deleteOnExit();
            }
        }
    }

    private static void attachWithFallback(File agentJarFile, String pid, String agentArgs) {
        try {
            // while the native providers may report to be supported and appear to work properly, in practice there are
            // cases (Docker without '--init' option on some JDK images like 'openjdk:8-jdk-alpine') where the accessor
            // returned by the provider will not work as expected at attachment time.
            ByteBuddyAgent.attach(agentJarFile, pid, agentArgs, ElasticAttachmentProvider.get());
        } catch (RuntimeException e1) {
            try {
                ByteBuddyAgent.attach(agentJarFile, pid, agentArgs, ElasticAttachmentProvider.getFallback());
            } catch (RuntimeException e2) {
                // output the two exceptions for debugging
                SystemStandardOutputLogger.stdErrInfo("Unable to attach with fallback provider:");
                SystemStandardOutputLogger.printStackTrace(e2);

                SystemStandardOutputLogger.stdErrInfo("Unable to attach with regular provider:");
                SystemStandardOutputLogger.printStackTrace(e1);
            }
        }
    }

    /**
     * Attaches the agent to a remote JVM
     *
     * @param pid       the PID of the JVM the agent should be attached on
     * @param agentArgs the agent arguments
     * @deprecated use {@link #attach(String, Map)}
     */
    @Deprecated
    public static void attach(String pid, String agentArgs) {
        attachWithFallback(AgentJarFileHolder.INSTANCE.agentJarFile, pid, agentArgs);
    }

    public static File getBundledAgentJarFile() {
        return AgentJarFileHolder.INSTANCE.agentJarFile;
    }

    private enum AgentJarFileHolder {
        INSTANCE;

        // initializes lazily and ensures its only loaded once
        final File agentJarFile = getAgentJarFile();

        private static File getAgentJarFile() {
            if (ElasticApmAttacher.class.getResource("/elastic-apm-agent.jar") != null) {
                // packaged agent as resource
                return ResourceExtractionUtil.extractResourceToTempDirectory("elastic-apm-agent.jar", "elastic-apm-agent", ".jar").toFile();
            }

            // Running attacher without proper packaging is quite common when running it from the IDE without having
            // it packaged from CLI beforehand
            throw new IllegalStateException("unable to get packaged agent within attacher jar.");
        }
    }

}
