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
import com.fasterxml.jackson.databind.JsonNode;
import okhttp3.Response;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class ExternalPluginTestApp extends TestApp {
    public ExternalPluginTestApp() {
        super(
            "../external-plugin-test/external-plugin-app",
            "external-plugin-webapp.war",
            "/external-plugin-webapp/status.html",
            "external-plugin-webapp"
        );
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
        return Map.of("ELASTIC_APM_PLUGINS_DIR", "/plugins");
    }

    @Override
    public void test(AbstractServletContainerIntegrationTest test) throws Exception {
        final Response response = test.executeRequest("/external-plugin-webapp/test-external-plugin", null);
        assertThat(response.code()).isEqualTo(200);
        assertThat(response.body().string()).isNotEmpty();

        final List<JsonNode> reportedTransactions = test.getReportedTransactions();
        assertThat(reportedTransactions).hasSize(1);
        String traceId = reportedTransactions.get(0).get("trace_id").textValue();
        String transactionId = reportedTransactions.get(0).get("id").textValue();
        List<JsonNode> reportedSpans = test.getReportedSpans();
        assertThat(reportedSpans).hasSize(1);
        JsonNode span = reportedSpans.get(0);
        assertThat(span.get("name").textValue()).isEqualTo("traceMe");
        assertThat(span.get("trace_id").textValue()).isEqualTo(traceId);
        assertThat(span.get("parent_id").textValue()).isEqualTo(transactionId);
        assertThat(span.get("type").textValue()).isEqualTo("plugin.external.trace");
        List<JsonNode> reportedErrors = test.getReportedErrors();
        assertThat(reportedErrors).hasSize(1);
        JsonNode error = reportedErrors.get(0);
        assertThat(error.get("transaction_id").textValue()).isEqualTo(transactionId);
        assertThat(error.get("context").get("service").get("name").textValue()).isEqualTo("external-plugin-webapp");
        JsonNode exception = error.get("exception");
        assertThat(exception.get("message").textValue()).isEqualTo("Test Exception");
        assertThat(exception.get("type").textValue()).isEqualTo("java.lang.IllegalStateException");
    }

}
