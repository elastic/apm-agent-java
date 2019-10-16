/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2019 Elastic and contributors
 * %%
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
 * #L%
 */
package co.elastic.apm.servlet.tests;

import co.elastic.apm.servlet.AbstractServletContainerIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Iterator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class CdiApplicationServerTestApp extends TestApp {

    public CdiApplicationServerTestApp() {
        super("../cdi-app/cdi-app-dependent", "cdi-app.war", "/cdi-app/status.html", "CDI App");
    }

    @Override
    public void test(AbstractServletContainerIntegrationTest containerIntegrationTest) throws Exception {
        String testPath = "/cdi-app/greeter";

        containerIntegrationTest.executeAndValidateRequest(testPath, "Hello World!", 200, null);
        JsonNode transaction = containerIntegrationTest.assertTransactionReported(testPath, 200);
        assertThat(transaction.get("name").textValue()).isEqualTo("GreeterServlet#doGet");
        String transactionId = transaction.get("id").textValue();
        List<JsonNode> spans = containerIntegrationTest.assertSpansTransactionId(
                containerIntegrationTest::getReportedSpans,
                transactionId);
        assertThat(spans.size()).isEqualTo(1);
        Iterator<JsonNode> iterator = spans.iterator();
        JsonNode cdiSpan1 = iterator.next();
        assertThat(cdiSpan1.get("name").textValue()).isEqualTo("GreeterManagerImpl#greet");
        assertThat(cdiSpan1.get("type").textValue()).isEqualTo("custom");
    }
}
