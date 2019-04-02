/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2019 Elastic and contributors
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package co.elastic.apm.servlet;

import co.elastic.apm.servlet.tests.JsfApplicationServerTestApp;
import co.elastic.apm.servlet.tests.ServletApiTestApp;
import co.elastic.apm.servlet.tests.SoapTestApp;
import co.elastic.apm.servlet.tests.TestApp;
import org.jetbrains.annotations.NotNull;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;

import java.util.Arrays;
import java.util.List;

@RunWith(Parameterized.class)
public class WebLogicIT extends AbstractServletContainerIntegrationTest {

    public WebLogicIT(final String webLogicVersion) {
        super(new GenericContainer<>("store/oracle/weblogic:" + webLogicVersion)
                .withEnv("EXTRA_JAVA_PROPERTIES", "-javaagent:/elastic-apm-agent.jar")
                .withClasspathResourceMapping("domain.properties", "/u01/oracle/properties/domain.properties", BindMode.READ_WRITE),
            7001,
            "weblogic-application",
            "/u01/oracle/user_projects/domains/base_domain/autodeploy",
            "weblogic");
    }

    @Parameterized.Parameters(name = "WebLogic {0}")
    public static Iterable<Object[]> data() {
        return Arrays.asList(new Object[][]{
            {"12.2.1.3-dev"}
        });
    }

    @Override
    protected void enableDebugging(GenericContainer<?> servletContainer) {
        servletContainer.withEnv("EXTRA_JAVA_PROPERTIES", "-javaagent:/elastic-apm-agent.jar " +
            "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005");
    }

    @Override
    protected Iterable<Class<? extends TestApp>> getTestClasses() {
        return Arrays.asList(ServletApiTestApp.class, JsfApplicationServerTestApp.class, SoapTestApp.class);
    }

    @Override
    public @NotNull List<String> getPathsToTestErrors() {
        // WebLogic can't handle the case when a exception is thrown in the runnable submitted to AsyncContext#start(Runnable)
        // it requires AsyncContext#complete() to be called, otherwise it throws a timeout
        return List.of("/index.jsp", "/servlet", "/async-dispatch-servlet");
    }

    @Override
    protected boolean isDeployViaFileSystemBind() {
        // WebLogic requires the files to be writable
        // Even binding with BindMode.READ_WRITE does not work for some reason
        return false;
    }
}
