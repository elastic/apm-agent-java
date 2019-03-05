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
package co.elastic.apm.servlet.tests;

import co.elastic.apm.servlet.AbstractServletContainerIntegrationTest;

import javax.annotation.Nullable;

public abstract class TestApp {

    private final String modulePath;
    private final String appFileName;
    private final String statusEndpoint;
    @Nullable
    private final String expectedServiceName;

    TestApp(String modulePath, String appFileName, String statusEndpoint, @Nullable String expectedServiceName) {
        this.modulePath = modulePath;
        this.appFileName = appFileName;
        this.statusEndpoint = statusEndpoint;
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

    @Nullable
    public String getExpectedServiceName() {
        return expectedServiceName;
    }

    public abstract void test(AbstractServletContainerIntegrationTest test) throws Exception;

}
