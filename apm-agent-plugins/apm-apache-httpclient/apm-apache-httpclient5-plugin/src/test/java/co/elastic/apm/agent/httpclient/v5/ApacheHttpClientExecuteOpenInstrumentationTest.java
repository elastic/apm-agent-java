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
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.InputStreamEntity;
import org.junit.AfterClass;
import org.junit.BeforeClass;

import java.io.ByteArrayInputStream;
import java.io.IOException;

public class ApacheHttpClientExecuteOpenInstrumentationTest extends AbstractHttpClientInstrumentationTest {

    private static CloseableHttpClient client;

    @BeforeClass
    public static void setUp() {
        client = HttpClients.createDefault();
    }

    @AfterClass
    public static void close() throws IOException {
        client.close();
    }

    /**
     * RFC 7230: treat presence of userinfo in authority component in request URI as an HTTP protocol violation.
     *
     * Uses {@link org.apache.hc.core5.http.message.BasicHttpRequest#setUri} to fill {@link org.apache.hc.core5.net.URIAuthority}
     *
     * Assertions on authority in {@link org.apache.hc.client5.http.impl.classic.ProtocolExec#execute}
     */
    @Override
    public boolean isTestHttpCallWithUserInfoEnabled() {
        return false;
    }

    private final HttpClientResponseHandler<String> responseHandler = response -> {
        int status = response.getCode();
        if (status >= 200 && status < 300) {
            HttpEntity entity = response.getEntity();
            return entity != null ? EntityUtils.toString(entity) : null;
        } else {
            throw new ClientProtocolException("Unexpected response status: " + status);
        }
    };

    @Override
    protected void performGet(String path) throws Exception {
        ClassicHttpResponse response = client.executeOpen(null, new HttpGet(path), null);
        responseHandler.handleResponse(response);
    }

    @Override
    protected boolean isBodyCapturingSupported() {
        return true;
    }

    @Override
    protected void performPost(String path, byte[] content, String contentTypeHeader) throws Exception {
        HttpPost request = new HttpPost(path);
        request.setEntity(new InputStreamEntity(new ByteArrayInputStream(content), ContentType.parse(contentTypeHeader)));
        request.setHeader("Content-Type", contentTypeHeader);

        ClassicHttpResponse response = client.executeOpen(null, request, null);
        responseHandler.handleResponse(response);
    }
}
