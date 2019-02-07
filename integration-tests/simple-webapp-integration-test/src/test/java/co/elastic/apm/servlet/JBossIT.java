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

import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;

import java.util.Arrays;
import java.util.Collections;

@RunWith(Parameterized.class)
public class JBossIT extends AbstractServletContainerIntegrationTest {

    public JBossIT(final String jbossVersion) {
        super(new GenericContainer<>("registry.access.redhat.com/jboss-eap-7/" + jbossVersion)
            .withNetwork(Network.SHARED)
            // this overrides the defaults, so we have to manually re-add preferIPv4Stack
            // the other defaults don't seem to be important
            .withEnv("JAVA_OPTS", "-javaagent:/elastic-apm-agent.jar " +
                "-Djboss.modules.system.pkgs=org.jboss.logmanager,jdk.nashorn.api,com.sun.crypto.provider")
            .withEnv("ELASTIC_APM_SERVER_URL", "http://apm-server:1080")
            .withEnv("ELASTIC_APM_IGNORE_URLS", "/status*,/favicon.ico")
            .withEnv("ELASTIC_APM_REPORT_SYNC", "true")
            .withEnv("ELASTIC_APM_LOGGING_LOG_LEVEL", "DEBUG")
            .withLogConsumer(new StandardOutLogConsumer().withPrefix("jboss"))
            .withFileSystemBind(pathToWar, "/opt/eap/standalone/deployments/ROOT.war")
            .withFileSystemBind(pathToJavaagent, "/elastic-apm-agent.jar")
            .withExposedPorts(8080, 9990),
            "jboss-application",
            "/opt/eap/standalone/deployments");
    }

    @Override
    protected void enableDebugging(GenericContainer<?> servletContainer) {
        servletContainer.withEnv("JAVA_OPTS", "-javaagent:/elastic-apm-agent.jar -Djava.net.preferIPv4Stack=true " +
            "-Djboss.modules.system.pkgs=org.jboss.logmanager,jdk.nashorn.api,com.sun.crypto.provider " +
            "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005");
    }

    @Parameterized.Parameters(name = "JBoss {0}")
    public static Iterable<Object[]> data() {
        return Arrays.asList(new Object[][]{
            {"eap70-openshift"},
            {"eap71-openshift"},
            {"eap72-openshift"}
        });
    }

    @Override
    protected Iterable<TestApp> getTestApps() {
        return Collections.singletonList(TestApp.JSF);
    }
}
