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
package co.elastic.apm.agent.configuration;

import javax.annotation.Nullable;
import java.util.Properties;
import java.util.jar.Attributes;
import java.util.jar.JarFile;

public class ServiceInfo {

    public static final ServiceInfo DEFAULT = createDefault();
    private static final String JAR_VERSION_SUFFIX = "-(\\d+\\.)+(\\d+)(.*)?$";

    private final String serviceName;
    private final String serviceVersion;

    private ServiceInfo() {
        this(null, null);
    }

    public ServiceInfo(@Nullable String serviceName) {
        this(serviceName, null);
    }

    public ServiceInfo(@Nullable String serviceName, @Nullable String serviceVersion) {
        this.serviceName = serviceName != null && !serviceName.trim().isEmpty() ? replaceDisallowedServiceNameChars(serviceName).trim() : "unknown-java-service";
        this.serviceVersion = serviceVersion;
    }

    public String getServiceName() {
        return serviceName;
    }

    @Nullable
    public String getServiceVersion() {
        return serviceVersion;
    }

    public static String replaceDisallowedServiceNameChars(String serviceName) {
        return serviceName.replaceAll("[^a-zA-Z0-9 _-]", "-");
    }

    public static ServiceInfo createDefault() {
        return createDefault(System.getProperties());
    }

    static ServiceInfo createDefault(Properties properties) {
        String lambdaFunctionName = System.getenv("AWS_LAMBDA_FUNCTION_NAME");
        if (lambdaFunctionName != null) {
            return new ServiceInfo(lambdaFunctionName, null);
        } else {
            ServiceInfo serviceInfo = createFromSunJavaCommand(properties.getProperty("sun.java.command"));
            if (serviceInfo != null) {
                return serviceInfo;
            }
            return new ServiceInfo();
        }
    }

    @Nullable
    private static ServiceInfo createFromSunJavaCommand(@Nullable String command) {
        if (command == null) {
            return null;
        }
        command = command.trim();
        String serviceName = getContainerServiceName(command);
        if (serviceName != null) {
            return new ServiceInfo(serviceName);
        }
        if (command.contains(".jar")) {
            return parseJarCommand(command);
        } else {
            return parseMainClass(command);
        }
    }

    @Nullable
    private static String getContainerServiceName(String command) {
        if (command.startsWith("org.apache.catalina.startup.Bootstrap")) {
            return "tomcat-application";
        } else if (command.startsWith("org.eclipse.jetty")) {
            return "jetty-application";
        } else if (command.startsWith("com.sun.enterprise.glassfish")) {
            return "glassfish-application";
        } else if (command.contains("ws-server.jar")) {
            return "websphere-application";
        } else if (command.contains("jboss-modules.jar")) {
            return "jboss-application";
        } else if (command.contains("weblogic")) {
            return "weblogic-application";
        }
        return null;
    }

    private static ServiceInfo parseJarCommand(String command) {
        final String[] commandParts = command.split(" ");
        String serviceName = null;
        String serviceVersion = null;
        for (String commandPart : commandParts) {
            if (commandPart.endsWith(".jar")) {
                try (JarFile jarFile = new JarFile(commandPart)) {
                    Attributes mainAttributes = jarFile.getManifest().getMainAttributes();
                    serviceName = mainAttributes.getValue(Attributes.Name.IMPLEMENTATION_TITLE);
                    serviceVersion = mainAttributes.getValue(Attributes.Name.IMPLEMENTATION_VERSION);
                } catch (Exception ignored) {
                }

                if (serviceName == null || serviceName.isEmpty()) {
                    serviceName = removeVersionFromJar(removePath(removeJarExtension(commandPart)));
                }
                break;
            }
        }
        return new ServiceInfo(serviceName, serviceVersion);
    }

    private static String removeJarExtension(String commandPart) {
        return commandPart.substring(0, commandPart.indexOf(".jar"));
    }

    private static String removePath(String path) {
        return path.substring(path.lastIndexOf("/") + 1).substring(path.lastIndexOf("\\") + 1);
    }

    private static String removeVersionFromJar(String jarFileName) {
        return jarFileName.replaceFirst(JAR_VERSION_SUFFIX, "");
    }

    private static ServiceInfo parseMainClass(String command) {
        final String mainClassName;
        int indexOfSpace = command.indexOf(' ');
        if (indexOfSpace != -1) {
            mainClassName = command.substring(0, indexOfSpace);
        } else {
            mainClassName = command;
        }
        return new ServiceInfo(mainClassName.substring(mainClassName.lastIndexOf('.') + 1));
    }
}
