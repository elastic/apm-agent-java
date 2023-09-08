package co.elastic.apm.agent.impl;

import co.elastic.apm.agent.sdk.internal.util.PrivilegedActionUtils;
import co.elastic.apm.agent.tracer.service.ServiceInfo;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.Properties;
import java.util.jar.JarFile;

public class AutoDetectedServiceInfo {

    private static final String JAR_VERSION_SUFFIX = "-(\\d+\\.)+(\\d+)(.*)?$";

    private static final ServiceInfo AUTO_DETECTED = AutoDetectedServiceInfo.autoDetect(System.getProperties(), PrivilegedActionUtils.getEnv());

    public static ServiceInfo autoDetected() {
        return AUTO_DETECTED;
    }

    public static ServiceInfo autoDetect(Properties sysProperties, Map<String,String> sysEnv) {
        String lambdaFunctionName = sysEnv.get("AWS_LAMBDA_FUNCTION_NAME");
        if (lambdaFunctionName != null) {
            return ServiceInfo.of(lambdaFunctionName, sysEnv.get("AWS_LAMBDA_FUNCTION_VERSION"));
        } else {
            ServiceInfo serviceInfo = createFromSunJavaCommand(sysProperties.getProperty("sun.java.command"));
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
            return ServiceInfo.ofMultiServiceContainer(serviceName);
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
                    serviceInfoFromManifest = ServiceInfo.fromManifest(jarFile.getManifest());
                } catch (Exception ignored) {
                }

                serviceInfoFromJarName = ServiceInfo.of(removeVersionFromJar(removePath(removeJarExtension(commandPart))));
                break;
            }
        }
        return serviceInfoFromManifest.withFallback(serviceInfoFromJarName);
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
        return ServiceInfo.of(mainClassName.substring(mainClassName.lastIndexOf('.') + 1));
    }
}
