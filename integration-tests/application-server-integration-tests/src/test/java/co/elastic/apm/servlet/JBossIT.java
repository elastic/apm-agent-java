/*
 * Licensed to Elasticsearch B.V. under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch B.V. licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package co.elastic.apm.servlet;

import co.elastic.apm.servlet.tests.CdiApplicationServerTestApp;
import co.elastic.apm.servlet.tests.JBossServletApiTestApp;
import co.elastic.apm.servlet.tests.JavaxExternalPluginTestApp;
import co.elastic.apm.servlet.tests.JsfApplicationServerTestApp;
import co.elastic.apm.servlet.tests.SoapTestApp;
import co.elastic.apm.servlet.tests.TestApp;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.testcontainers.containers.GenericContainer;

import java.util.Arrays;

@RunWith(Parameterized.class)
public class JBossIT extends AbstractServletContainerIntegrationTest {

    public JBossIT(final String jbossVersion) {
        super(new GenericContainer<>("registry.access.redhat.com/" + jbossVersion)
                // this overrides the defaults, so we have to manually re-add preferIPv4Stack
                // the other defaults don't seem to be important
                .withEnv("JAVA_OPTS", "-javaagent:/elastic-apm-agent.jar -Djava.net.preferIPv4Stack=true " +
                    "-Djboss.modules.system.pkgs=org.jboss.logmanager,jdk.nashorn.api,com.sun.crypto.provider"),
            "jboss-application",
            "/opt/eap/standalone/deployments",
            "jboss");
    }

    @Parameterized.Parameters(name = "JBoss {0}")
    public static Iterable<Object[]> data() {
        return Arrays.asList(new Object[][]{
            {"jboss-eap-6/eap64-openshift"},
            {"jboss-eap-7/eap70-openshift"},
            {"jboss-eap-7/eap71-openshift"},
            {"jboss-eap-7/eap72-openshift"}
        });
    }

    @Override
    protected void enableDebugging(GenericContainer<?> servletContainer) {
        servletContainer.withEnv("JAVA_OPTS", "-javaagent:/elastic-apm-agent.jar -Djava.net.preferIPv4Stack=true " +
            "-Djboss.modules.system.pkgs=org.jboss.logmanager,jdk.nashorn.api,com.sun.crypto.provider " +
            "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005");
    }

    @Override
    protected Iterable<Class<? extends TestApp>> getTestClasses() {
        return Arrays.asList(
            JBossServletApiTestApp.class,
            JsfApplicationServerTestApp.class,
            SoapTestApp.class,
            CdiApplicationServerTestApp.class,
            JavaxExternalPluginTestApp.class
        );
    }
}
