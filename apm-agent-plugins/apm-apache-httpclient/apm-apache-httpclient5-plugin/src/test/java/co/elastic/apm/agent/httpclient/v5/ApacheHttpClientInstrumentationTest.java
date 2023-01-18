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
package co.elastic.apm.agent.httpclient.v5;

import co.elastic.apm.agent.httpclient.AbstractHttpClientInstrumentationTest;
import org.apache.hc.client5.http.ClientProtocolException;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;

import java.io.IOException;

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
        HttpClientResponseHandler<String> responseHandler = response -> {
            int status = response.getCode();
            if (status >= 200 && status < 300) {
                HttpEntity entity = response.getEntity();
                String res = entity != null ? EntityUtils.toString(entity) : null;
                System.out.println(String.format("got result %s", res));
                return res;
            } else {
                throw new ClientProtocolException("Unexpected response status: " + status);
            }
        };
        String response = client.execute(new HttpGet(path), responseHandler);
    }

    /**
     * org.apache.hc.client5.http.ClientProtocolException: Request URI authority contains deprecated userinfo component
     * @return
     */
    @Override
    public boolean isTestHttpCallWithUserInfoEnabled() {
        return false;
    }
}
