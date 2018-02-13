package co.elastic.apm.intake;

import co.elastic.apm.intake.errors.Agent;
import co.elastic.apm.intake.errors.Framework;
import co.elastic.apm.intake.errors.Language;
import co.elastic.apm.intake.errors.Runtime;
import co.elastic.apm.util.VersionUtils;

public class ServiceFactory {

    public Service createService(String frameworkName, String frameworkVersion) {
        return new Service()
            .withName("java-test") // TODO config option
            //.withVersion("1.0") TODO config option
            //.withEnvironment("test") TODO config option
            .withAgent(new Agent().withName("elastic-apm-java").withVersion(getAgentVersion()))
            .withRuntime(new Runtime().withName("Java").withVersion(java.lang.System.getProperty("java.version")))
            .withFramework(new Framework().withName(frameworkName).withVersion(frameworkVersion))
            .withLanguage(new Language().withName("Java").withVersion(java.lang.System.getProperty("java.version")));
    }

    private String getAgentVersion() {
        return VersionUtils.getVersionFromPomProperties(ServiceFactory.class, "co.elastic.apm", "apm-agent-java");
    }
}
