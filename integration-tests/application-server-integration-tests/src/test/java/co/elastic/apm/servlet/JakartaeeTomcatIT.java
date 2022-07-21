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

import co.elastic.apm.servlet.tests.CdiJakartaeeServletContainerTestApp;
import co.elastic.apm.servlet.tests.JakartaExternalPluginTestApp;
import co.elastic.apm.servlet.tests.JakartaeeJsfServletContainerTestApp;
import co.elastic.apm.servlet.tests.JakartaeeServletApiTestApp;
import co.elastic.apm.servlet.tests.TestApp;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;

@RunWith(Parameterized.class)
public class JakartaeeTomcatIT extends AbstractTomcatIT {

    public JakartaeeTomcatIT(final String tomcatVersion) {
        super(tomcatVersion);
    }

    @Parameterized.Parameters(name = "Tomcat {0}")
    public static Iterable<Object[]> data() {
        return Arrays.asList(new Object[][]{
            {"10.0.10-jdk8"},
            {"10.0.10-jdk11"},
            {"10.0.10-jdk8-adoptopenjdk-openj9"},
            {"10.0.10-jdk11-adoptopenjdk-openj9"},
            {"10.0.10-jdk8-adoptopenjdk-hotspot"},
            {"10.0.10-jdk11-adoptopenjdk-hotspot"},
            {"10.1.0-jdk17-temurin"}, // Servlet 6.x
        });
    }

    @Override
    protected Iterable<Class<? extends TestApp>> getTestClasses() {
        return Arrays.asList(JakartaeeServletApiTestApp.class,
            JakartaeeJsfServletContainerTestApp.class,
            JakartaExternalPluginTestApp.class,
            CdiJakartaeeServletContainerTestApp.class);
    }
}
