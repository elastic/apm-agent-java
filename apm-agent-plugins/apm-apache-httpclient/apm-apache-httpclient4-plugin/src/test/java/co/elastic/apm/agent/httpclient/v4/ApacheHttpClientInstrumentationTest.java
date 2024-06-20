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
package co.elastic.apm.agent.httpclient.v4;

import co.elastic.apm.agent.common.util.WildcardMatcher;
import co.elastic.apm.agent.httpclient.AbstractHttpClientInstrumentationTest;
import co.elastic.apm.agent.tracer.configuration.WebConfiguration;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

public class ApacheHttpClientInstrumentationTest extends AbstractHttpClientInstrumentationTest {

    private static CloseableHttpClient client;

    @BeforeClass
    public static void setUp() {
        client = HttpClients.createDefault();
    }

    @AfterClass
    public static void close() throws IOException {
        client.close();
    }

    @Override
    protected void performGet(String path) throws Exception {
        CloseableHttpResponse response = client.execute(new HttpGet(path));
        response.getStatusLine().getStatusCode();
        response.close();
    }

    @Test
    public void testPostBodyCapture() throws IOException {
        doReturn(Collections.singletonList(WildcardMatcher.matchAll()))
            .when(getConfig().getConfig(WebConfiguration.class)).getCaptureClientRequestContentTypes();

        StringBuilder longString = new StringBuilder();
        for (int i = 0; i < 200; i++) {
            longString.append(String.format("line %1$4d\n", i));
        }
        HttpPost request = new HttpPost(getBaseUrl() + "/");
        request.setEntity(new InputStreamEntity(new ByteArrayInputStream(longString.toString().getBytes(StandardCharsets.UTF_8))));

        client.execute(request);

        expectSpan("/")
            .withRequestBodySatisfying(body -> {
                assertThat(body).endsWith("line  101\nline");
            }).verify();
    }

}
