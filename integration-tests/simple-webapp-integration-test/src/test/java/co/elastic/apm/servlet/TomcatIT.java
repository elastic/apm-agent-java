/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 Elastic and contributors
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

import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;

import javax.annotation.Nullable;
import java.util.Arrays;

/**
 * When you want to execute the test in the IDE, execute {@code mvn clean package} before.
 * This creates the {@code ROOT.war} file,
 * which is bound into the docker container.
 * <p>
 * Whenever you make changes to the application,
 * you have to rerun {@code mvn clean package}.
 * </p>
 * <p>
 * To debug simple-webapp which is deployed to tomcat,
 * add a break point in {@link super#AbstractServletContainerIntegrationTest(GenericContainer, int, String)} and evaluate
 * tomcatContainer.getMappedPort(8000).
 * Then create a remote debug configuration in IntelliJ using this port and start debugging.
 * TODO use {@link org.testcontainers.containers.SocatContainer} to always have the same debugging port
 * </p>
 */
@RunWith(Parameterized.class)
public class TomcatIT extends AbstractServletContainerIntegrationTest {

    public TomcatIT(final String tomcatVersion) {
        super(new GenericContainer<>("tomcat:" + tomcatVersion)
            .withNetwork(Network.SHARED)
            .withEnv("JPDA_ADDRESS", "8000")
            .withEnv("JPDA_TRANSPORT", "dt_socket")
            .withEnv("CATALINA_OPTS", "-javaagent:/elastic-apm-agent.jar")
            .withEnv("ELASTIC_APM_SERVER_URL", "http://apm-server:1080")
            .withEnv("ELASTIC_APM_SERVICE_NAME", "servlet-test-app")
            .withEnv("ELASTIC_APM_IGNORE_URLS", "/status*,/favicon.ico")
            .withEnv("ELASTIC_APM_REPORT_SYNC", "true")
            .withEnv("ELASTIC_APM_LOGGING_LOG_LEVEL", "DEBUG")
            .withLogConsumer(new StandardOutLogConsumer().withPrefix("tomcat"))
            .withFileSystemBind(pathToWar, "/usr/local/tomcat/webapps/simple-webapp.war")
            .withFileSystemBind(pathToJavaagent, "/elastic-apm-agent.jar")
            .withExposedPorts(8080, 8000), 8080, "/simple-webapp");
    }

    @Parameterized.Parameters(name = "Tomcat {0}")
    public static Iterable<Object[]> data() {
        return Arrays.asList(new Object[][]{{"7-jre7-slim"}, {"8.5-jre8-slim"}, {"9-jre9-slim"}, {"9-jre10-slim"}});
    }

    @Nullable
    protected String getServerLogsPath() {
        return "/usr/local/tomcat/logs/*";
    }

}
