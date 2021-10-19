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

import co.elastic.apm.servlet.tests.ExternalPluginTestApp;
import co.elastic.apm.servlet.tests.JsfServletContainerTestApp;
import co.elastic.apm.servlet.tests.ServletApiTestApp;
import co.elastic.apm.servlet.tests.TestApp;
import org.junit.runners.Parameterized;
import org.testcontainers.containers.GenericContainer;

import java.util.Arrays;
import java.util.List;

public abstract class AbstractJettyIT extends AbstractServletContainerIntegrationTest {

    private String version;

    public AbstractJettyIT(final String version) {
        super(new GenericContainer<>("jetty:" + version)
                .withExposedPorts(8080),
            "jetty-application",
            "/var/lib/jetty/webapps",
            "jetty");

        this.version = version;
    }

    @Override
    protected void enableDebugging(GenericContainer<?> servletContainer) {
        servletContainer.withEnv("JAVA_OPTIONS", "-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=*:5005");
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
