/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 the original author or authors
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
import okhttp3.Request;
import okhttp3.ResponseBody;
import org.junit.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class ServletIntegrationTest extends AbstractTomcatIntegrationTest {

    @Test
    public void testTransactionReporting() throws Exception {
        final ResponseBody responseBody = httpClient.newCall(new Request.Builder()
            .get()
            .url("http://" + c.getContainerIpAddress() + ":" + c.getMappedPort(8080) + "/index.jsp")
            .build())
            .execute()
            .body();

        assertThat(responseBody).isNotNull();
        assertThat(responseBody.string()).contains("Hello World");

        final List<JsonNode> reportedTransactions = getReportedTransactions();
        assertThat(reportedTransactions.size()).isEqualTo(1);
        assertThat(reportedTransactions.iterator().next().get("context").get("request").get("url").get("pathname").textValue())
            .isEqualTo("/index.jsp");
    }

}
