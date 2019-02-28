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
package co.elastic.apm.servlet;

import co.elastic.apm.servlet.tests.JsfServletContainerTestApp;
import co.elastic.apm.servlet.tests.ServletApiTestApp;
import co.elastic.apm.servlet.tests.TestApp;
import org.jetbrains.annotations.NotNull;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.testcontainers.containers.GenericContainer;

import java.util.Arrays;
import java.util.List;

@RunWith(Parameterized.class)
public class JettyIT extends AbstractServletContainerIntegrationTest {

    private String version;

    public JettyIT(final String version) {
        super(new GenericContainer<>("jetty:" + version)
                .withEnv("JAVA_OPTIONS", "-javaagent:/elastic-apm-agent.jar")
                .withEnv("ELASTIC_APM_SERVER_URL", "http://apm-server:1080")
                .withEnv("ELASTIC_APM_IGNORE_URLS", "/status*,/favicon.ico")
                .withEnv("ELASTIC_APM_REPORT_SYNC", "true")
                .withEnv("ELASTIC_APM_LOGGING_LOG_LEVEL", "DEBUG")
                .withExposedPorts(8080),
            "jetty-application",
            "/var/lib/jetty/webapps",
            "jetty");

        this.version = version;
    }

    @Parameterized.Parameters(name = "Jetty {0}")
    public static Iterable<Object[]> data() {
        return Arrays.asList(new Object[][]{{"9.2"}, {"9.3"}, {"9.4"}});
    }

    @Override
    protected void enableDebugging(GenericContainer<?> servletContainer) {
        servletContainer.withEnv("JAVA_OPTIONS", "-javaagent:/elastic-apm-agent.jar -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5005");
    }

    @NotNull
    public List<String> getPathsToTestErrors() {
        return Arrays.asList("/index.jsp", "/servlet", "/async-dispatch-servlet");
    }

    @Override
    public boolean isExpectedStacktrace(String path) {
        // only from version 9.4 Jetty includes a valid Throwable instance and only in the onComplete
        return version.equals("9.4") || !path.equals("/async-dispatch-servlet");
    }

    @Override
    protected Iterable<Class<? extends TestApp>> getTestClasses() {
        return Arrays.asList(ServletApiTestApp.class, JsfServletContainerTestApp.class);
    }
}
