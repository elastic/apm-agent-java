package co.elastic.apm.impl.payload;

import co.elastic.apm.configuration.CoreConfiguration;
import co.elastic.apm.util.VersionUtils;

public class ServiceFactory {

    public Service createService(CoreConfiguration coreConfiguration, String frameworkName, String frameworkVersion) {
        Service service = new Service()
            .withName(coreConfiguration.getServiceName())
            .withVersion(coreConfiguration.getServiceVersion())
            .withEnvironment(coreConfiguration.getEnvironment())
            .withAgent(new Agent("elastic-apm-java", getAgentVersion()))
            .withRuntime(new Runtime("Java", System.getProperty("java.version")))
            .withLanguage(new Language("Java", System.getProperty("java.version")));
        if (frameworkName != null && frameworkVersion != null) {
            service.withFramework(new Framework(frameworkName, frameworkVersion));
        }
        return service;
    }

    private String getAgentVersion() {
        return VersionUtils.getVersionFromPomProperties(ServiceFactory.class, "co.elastic.apm", "apm-agent-java");
    }
}
