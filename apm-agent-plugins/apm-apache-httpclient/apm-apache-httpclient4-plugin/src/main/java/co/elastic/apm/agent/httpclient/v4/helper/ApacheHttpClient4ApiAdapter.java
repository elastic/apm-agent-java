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
package co.elastic.apm.agent.httpclient.v4.helper;


import co.elastic.apm.agent.httpclient.common.ApacheHttpClientApiAdapter;
import co.elastic.apm.agent.tracer.GlobalTracer;
import co.elastic.apm.agent.tracer.Tracer;
import co.elastic.apm.agent.tracer.configuration.CoreConfiguration;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.StatusLine;
import org.apache.http.client.CircularRedirectException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpRequestWrapper;

import javax.annotation.Nullable;
import java.net.URI;

public class ApacheHttpClient4ApiAdapter implements ApacheHttpClientApiAdapter<HttpRequest, HttpRequestWrapper, HttpHost, CloseableHttpResponse, HttpEntity> {
    private static final ApacheHttpClient4ApiAdapter INSTANCE = new ApacheHttpClient4ApiAdapter();

    private final Tracer tracer = GlobalTracer.get();

    private ApacheHttpClient4ApiAdapter() {
    }

    public static ApacheHttpClient4ApiAdapter get() {
        return INSTANCE;
    }

    @Override
    public String getMethod(HttpRequestWrapper request) {
        return request.getMethod();
    }

    @Override
    public URI getUri(HttpRequestWrapper request) {
        return request.getURI();
    }

    @Override
    public CharSequence getHostName(HttpHost httpHost, HttpRequestWrapper request) {
        return httpHost.getHostName();
    }

    @Override
    public HttpEntity getRequestEntity(HttpRequest request) {
        if (request instanceof HttpEntityEnclosingRequest) {
            return ((HttpEntityEnclosingRequest) request).getEntity();
        }
        return null;
    }

    @Override
    @Nullable
    public byte[] getSimpleBodyBytes(HttpRequest request) {
        return null; //Apache v4 client only provides body via HttpEntity
    }

    @Override
    public int getResponseCode(CloseableHttpResponse closeableHttpResponse) {
        final StatusLine statusLine = closeableHttpResponse.getStatusLine();
        if (statusLine == null) {
            return 0;
        }
        return statusLine.getStatusCode();
    }

    @Override
    public boolean isCircularRedirectException(Throwable t) {
        if (t == null || tracer.getConfig(CoreConfiguration.class).isAvoidTouchingExceptions()) {
            return false;
        }
        return t instanceof CircularRedirectException;
    }

    @Override
    public boolean isNotNullStatusLine(CloseableHttpResponse o) {
        return null != o.getStatusLine();
    }
}
