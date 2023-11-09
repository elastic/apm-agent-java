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
package co.elastic.apm.agent.httpclient.v5.helper;

import co.elastic.apm.agent.httpclient.common.ApacheHttpClientApiAdapter;
import org.apache.hc.client5.http.CircularRedirectException;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;

import java.net.URI;
import java.net.URISyntaxException;

public class ApacheHttpClient5ApiAdapter implements ApacheHttpClientApiAdapter<HttpRequest, ClassicHttpRequest, HttpHost, CloseableHttpResponse> {
    private static final ApacheHttpClient5ApiAdapter INSTANCE = new ApacheHttpClient5ApiAdapter();

    private ApacheHttpClient5ApiAdapter() {
    }

    public static ApacheHttpClient5ApiAdapter get() {
        return INSTANCE;
    }

    @Override
    public String getMethod(ClassicHttpRequest request) {
        return request.getMethod();
    }

    @Override
    public URI getUri(ClassicHttpRequest request) throws URISyntaxException {
        return request.getUri();
    }

    @Override
    public CharSequence getHostName(HttpHost httpHost) {
        return httpHost.getHostName();
    }

    @Override
    public int getResponseCode(CloseableHttpResponse closeableHttpResponse) {
        return closeableHttpResponse.getCode();
    }

    @Override
    public boolean isCircularRedirectException(Throwable t) {
        return t instanceof CircularRedirectException;
    }

    @Override
    public boolean isNotNullStatusLine(CloseableHttpResponse closeableHttpResponse) {
        return true;
    }
}
