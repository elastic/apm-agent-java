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
            "websphere-application",
            "/config/dropins",
            "websphere");
    }

    @Parameterized.Parameters(name = "WebSphere {0}")
    public static Iterable<Object[]> data() {
        return Arrays.asList(new Object[][]{{"8.5.5"}, {"webProfile7"}});
    }

    @Override
    protected void enableDebugging(GenericContainer<?> servletContainer) {
        servletContainer.withEnv("JVM_ARGS", "-javaagent:/elastic-apm-agent.jar -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=5005");
    }

    @Override
    protected boolean isExpectedStacktrace(String path) {
        return true;
    }

    @Override
    protected Iterable<Class<? extends TestApp>> getTestClasses() {
        return Arrays.asList(ServletApiTestApp.class, JsfApplicationServerTestApp.class);
    }
}
