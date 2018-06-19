/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 Elastic and contributors
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
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(Parameterized.class)
public class ServletIntegrationTest extends AbstractTomcatIntegrationTest {

    public ServletIntegrationTest(String tomcatVersion) {
        super(tomcatVersion, "../simple-webapp/target/ROOT.war");
    }

    @Parameterized.Parameters(name = "Tomcat {0}")
    public static Iterable<Object[]> data() {
        return Arrays.asList(new Object[][]{{"7-jre7-slim"}, {"8.5-jre8-slim"}, {"9-jre9-slim"}, {"9-jre10-slim"}});
    }

    @Test
    public void testTransactionReporting() throws Exception {
        final Response response = httpClient.newCall(new Request.Builder()
            .get()
            .url("http://" + tomcatContainer.getContainerIpAddress() + ":" + tomcatContainer.getMappedPort(8080) + "/index.jsp")
            .build())
            .execute();

        assertThat(response.code()).withFailMessage(response.toString()).isEqualTo(200);
        final ResponseBody responseBody = response.body();
        assertThat(responseBody).isNotNull();
        assertThat(responseBody.string()).contains("Hello World");

        final List<JsonNode> reportedTransactions = getReportedTransactions();
        assertThat(reportedTransactions.size()).isEqualTo(1);
        assertThat(reportedTransactions.iterator().next().get("context").get("request").get("url").get("pathname").textValue())
            .isEqualTo("/index.jsp");
    }

}
