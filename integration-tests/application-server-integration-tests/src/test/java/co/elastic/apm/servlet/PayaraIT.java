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
import co.elastic.apm.servlet.tests.CdiApplicationServerTestApp;
import co.elastic.apm.servlet.tests.JavaxExternalPluginTestApp;
import co.elastic.apm.servlet.tests.JsfApplicationServerTestApp;
import co.elastic.apm.servlet.tests.ServletApiTestApp;
import co.elastic.apm.servlet.tests.TestApp;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.testcontainers.images.builder.ImageFromDockerfile;

import java.util.Arrays;
import java.util.concurrent.Future;

@RunWith(Parameterized.class)
public class PayaraIT extends AbstractServletContainerIntegrationTest {

    public PayaraIT(String serverVersion, String homeFolder) {
        super(AgentTestContainer.appServer(getImage(serverVersion, homeFolder))
                .withContainerName("payara")
                .withHttpPort(8080)
                .withJvmArgumentsVariable("ELASTIC_JVM_ARGUMENTS")
                .withDeploymentPath(homeFolder + "/deployments"),
            "glassfish-application");
    }

    private static Future<String> getImage(String serverVersion, String homeFolder) {
        return new ImageFromDockerfile()
            .withDockerfileFromBuilder(builder -> {
                builder.from("payara/server-web:" + serverVersion)
                    .user("root")
                    .run("/bin/bash", "-x", "-c",
                        "sed -i '/^#!/a " +
                            // command that is added just after #!/bin/bash and will modify domain config on startup
                            "sed -i \"s#" +
                            "</java-config>" +
                            "#" +
                            // using -DignoreMe in JVM command when arguments are not provided to avoid empty jvm-options tag
                            "<jvm-options>${ELASTIC_JVM_ARGUMENTS:--DignoreMe}</jvm-options></java-config>" +
                            "#\" " +
                            // configuration file
                            homeFolder + "/glassfish/domains/domain1/config/domain.xml' " +
                            // startup script that is modified to inject the command above
                            homeFolder + "/bin/startInForeground.sh")
                    .user("payara");
            });
    }

    @Parameterized.Parameters(name = "Payara {0}")
    public static Iterable<Object[]> data() {
        return Arrays.asList(new Object[][]{
            {"4.181", "/opt/payara41"},
            {"5.182", "/opt/payara5"}
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
