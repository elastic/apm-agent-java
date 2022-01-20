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

import co.elastic.apm.agent.impl.Tracer;
import co.elastic.apm.agent.impl.transaction.Outcome;
import co.elastic.apm.servlet.AbstractServletContainerIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import okhttp3.Response;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class ExternalPluginTestApp extends TestApp {

    private final String appWarFileName;
    private boolean isServiceNameExpected;

    protected ExternalPluginTestApp(String testAppModuleName, String appWarFileName) {
        super("../external-plugin-test/" + testAppModuleName,
            appWarFileName + ".war",
            appWarFileName,
            "status.html",
            appWarFileName
        );
        this.appWarFileName = appWarFileName;
    }

    @Override
    public Map<String, String> getAdditionalFilesToBind() {
        File externalPluginBuildDir = new File("../external-plugin-test/external-plugin/target/");
        File pluginJar = Arrays.stream(externalPluginBuildDir
            .listFiles(file -> file.getName().startsWith("external-plugin-") && !file.getName().contains("javadoc") && file.getName().endsWith(".jar")))
            .findFirst()
            .orElse(null);
        assert pluginJar != null;
        return Map.of(pluginJar.getAbsolutePath(), "/plugins/" + pluginJar.getName());
    }

    @Override
    public Map<String, String> getAdditionalEnvVariables() {
        return Map.of(
            "ELASTIC_APM_PLUGINS_DIR", "/plugins",
            "ELASTIC_APM_ENABLE_LOG_CORRELATION", "true"
        );
    }

    @Override
    public Collection<String> getPathsToIgnore() {
        return List.of("/test-transaction-external-plugin");
    }

    @Override
    public void test(AbstractServletContainerIntegrationTest containerIntegrationTest) throws Exception {
        executeTest(containerIntegrationTest, true, this::testSpanReporting);
        // service name is not expected to be captured for the plugin transaction creation test
        executeTest(containerIntegrationTest, false, this::testTransactionReporting);
    }

    private void executeTest(final AbstractServletContainerIntegrationTest containerIntegrationTest,
                             boolean isServiceNameExpected,
                             Test test) throws IOException {
        containerIntegrationTest.clearMockServerLog();
        this.isServiceNameExpected = isServiceNameExpected;
        test.execute(containerIntegrationTest);
    }

    /**
     * Since we test custom transaction creation through the external plugin, the service name for this transaction cannot be
     * captured through the {@link Tracer#overrideServiceNameForClassLoader(java.lang.ClassLoader, java.lang.String)} mechanism.
     */
    @Nullable
    @Override
    public String getExpectedServiceName() {
        if (!isServiceNameExpected) {
            return null;
        }
        return super.getExpectedServiceName();
    }

    void testSpanReporting(AbstractServletContainerIntegrationTest test) throws IOException {
        final Response response = test.executeRequest(constructFullPath("test-span-external-plugin"), null);
        assertThat(response.code()).isEqualTo(200);
        assertThat(Objects.requireNonNull(response.body()).string()).isNotEmpty();

        final List<JsonNode> reportedTransactions = test.getReportedTransactions();
        assertThat(reportedTransactions).hasSize(1);
        JsonNode transaction = reportedTransactions.get(0);
        String traceId = transaction.get("trace_id").textValue();
        String transactionId = transaction.get("id").textValue();
        assertThat(transaction.get("outcome").textValue()).isEqualTo(Outcome.SUCCESS.toString());
        List<JsonNode> reportedSpans = test.getReportedSpans();
        assertThat(reportedSpans).hasSize(1);
        JsonNode span = reportedSpans.get(0);
        assertThat(span.get("name").textValue()).isEqualTo("traceMe");
        assertThat(span.get("trace_id").textValue()).isEqualTo(traceId);
        assertThat(span.get("parent_id").textValue()).isEqualTo(transactionId);
        assertThat(span.get("type").textValue()).isEqualTo("plugin.external.trace");
        assertThat(span.get("outcome").textValue()).isEqualTo(Outcome.FAILURE.toString());
        List<JsonNode> reportedErrors = test.getReportedErrors();
        assertThat(reportedErrors).hasSize(1);
        JsonNode error = reportedErrors.get(0);
        assertThat(error.get("transaction_id").textValue()).isEqualTo(transactionId);
        assertThat(error.get("context").get("service").get("name").textValue()).isEqualTo(appWarFileName);
        JsonNode exception = error.get("exception");
        assertThat(exception.get("message").textValue()).isEqualTo("Test Exception");
        assertThat(exception.get("type").textValue()).isEqualTo("java.lang.IllegalStateException");
    }

    void testTransactionReporting(AbstractServletContainerIntegrationTest test) throws IOException {
        final Response response = test.executeRequest(constructFullPath("test-transaction-external-plugin"), null);
        assertThat(response.code()).isEqualTo(200);
        assertThat(Objects.requireNonNull(response.body()).string()).isNotEmpty();

        final List<JsonNode> reportedTransactions = test.getReportedTransactions();
        assertThat(reportedTransactions).hasSize(1);
        JsonNode transaction = reportedTransactions.get(0);
        assertThat(transaction.get("name").textValue()).isEqualTo("TestClass#traceMe");
        assertThat(transaction.get("type").textValue()).isEqualTo("custom");
        assertThat(transaction.get("outcome").textValue()).isEqualTo(Outcome.FAILURE.toString());
        List<JsonNode> reportedErrors = test.getReportedErrors();
        assertThat(reportedErrors).hasSize(1);
        JsonNode error = reportedErrors.get(0);
        String transactionId = transaction.get("id").textValue();
        assertThat(error.get("transaction_id").textValue()).isEqualTo(transactionId);
        JsonNode exception = error.get("exception");
        assertThat(exception.get("message").textValue()).isEqualTo("Test Exception");
        assertThat(exception.get("type").textValue()).isEqualTo("java.lang.IllegalStateException");
    }

    private String constructFullPath(String path) {
        return String.format("/%s/%s", appWarFileName, path);
    }

    @FunctionalInterface
    private interface Test {
        void execute(AbstractServletContainerIntegrationTest containerIntegrationTest) throws IOException;
    }
}
