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

import co.elastic.apm.agent.test.AgentTestContainer;
import co.elastic.apm.servlet.tests.*;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;

import java.util.Arrays;

@RunWith(Parameterized.class)
public class JBossIT extends AbstractServletContainerIntegrationTest {

    public JBossIT(final String jbossImage, boolean preserveDefaults) {
        super(jbossContainer(jbossImage, preserveDefaults),
            "jboss-application");
    }

    private static AgentTestContainer.AppServer jbossContainer(String image, boolean preserveDefaults) {
        AgentTestContainer.AppServer jboss = AgentTestContainer.appServer(image)
            .withContainerName("jboss")
            .withHttpPort(8080)
            // set JVM arguments through JAVA_OPTS
            .withJvmArgumentsVariable("JAVA_OPTS")

            .withDeploymentPath("/opt/eap/standalone/deployments")
            .waitingFor(new HttpWaitStrategy().forPort(8080).forStatusCode(200));

        if (preserveDefaults) {
            // for older versions setting JAVA_OPTS means overwriting the defaults
            // so we have to manually re-add some of them like 'preferIPv4Stack' and 'jboss.modules.system.pkgs'
            // other defaults do not seem to impact test thus we ignore them
            jboss.withSystemProperty("java.net.preferIPv4Stack", "true")
                // the other defaults don't seem to be important
                .withSystemProperty("jboss.modules.system.pkgs", "org.jboss.logmanager,jdk.nashorn.api,com.sun.crypto.provider");
        }

        return jboss;
    }

    @Parameterized.Parameters(name = "JBoss {0}")
    public static Iterable<Object[]> data() {
        // When running in GitHub actions if a new docker image is added, please
        // update the list of these docker images in .ci/scripts/jboss-docker-images.txt
        // then you can run .ci/scripts/jboss-upload.sh to upload these new docker images
        // to the internal docker registry.
        return Arrays.asList(new Object[][]{
            {"registry.redhat.io/jboss-eap-7/eap70-openshift:1.7", true},
            {"registry.access.redhat.com/jboss-eap-7/eap71-openshift", true},
            {"registry.access.redhat.com/jboss-eap-7/eap72-openshift", true},
            {"registry.redhat.io/jboss-eap-7/eap73-openjdk11-openshift-rhel8:7.3.10", true},
            {"registry.redhat.io/jboss-eap-7/eap74-openjdk11-openshift-rhel8:7.4.0", false},
            {"registry.redhat.io/jboss-eap-7/eap74-openjdk17-openshift-rhel8:7.4.14", false},
        });
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
