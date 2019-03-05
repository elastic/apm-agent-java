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
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class ServletApiTestApp extends TestApp {

    public ServletApiTestApp() {
        super("../simple-webapp", "simple-webapp.war", "/simple-webapp/status.jsp", "Simple Web App");
    }

    @Override
    public void test(AbstractServletContainerIntegrationTest test) throws Exception {
        testTransactionReporting(test);
        testTransactionErrorReporting(test);
        testSpanErrorReporting(test);
        testExecutorService(test);
        testHttpUrlConnection(test);
    }

    private void testExecutorService(AbstractServletContainerIntegrationTest test) throws Exception {
        test.clearMockServerLog();
        final String pathToTest = "/simple-webapp/executor-service-servlet";
        test.executeAndValidateRequest(pathToTest, null, 200);
        String transactionId = test.assertTransactionReported(pathToTest, 200).get("id").textValue();
        final List<JsonNode> spans = test.assertSpansTransactionId(500, test::getReportedSpans, transactionId);
        assertThat(spans).hasSize(1);
    }

    private void testHttpUrlConnection(AbstractServletContainerIntegrationTest test) throws IOException, InterruptedException {
        test.clearMockServerLog();
        final String pathToTest = "/simple-webapp/http-url-connection";
        test.executeAndValidateRequest(pathToTest, "Hello World!", 200);

        final List<JsonNode> reportedTransactions = test.getAllReported(500, test::getReportedTransactions, 2);
        final JsonNode innerTransaction = reportedTransactions.get(0);
        final JsonNode outerTransaction = reportedTransactions.get(1);

        final List<JsonNode> spans = test.assertSpansTransactionId(500, test::getReportedSpans, outerTransaction.get("id").textValue());
        assertThat(spans).hasSize(1);
        final JsonNode span = spans.get(0);

        assertThat(innerTransaction.get("trace_id").textValue()).isEqualTo(outerTransaction.get("trace_id").textValue());
        assertThat(innerTransaction.get("trace_id").textValue()).isEqualTo(span.get("trace_id").textValue());
        assertThat(innerTransaction.get("parent_id").textValue()).isEqualTo(span.get("id").textValue());
        assertThat(span.get("parent_id").textValue()).isEqualTo(outerTransaction.get("id").textValue());
        assertThat(span.get("context").get("http").get("url").textValue()).endsWith("hello-world.jsp");
        assertThat(span.get("context").get("http").get("status_code").intValue()).isEqualTo(200);
    }

    private void testTransactionReporting(AbstractServletContainerIntegrationTest test) throws Exception {
        for (String pathToTest : test.getPathsToTest()) {
            pathToTest = "/simple-webapp" + pathToTest;
            test.clearMockServerLog();
            test.executeAndValidateRequest(pathToTest, "Hello World", 200);
            JsonNode transaction = test.assertTransactionReported(pathToTest, 200);
            String transactionId = transaction.get("id").textValue();
            List<JsonNode> spans = test.assertSpansTransactionId(500, test::getReportedSpans, transactionId);
            for (JsonNode span : spans) {
                assertThat(span.get("type").textValue()).isEqualTo("db.h2.query");
            }
            test.validateEventMetadata(this);
        }
    }

    private void testSpanErrorReporting(AbstractServletContainerIntegrationTest test) throws Exception {
        for (String pathToTest : test.getPathsToTest()) {
            pathToTest = "/simple-webapp" + pathToTest;
            test.clearMockServerLog();
            test.executeAndValidateRequest(pathToTest + "?cause_db_error=true", "DB Error", 200);
            JsonNode transaction = test.assertTransactionReported(pathToTest, 200);
            String transactionId = transaction.get("id").textValue();
            test.assertSpansTransactionId(500, test::getReportedSpans, transactionId);
            test.assertErrorContent(500, test::getReportedErrors, transactionId, "Column \"NON_EXISTING_COLUMN\" not found");
        }
    }

    private void testTransactionErrorReporting(AbstractServletContainerIntegrationTest test) throws Exception {
        for (String pathToTest : test.getPathsToTestErrors()) {
            String fullPathToTest = "/simple-webapp" + pathToTest;
            test.clearMockServerLog();
            // JBoss EAP 6.4 returns a 200 in case of an error in async dispatch 🤷‍♂️
            test.executeAndValidateRequest(fullPathToTest + "?cause_transaction_error=true", "", null);
            JsonNode transaction = test.assertTransactionReported(fullPathToTest, 500);
            String transactionId = transaction.get("id").textValue();
            test.assertSpansTransactionId(500, test::getReportedSpans, transactionId);
            // we currently only report errors when Exceptions are caught, still this test is relevant for response code capturing
            if (test.isExpectedStacktrace(pathToTest)) {
                test.assertErrorContent(500, test::getReportedErrors, transactionId, "Transaction failure");
            }
        }
    }
}
