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

import java.util.Arrays;

@RunWith(Parameterized.class)
public class WebSphereIT extends AbstractServletContainerIntegrationTest {

    public WebSphereIT(String version) {
        super(AgentTestContainer.appServer("websphere-liberty:" + version)
                .withContainerName("websphere")
                .withHttpPort(9080)
                .withDeploymentPath("/config/dropins")
                .withJvmArgumentsVariable("JVM_ARGS"),
            "websphere-application");
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
