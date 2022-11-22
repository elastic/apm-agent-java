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

import org.testcontainers.containers.GenericContainer;

import javax.annotation.Nullable;
import java.util.Objects;

public abstract class AbstractTomcatIT extends AbstractServletContainerIntegrationTest {

    public static final String CATALINA_POLICY_FILE_PATH = "/catalina.policy";

    public AbstractTomcatIT(final String tomcatVersion) {
        super(new GenericContainer<>("tomcat:" + tomcatVersion),
            "tomcat-application",
            "/usr/local/tomcat/webapps",
            "tomcat");
    }

    @Override
    protected void enableDebugging(GenericContainer<?> servletContainer) {
        servletContainer.withEnv("CATALINA_OPTS", "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005");
    }

    @Nullable
    protected String getServerLogsPath() {
        return "/usr/local/tomcat/logs/*";
    }

    @Override
    protected boolean runtimeAttachSupported() {
        return true;
    }

    @Override
    protected String getJavaagentEnvVariable() {
        return "CATALINA_OPTS";
    }

    @Nullable
    @Override
    protected String getLocalPolicyFilePath() {
        return Objects.requireNonNull(getClass().getResource(CATALINA_POLICY_FILE_PATH)).getPath();
    }

    @Override
    protected void enableSecurityManager(GenericContainer<?> servletContainer, String policyFilePathWithinContainer) {
        servletContainer.withEnv("JAVA_OPTS", String.format("-Djava.security.manager -Djava.security.debug=failure -Djava.security.policy=%s", policyFilePathWithinContainer));
    }
}
