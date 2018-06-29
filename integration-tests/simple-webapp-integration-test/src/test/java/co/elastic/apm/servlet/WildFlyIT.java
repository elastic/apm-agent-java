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

import java.util.Arrays;

@RunWith(Parameterized.class)
public class WildFlyIT extends AbstractServletContainerIntegrationTest {

    public WildFlyIT(final String wildFlyVersion) {
        super(new GenericContainer<>("jboss/wildfly:" + wildFlyVersion)
            .withNetwork(Network.SHARED)
            // this overrides the defaults, so we have to manually re-add preferIPv4Stack
            // the other defaults don't seem to be important
            .withEnv("JAVA_OPTS", "-javaagent:/elastic-apm-agent.jar -Djava.net.preferIPv4Stack=true")
            .withEnv("ELASTIC_APM_SERVER_URL", "http://apm-server:1080")
            .withEnv("ELASTIC_APM_SERVICE_NAME", "servlet-test-app")
            .withEnv("ELASTIC_APM_IGNORE_URLS", "/status*,/favicon.ico")
            .withEnv("ELASTIC_APM_REPORT_SYNC", "true")
            .withEnv("ELASTIC_APM_LOGGING_LOG_LEVEL", "DEBUG")
            .withLogConsumer(new StandardOutLogConsumer().withPrefix("wildfly"))
            .withFileSystemBind(pathToWar, "/opt/jboss/wildfly/standalone/deployments/ROOT.war")
            .withFileSystemBind(pathToJavaagent, "/elastic-apm-agent.jar")
            .withExposedPorts(8080, 9990));
    }

    @Parameterized.Parameters(name = "Wildfly {0}")
    public static Iterable<Object[]> data() {
        return Arrays.asList(new Object[][]{
            {"8.2.1.Final"},
            {"9.0.0.Final"},
            {"10.0.0.Final"},
            {"11.0.0.Final"},
            {"12.0.0.Final"},
            {"13.0.0.Final"}
        });
    }
}
