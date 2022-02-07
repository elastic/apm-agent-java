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

import co.elastic.apm.servlet.tests.CdiServletContainerTestApp;
import co.elastic.apm.servlet.tests.JavaxExternalPluginTestApp;
import co.elastic.apm.servlet.tests.JsfServletContainerTestApp;
import co.elastic.apm.servlet.tests.ServletApiTestApp;
import co.elastic.apm.servlet.tests.TestApp;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@RunWith(Parameterized.class)
public class TomcatIT extends AbstractTomcatIT {

    public TomcatIT(final String tomcatVersion) {
        super(tomcatVersion);
    }

    @Parameterized.Parameters(name = "Tomcat {0}")
    public static Iterable<Object[]> data() {
        return Arrays.asList(new Object[][]{
            {"7-jre7-slim"},
            {"8.5.0-jre8"},
            {"8.5-jre8-slim"},
            {"9-jre9-slim"},
            {"9-jre10-slim"},
            {"9-jre11-slim"},
            {"9.0.39-jdk14-openjdk-oracle"},
            {"jdk8-adoptopenjdk-openj9"},
            {"jdk11-adoptopenjdk-openj9"},
            {"9.0.50-jdk11-adoptopenjdk-openj9"}
        });
    }

    @Override
    protected Iterable<Class<? extends TestApp>> getTestClasses() {
        List<Class<? extends TestApp>> testClasses = new ArrayList<>();
        testClasses.add(ServletApiTestApp.class);
        testClasses.add(CdiServletContainerTestApp.class);
        testClasses.add(JavaxExternalPluginTestApp.class);
        if (!getImageName().contains("jre7")) {
            // The JSF test app depends on myfaces 2.3.2 which requires Java 8 or higher
            testClasses.add(JsfServletContainerTestApp.class);
        }
        return testClasses;
    }
}
