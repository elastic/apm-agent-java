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
import okhttp3.Response;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

public class SoapTestApp extends TestApp {
    public SoapTestApp() {
        super("../soap-test", "soap-test.war", "/soap-test/status.html", "soap-test");
    }

    @Override
    public void test(AbstractServletContainerIntegrationTest test) throws Exception {
        final Response response = test.executeRequest("/soap-test/execute-soap-request");
        assertThat(response.code()).isEqualTo(200);
        assertThat(response.body().string()).isNotEmpty();

        final List<JsonNode> reportedTransactions = test.getReportedTransactions();
        // there will also be another transaction for getting the WSDL but we don't need to assert on that
        assertThat(reportedTransactions.stream().map(node -> node.get("name").textValue())).contains("HelloWorldServiceImpl#sayHello", "SoapClientServlet#doGet");
        final Set<String> traceIds = reportedTransactions.stream().map(node -> node.get("trace_id").textValue()).collect(Collectors.toSet());
        assertThat(traceIds).hasSize(1);

        final List<JsonNode> spans = test.getReportedSpans();
        // there will also be another span for getting the WSDL but we don't need to assert on that
        assertThat(spans.stream().map(node -> node.get("trace_id").textValue()).collect(Collectors.toSet())).isEqualTo(traceIds);
        assertThat(spans.stream().filter(span -> span.get("context").get("http").get("url").textValue().endsWith("/soap-test/HelloWorldService"))).isNotEmpty();
    }
}
