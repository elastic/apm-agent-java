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
        servletContainer.withEnv("CATALINA_OPTS", "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005");
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
