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

import org.jetbrains.annotations.NotNull;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@RunWith(Parameterized.class)
public class JettyIT extends AbstractServletContainerIntegrationTest {

    private String version;

    public JettyIT(final String version) {
        super(new GenericContainer<>("jetty:" + version)
            .withNetwork(Network.SHARED)
            .withEnv("JAVA_OPTIONS", "-javaagent:/elastic-apm-agent.jar -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=5005")
            .withEnv("ELASTIC_APM_SERVER_URL", "http://apm-server:1080")
            .withEnv("ELASTIC_APM_IGNORE_URLS", "/status*,/favicon.ico")
            .withEnv("ELASTIC_APM_REPORT_SYNC", "true")
            .withEnv("ELASTIC_APM_LOGGING_LOG_LEVEL", "DEBUG")
            .withLogConsumer(new StandardOutLogConsumer().withPrefix("jetty"))
            .withFileSystemBind(pathToWar, "/var/lib/jetty/webapps/ROOT.war")
            .withFileSystemBind(pathToJavaagent, "/elastic-apm-agent.jar")
            .withExposedPorts(8080, 9990),
            "jetty-application",
            "/var/lib/jetty/webapps");

        this.version = version;
    }

    @Parameterized.Parameters(name = "Jetty {0}")
    public static Iterable<Object[]> data() {
        return Arrays.asList(new Object[][]{{"9.2"}, {"9.3"}, {"9.4"}});
    }

    @NotNull
    protected List<String> getPathsToTestErrors() {
        return Arrays.asList("/index.jsp", "/servlet", "/async-dispatch-servlet");
    }

    @Override
    protected boolean isExpectedStacktrace(String path) {
        // only from version 9.4 Jetty includes a valid Throwable instance and only in the onComplete
        return version.equals("9.4") || !path.equals("/async-dispatch-servlet");
    }

    @Override
    protected Iterable<TestApp> getTestApps() {
        return Collections.singletonList(TestApp.JSF_STANDALONE);
    }
}
