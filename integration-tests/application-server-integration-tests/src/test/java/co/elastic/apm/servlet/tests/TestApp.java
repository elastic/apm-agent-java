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
package co.elastic.apm.servlet.tests;

import co.elastic.apm.servlet.AbstractServletContainerIntegrationTest;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

public abstract class TestApp {

    private final String modulePath;
    private final String appFileName;
    private final String statusEndpoint;
    @Nullable
    private final String expectedServiceName;
    private final String deploymentContext;

    TestApp(String modulePath, String appFileName, String deploymentContext, String statusEndpoint, @Nullable String expectedServiceName) {
        this.modulePath = modulePath;
        this.appFileName = appFileName;
        this.statusEndpoint = String.format("/%s/%s", deploymentContext, statusEndpoint);
        this.deploymentContext = deploymentContext;
        this.expectedServiceName = expectedServiceName;
    }

    public String getAppFilePath() {
        return modulePath + "/target/" + getAppFileName();
    }

    public String getAppFileName() {
        return appFileName;
    }

    public String getStatusEndpoint() {
        return statusEndpoint;
    }

    public String getDeploymentContext() {
        return deploymentContext;
    }

    @Nullable
    public String getExpectedServiceName() {
        return expectedServiceName;
    }

    /**
     * Provides a way to bind additional files to the container file system
     *
     * @return a map of file-paths to designated container-paths
     */
    public Map<String, String> getAdditionalFilesToBind() {
        return Collections.emptyMap();
    }

    /**
     * Provides a way for test apps to configure ignored URLs
     *
     * @return a collection of URL paths that will be appended to the {@link #getDeploymentContext() app context}
     */
    public Collection<String> getPathsToIgnore() {
        return Collections.emptyList();
    }

    /**
     * Provides a way to configure additional environment variables for a specific app
     *
     * @return a map of env variable names to values
     */
    public Map<String, String> getAdditionalEnvVariables() {
        return Collections.emptyMap();
    }

    public abstract void test(AbstractServletContainerIntegrationTest test) throws Exception;

}
