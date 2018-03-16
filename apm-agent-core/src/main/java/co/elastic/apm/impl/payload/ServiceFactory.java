package co.elastic.apm.impl.payload;

import co.elastic.apm.configuration.CoreConfiguration;
import co.elastic.apm.util.VersionUtils;

import javax.annotation.Nullable;

public class ServiceFactory {

    public Service createService(CoreConfiguration coreConfiguration, @Nullable String frameworkName, @Nullable String frameworkVersion) {
        Service service = new Service()
            .withName(coreConfiguration.getServiceName())
            .withVersion(coreConfiguration.getServiceVersion())
            .withEnvironment(coreConfiguration.getEnvironment())
            .withAgent(new Agent("elastic-apm-java", getAgentVersion()))
            .withRuntime(new RuntimeInfo("Java", System.getProperty("java.version")))
            .withLanguage(new Language("Java", System.getProperty("java.version")));
        if (frameworkName != null && frameworkVersion != null) {
            service.withFramework(new Framework(frameworkName, frameworkVersion));
        }
        return service;
    }

    private String getAgentVersion() {
        String version = VersionUtils.getVersionFromPomProperties(ServiceFactory.class, "co.elastic.apm", "apm-agent-java");
        if (version == null) {
            return "unknown";
        }
        return version;
    }
}
