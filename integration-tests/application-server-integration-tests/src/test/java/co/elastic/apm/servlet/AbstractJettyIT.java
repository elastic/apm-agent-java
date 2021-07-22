package co.elastic.apm.servlet;

import co.elastic.apm.servlet.tests.ExternalPluginTestApp;
import co.elastic.apm.servlet.tests.JsfServletContainerTestApp;
import co.elastic.apm.servlet.tests.ServletApiTestApp;
import co.elastic.apm.servlet.tests.TestApp;
import org.junit.runners.Parameterized;
import org.testcontainers.containers.GenericContainer;

import java.util.Arrays;
import java.util.List;

public abstract class AbstractJettyIT extends AbstractServletContainerIntegrationTest {

    private String version;

    public AbstractJettyIT(final String version) {
        super(new GenericContainer<>("jetty:" + version)
                .withExposedPorts(8080),
            "jetty-application",
            "/var/lib/jetty/webapps",
            "jetty");

        this.version = version;
    }

    @Override
    protected void enableDebugging(GenericContainer<?> servletContainer) {
        servletContainer.withEnv("JAVA_OPTIONS", "-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=*:5005");
    }

    public List<String> getPathsToTestErrors() {
        return Arrays.asList("/index.jsp", "/servlet", "/async-dispatch-servlet");
    }

    @Override
    public boolean isExpectedStacktrace(String path) {
        return !path.equals("/async-dispatch-servlet");
    }

    @Override
    protected boolean runtimeAttachSupported() {
        return true;
    }
}
