package co.elastic.apm.impl;

import co.elastic.apm.configuration.CoreConfiguration;
import co.elastic.apm.util.VersionUtils;

public class ServiceFactory {

    public Service createService(CoreConfiguration coreConfiguration, String frameworkName, String frameworkVersion) {
        return new Service()
            .withName(coreConfiguration.getServiceName())
            .withVersion(coreConfiguration.getServiceVersion())
            .withEnvironment(coreConfiguration.getEnvironment())
            .withAgent(new Agent("elastic-apm-java", getAgentVersion()))
            .withRuntime(new Runtime("Java", java.lang.System.getProperty("java.version")))
            .withFramework(new Framework(frameworkName, frameworkVersion))
            .withLanguage(new Language("Java", java.lang.System.getProperty("java.version")));
    }

    private String getAgentVersion() {
        return VersionUtils.getVersionFromPomProperties(ServiceFactory.class, "co.elastic.apm", "apm-agent-java");
    }
}
