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

import co.elastic.apm.agent.httpclient.AbstractHttpClientInstrumentationTest;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

import java.io.ByteArrayInputStream;
import java.io.IOException;

public class ApacheHttpClientInstrumentationTest extends AbstractHttpClientInstrumentationTest {

    private static CloseableHttpClient client;

    @BeforeAll
    public static void setUp() {
        client = HttpClients.createDefault();
    }

    @AfterAll
    public static void close() throws IOException {
        client.close();
    }

    @Override
    protected void performGet(String path) throws Exception {
        CloseableHttpResponse response = client.execute(new HttpGet(path));
        response.getStatusLine().getStatusCode();
        response.close();
    }

    @Override
    protected boolean isBodyCapturingSupported() {
        return true;
    }

    @Override
    protected void performPost(String path, byte[] content, String contentTypeHeader) throws Exception {
        HttpPost request = new HttpPost(path);
        request.setEntity(new InputStreamEntity(new ByteArrayInputStream(content)));
        request.setHeader("Content-Type", contentTypeHeader);

        client.execute(request);
    }

}
