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
public class WebSphereIT extends AbstractServletContainerIntegrationTest {

    public WebSphereIT(final String version) {
        super((ENABLE_DEBUGGING
                ? new GenericContainer<>(new ImageFromDockerfile()
                .withDockerfileFromBuilder(builder -> builder
                    .from("websphere-liberty:" + version).cmd("/opt/ibm/wlp/bin/server", "debug", "defaultServer")))
                : new GenericContainer<>("websphere-liberty:" + version)
            )
                .withEnv("JVM_ARGS", "-javaagent:/elastic-apm-agent.jar"),
            9080,
            7777,
            "websphere-application",
            "/config/dropins",
            "websphere");
    }

    @Parameterized.Parameters(name = "WebSphere {0}")
    public static Iterable<Object[]> data() {
        return Arrays.asList(new Object[][]{
            {"webProfile6"},
            {"webProfile7"},
            {"webProfile8"},
            {"latest"}
        });
    }

    @Override
    public boolean isExpectedStacktrace(String path) {
        return true;
    }

    @Override
    public boolean isHotSpotBased() {
        return false;
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
