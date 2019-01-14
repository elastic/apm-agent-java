/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2019 Elastic and contributors
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package co.elastic.apm.agent.configuration;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

class ServiceNameUtil {
    private static final String JAR_VERSION_SUFFIX = "-(\\d+\\.)+(\\d+)(-.*)?$";

    static String getDefaultServiceName() {
        String serviceName = null;
        String sunJavaCommand = System.getProperty("sun.java.command");
        if (sunJavaCommand != null) {
            serviceName = parseSunJavaCommand(sunJavaCommand);
        }
        if (serviceName == null) {
            serviceName = "my-service";
        }
        return serviceName;
    }

    @Nullable
    static String parseSunJavaCommand(String command) {
        String serviceName = getSpecialServiceName(command);
        if (serviceName != null) {
            return serviceName;
        }
        if (command.contains(".jar")) {
            serviceName = parseJarCommand(command);
        } else {
            serviceName = parseMainClass(command);
        }
        if (serviceName != null) {
            return replaceDisallowedChars(serviceName);
        }
        return null;
    }

    @Nullable
    private static String getSpecialServiceName(String command) {
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
        }
        return null;
    }

    private static String replaceDisallowedChars(String serviceName) {
        return serviceName.replaceAll("[^a-zA-Z0-9 _-]", "-");
    }

    @Nullable
    private static String parseJarCommand(String command) {
        final String[] commandParts = command.split(" ");
        for (String commandPart : commandParts) {
            if (commandPart.endsWith(".jar")) {
                return removeVersionFromJar(removePath(removeJarExtension(commandPart)));
            }
        }
        return null;
    }

    @Nonnull
    private static String removeJarExtension(String commandPart) {
        return commandPart.substring(0, commandPart.indexOf(".jar"));
    }

    private static String removePath(String path) {
        return path.substring(path.lastIndexOf("/") + 1).substring(path.lastIndexOf("\\") + 1);
    }

    private static String removeVersionFromJar(String jarFileName) {
        return jarFileName.replaceFirst(JAR_VERSION_SUFFIX, "");
    }

    private static String parseMainClass(String command) {
        final String mainClassName;
        int indexOfSpace = command.indexOf(' ');
        if (indexOfSpace != -1) {
            mainClassName = command.substring(0, indexOfSpace);
        } else {
            mainClassName = command;
        }
        return mainClassName.substring(mainClassName.lastIndexOf('.') + 1);
    }
}
