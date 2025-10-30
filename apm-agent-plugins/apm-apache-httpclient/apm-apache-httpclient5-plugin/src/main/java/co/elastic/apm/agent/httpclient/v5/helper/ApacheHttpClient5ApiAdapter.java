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
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;
import co.elastic.apm.agent.tracer.GlobalTracer;
import co.elastic.apm.agent.tracer.Tracer;
import co.elastic.apm.agent.tracer.configuration.CoreConfiguration;
import org.apache.hc.client5.http.CircularRedirectException;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.routing.RoutingSupport;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpEntityContainer;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;

import javax.annotation.Nullable;
import java.net.URI;
import java.net.URISyntaxException;

public class ApacheHttpClient5ApiAdapter implements ApacheHttpClientApiAdapter<HttpRequest, ClassicHttpRequest, HttpHost, CloseableHttpResponse, HttpEntity> {
    private static final ApacheHttpClient5ApiAdapter INSTANCE = new ApacheHttpClient5ApiAdapter();

    private static final Logger logger = LoggerFactory.getLogger(ApacheHttpClient5ApiAdapter.class);

    private final Tracer tracer = GlobalTracer.get();

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
    public CharSequence getHostName(@Nullable HttpHost httpHost, ClassicHttpRequest request) {
        if (httpHost != null) {
            return httpHost.getHostName();
        }

        try {
            HttpHost host = RoutingSupport.determineHost(request);
            return host == null ?
                null : host.getHostName();
        } catch (HttpException e) {
            logger.error("Exception while determining HostName", e);

            return null;
        }
    }

    @Nullable
    @Override
    public byte[] getSimpleBodyBytes(HttpRequest httpRequest) {
        if (httpRequest instanceof SimpleHttpRequest) {
            return ((SimpleHttpRequest) httpRequest).getBodyBytes();
        }
        return null;
    }

    @Override
    public int getResponseCode(CloseableHttpResponse closeableHttpResponse) {
        return closeableHttpResponse.getCode();
    }

    @Override
    public boolean isCircularRedirectException(Throwable t) {
        if (t == null || tracer.getConfig(CoreConfiguration.class).isAvoidTouchingExceptions()) {
            return false;
        }
        return t instanceof CircularRedirectException;
    }

    @Override
    public boolean isNotNullStatusLine(CloseableHttpResponse closeableHttpResponse) {
        // HTTP response messages in HttpClient 5.x no longer have a status line.
        return true;
    }

    @Nullable
    @Override
    public HttpEntity getRequestEntity(HttpRequest httpRequest) {
        if (httpRequest instanceof HttpEntityContainer) {
            return ((HttpEntityContainer) httpRequest).getEntity();
        }
        return null;
    }
}
