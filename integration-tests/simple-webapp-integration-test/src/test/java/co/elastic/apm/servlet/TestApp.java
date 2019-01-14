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

import com.fasterxml.jackson.databind.JsonNode;
import org.mockserver.model.ClearType;
import org.mockserver.model.HttpRequest;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

import static co.elastic.apm.servlet.AbstractServletContainerIntegrationTest.mockServerContainer;

public enum TestApp {
    JSF("../jsf-http-get", "jsf-http-get.war", TestApp::testJsf);

    TestApp(String modulePath, String appFileName, Consumer<AbstractServletContainerIntegrationTest> testMethod) {
        this.modulePath = modulePath;
        this.appFileName = appFileName;
        this.testMethod = testMethod;
    }

    String modulePath;
    String appFileName;
    Consumer<AbstractServletContainerIntegrationTest> testMethod;

    String getAppFilePath() {
        return modulePath + "/target/" + appFileName;
    }

    private static void testJsf(AbstractServletContainerIntegrationTest containerIntegrationTest) {
        try {
            testJsfRequest(containerIntegrationTest, "/faces/index.xhtml", "");
            testJsfRequest(containerIntegrationTest, "/faces/login.xhtml", "?name=Jack");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void testJsfRequest(AbstractServletContainerIntegrationTest containerIntegrationTest, String testedPath,
                                       String additionalPathInfo) throws IOException, InterruptedException {
        String viewPath = "/jsf-http-get" + testedPath;
        String fullTestPath = viewPath + additionalPathInfo;

        // warmup required, may result in 404 at the first time...
        containerIntegrationTest.executeRequest(fullTestPath);
        mockServerContainer.getClient().clear(HttpRequest.request(), ClearType.LOG);
        Thread.sleep(1000);

        containerIntegrationTest.executeAndValidateRequest(fullTestPath, "HTTP GET", 200);
        JsonNode transaction = containerIntegrationTest.assertTransactionReported(viewPath, 200);
        assertThat(transaction.get("name").textValue()).isEqualTo(testedPath);
        String transactionId = transaction.get("id").textValue();
        List<JsonNode> spans = containerIntegrationTest.assertSpansTransactionId(
            500,
            containerIntegrationTest::getReportedSpans,
            transactionId);
        Iterator<JsonNode> iterator = spans.iterator();
        JsonNode executeSpan = iterator.next();
        assertThat(executeSpan.get("name").textValue()).isEqualTo("JSF Execute");
        JsonNode renderSpan = iterator.next();
        assertThat(renderSpan.get("name").textValue()).isEqualTo("JSF Render");
    }
}
