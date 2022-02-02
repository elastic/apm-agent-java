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
import java.util.Objects;
import java.util.Properties;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

public class ServiceInfo {

    private static final String JAR_VERSION_SUFFIX = "-(\\d+\\.)+(\\d+)(.*)?$";
    private static final String DEFAULT_SERVICE_NAME = "unknown-java-service";
    private static final ServiceInfo EMPTY = new ServiceInfo(null, null);
    private static final ServiceInfo AUTO_DETECTED = autoDetect(System.getProperties());

    private final String serviceName;
    @Nullable
    private final String serviceVersion;

    public ServiceInfo(@Nullable String serviceName) {
        this(serviceName, null);
    }

    private ServiceInfo(@Nullable String serviceName, @Nullable String serviceVersion) {
        if (serviceName == null || serviceName.trim().isEmpty()) {
            this.serviceName = DEFAULT_SERVICE_NAME;
        } else {
            this.serviceName = replaceDisallowedServiceNameChars(serviceName).trim();
        }
        this.serviceVersion = serviceVersion;
    }

    public static ServiceInfo empty() {
        return EMPTY;
    }

    public static ServiceInfo of(@Nullable String serviceName) {
        return of(serviceName, null);
    }

    public static ServiceInfo of(@Nullable String serviceName, @Nullable String serviceVersion) {
        if ((serviceName == null || serviceName.isEmpty()) &&
            (serviceVersion == null || serviceVersion.isEmpty())) {
            return empty();
        }
        return new ServiceInfo(serviceName, serviceVersion);
    }

    private static String replaceDisallowedServiceNameChars(String serviceName) {
        return serviceName.replaceAll("[^a-zA-Z0-9 _-]", "-");
    }

    public static ServiceInfo autoDetected() {
        return AUTO_DETECTED;
    }

    public static ServiceInfo autoDetect(Properties properties) {
        String lambdaFunctionName = System.getenv("AWS_LAMBDA_FUNCTION_NAME");
        if (lambdaFunctionName != null) {
            return new ServiceInfo(lambdaFunctionName, null);
        } else {
            ServiceInfo serviceInfo = createFromSunJavaCommand(properties.getProperty("sun.java.command"));
            if (serviceInfo != null) {
                return serviceInfo;
            }
            return ServiceInfo.empty();
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
            return ServiceInfo.of(serviceName);
        }
        if (command.contains(".jar")) {
            return fromJarCommand(command);
        } else {
            return fromMainClassCommand(command);
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

    private static ServiceInfo fromJarCommand(String command) {
        final String[] commandParts = command.split(" ");
        ServiceInfo serviceInfoFromManifest = ServiceInfo.empty();
        ServiceInfo serviceInfoFromJarName = ServiceInfo.empty();
        for (String commandPart : commandParts) {
            if (commandPart.endsWith(".jar")) {
                try (JarFile jarFile = new JarFile(commandPart)) {
                    serviceInfoFromManifest = fromManifest(jarFile.getManifest());
                } catch (Exception ignored) {
                }

                serviceInfoFromJarName = ServiceInfo.of(removeVersionFromJar(removePath(removeJarExtension(commandPart))));
                break;
            }
        }
        return serviceInfoFromManifest.withFallback(serviceInfoFromJarName);
    }

    public static ServiceInfo fromManifest(@Nullable Manifest manifest) {
        if (manifest == null) {
            return ServiceInfo.empty();
        }
        Attributes mainAttributes = manifest.getMainAttributes();
        return ServiceInfo.of(
            mainAttributes.getValue(Attributes.Name.IMPLEMENTATION_TITLE),
            mainAttributes.getValue(Attributes.Name.IMPLEMENTATION_VERSION));
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

    private static ServiceInfo fromMainClassCommand(String command) {
        final String mainClassName;
        int indexOfSpace = command.indexOf(' ');
        if (indexOfSpace != -1) {
            mainClassName = command.substring(0, indexOfSpace);
        } else {
            mainClassName = command;
        }
        return new ServiceInfo(mainClassName.substring(mainClassName.lastIndexOf('.') + 1));
    }

    public String getServiceName() {
        return serviceName;
    }

    @Nullable
    public String getServiceVersion() {
        return serviceVersion;
    }

    public ServiceInfo withFallback(ServiceInfo fallback) {
        return ServiceInfo.of(
            hasServiceName() ? serviceName : fallback.serviceName,
            serviceVersion != null ? serviceVersion : fallback.serviceVersion);
    }

    public boolean hasServiceName() {
        return !serviceName.equals(DEFAULT_SERVICE_NAME);
    }

    public boolean isEmpty() {
        return !hasServiceName() && serviceVersion == null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ServiceInfo that = (ServiceInfo) o;
        return serviceName.equals(that.serviceName) && Objects.equals(serviceVersion, that.serviceVersion);
    }

    @Override
    public int hashCode() {
        return Objects.hash(serviceName, serviceVersion);
    }

    @Override
    public String toString() {
        return "ServiceInfo{" +
            "name='" + serviceName + '\'' +
            ", version='" + serviceVersion + '\'' +
            '}';
    }
}
