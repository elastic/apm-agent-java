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
import co.elastic.apm.servlet.tests.JavaxExternalPluginTestApp;
import co.elastic.apm.servlet.tests.JsfApplicationServerTestApp;
import co.elastic.apm.servlet.tests.ServletApiTestApp;
import co.elastic.apm.servlet.tests.TestApp;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;

import java.util.Arrays;

@RunWith(Parameterized.class)
public class PayaraIT extends AbstractServletContainerIntegrationTest {

    public PayaraIT(final String serverVersion, final String deploymentsFolder) {
        super(new GenericContainer<>(
                new ImageFromDockerfile()
                    .withDockerfileFromBuilder(builder -> {
                            builder
                                .from("payara/server-web:" + serverVersion)
                                .run("sed", "-i", "s#" +
                                        "</java-config>#" +
                                        "<jvm-options>-javaagent:/elastic-apm-agent.jar</jvm-options></java-config>#",
                                    "glassfish/domains/domain1/config/domain.xml")
                                .run("sed", "-i", "s#" +
                                        "</java-config>#" +
                                        "<jvm-options>-Xdebug</jvm-options></java-config>#",
                                    "glassfish/domains/domain1/config/domain.xml");
                            if (ENABLE_DEBUGGING) {
                                builder.run("sed", "-i", "s#" +
                                        "</java-config>#" +
                                        "<jvm-options>-Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=5005</jvm-options></java-config>#",
                                    "glassfish/domains/domain1/config/domain.xml");
                            }
                        }
                    )
            ),
            "glassfish-application",
            deploymentsFolder,
            "payara");
    }

    @Parameterized.Parameters(name = "Payara {0}")
    public static Iterable<Object[]> data() {
        return Arrays.asList(new Object[][]{
            {"4.181", "/opt/payara41/deployments"},
            {"5.182", "/opt/payara5/deployments"}
        });
    }

    @Override
    protected Iterable<Class<? extends TestApp>> getTestClasses() {
        return Arrays.asList(
            ServletApiTestApp.class,
            JsfApplicationServerTestApp.class,
            CdiApplicationServerTestApp.class,
            JavaxExternalPluginTestApp.class
        );
    }
}
