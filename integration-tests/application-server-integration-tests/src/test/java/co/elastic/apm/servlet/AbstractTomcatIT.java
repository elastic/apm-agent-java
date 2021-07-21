package co.elastic.apm.servlet;

import org.testcontainers.containers.GenericContainer;

import javax.annotation.Nullable;

public abstract class AbstractTomcatIT extends AbstractServletContainerIntegrationTest {

    public AbstractTomcatIT(final String tomcatVersion) {
        super(new GenericContainer<>("tomcat:" + tomcatVersion),
            "tomcat-application",
            "/usr/local/tomcat/webapps",
            "tomcat");
    }

    @Override
    protected void enableDebugging(GenericContainer<?> servletContainer) {
        servletContainer
            .withEnv("JPDA_ADDRESS", "5005")
            .withEnv("JPDA_TRANSPORT", "dt_socket");
    }

    @Nullable
    protected String getServerLogsPath() {
        return "/usr/local/tomcat/logs/*";
    }

    @Override
    protected boolean runtimeAttachSupported() {
        return true;
    }

    @Override
    protected String getJavaagentEnvVariable() {
        return "CATALINA_OPTS";
    }
}
