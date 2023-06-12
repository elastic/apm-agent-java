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
import org.testcontainers.utility.MountableFile;

import javax.annotation.Nullable;
import java.net.URL;
import java.nio.file.Paths;
import java.util.Objects;

public abstract class AbstractTomcatIT extends AbstractServletContainerIntegrationTest {

    public static final String CATALINA_POLICY_FILE_PATH = "/catalina.policy";

    public AbstractTomcatIT(String tomcatVersion) {
        super(AgentTestContainer.appServer("tomcat:" + tomcatVersion)
                .withContainerName("tomcat")
                .withDeploymentPath("/usr/local/tomcat/webapps")
                .withHttpPort(8080)
                .withLogsPath("/usr/local/tomcat/logs/")
                .withJvmArgumentsVariable("CATALINA_OPTS"), // using CATALINA_OPTS to configure JVM arguments,
            "tomcat-application"
        );
    }

    @Override
    protected boolean runtimeAttachSupported() {
        return true;
    }

    protected boolean isSecurityManagerEnabled() {
        return false;
    }

    @Override
    protected void beforeContainerStart(AgentTestContainer.AppServer container) {
        if (isSecurityManagerEnabled()) {
            // copy policy and enable security manager
            container.withSecurityManager(MountableFile.forClasspathResource(CATALINA_POLICY_FILE_PATH));
        }

    }


}
