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
import java.util.jar.Attributes;
import java.util.jar.JarFile;

public class ServiceVersionUtil {

    public static String getDefaultServiceVersion() {
        return getDefaultServiceVersion(System.getProperty("sun.java.command"));
    }

    private static String getDefaultServiceVersion(@Nullable String command) {
        if (command == null || !command.contains(".jar") || isContainerCommand(command)) {
            return null;
        }

        for (String commandPart : command.split(" ")) {
            if (commandPart.endsWith(".jar")) {
                try (JarFile jarFile = new JarFile(commandPart)) {
                    String serviceVersion = jarFile.getManifest().getMainAttributes().getValue(Attributes.Name.IMPLEMENTATION_VERSION);
                    if (serviceVersion != null && !serviceVersion.isEmpty()) {
                        return serviceVersion;
                    }
                } catch (Exception ignored) {
                }

                break;
            }
        }

        return null;
    }

    private static boolean isContainerCommand(String command) {
        return command.startsWith("org.apache.catalina.startup.Bootstrap")
            || command.startsWith("org.eclipse.jetty")
            || command.startsWith("com.sun.enterprise.glassfish")
            || command.contains("ws-server.jar")
            || command.contains("jboss-modules.jar")
            || command.contains("weblogic");
    }
}
