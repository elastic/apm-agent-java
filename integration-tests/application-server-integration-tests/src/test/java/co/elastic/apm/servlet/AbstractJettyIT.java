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

import java.util.Arrays;
import java.util.List;

public abstract class AbstractJettyIT extends AbstractServletContainerIntegrationTest {

    public AbstractJettyIT(final String version) {
        super(AgentTestContainer.appServer("jetty:" + version)
                .withContainerName("jetty")
                .withHttpPort(8080)
                .withDeploymentPath("/var/lib/jetty/webapps")
                .withJvmArgumentsVariable("JAVA_OPTIONS"),
            "jetty-application");

    }

    public List<String> getPathsToTestErrors() {
        return Arrays.asList("/index.jsp", "/servlet", "/async-dispatch-servlet");
    }

    @Override
    public boolean isExpectedStacktrace(String path) {
        return !path.equals("/async-dispatch-servlet");
    }

    @Override
    protected boolean runtimeAttachSupported() {
        return true;
    }
}
