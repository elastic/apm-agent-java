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


import co.elastic.apm.agent.httpclient.HttpClientHelper;
import co.elastic.apm.agent.httpclient.common.RequestBodyCaptureRegistry;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;
import co.elastic.apm.agent.tracer.Span;
import co.elastic.apm.agent.tracer.TraceState;
import co.elastic.apm.agent.tracer.pooling.Recyclable;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.nio.RequestChannel;
import org.apache.hc.core5.http.protocol.HttpContext;

import javax.annotation.Nullable;
import java.io.IOException;
import java.net.URI;

public class RequestChannelWrapper implements RequestChannel, Recyclable {
    private static final Logger logger = LoggerFactory.getLogger(RequestChannelWrapper.class);

    private volatile RequestChannel delegate;

    @Nullable
    private TraceState<?> toPropagate;

    @Nullable
    private Span<?> span;

    public RequestChannelWrapper() {
    }

    public RequestChannelWrapper with(RequestChannel delegate,
                                      @Nullable Span<?> span,
                                      TraceState<?> toPropagate) {
        this.span = span;
        toPropagate.incrementReferences();
        this.toPropagate = toPropagate;
        // write to volatile field last
        this.delegate = delegate;
        return this;
    }

    @Override
    public void resetState() {
        span = null;
        if (toPropagate != null) {
            toPropagate.decrementReferences();
            toPropagate = null;
        }
        // write to volatile field last
        delegate = null;
    }

    @Override
    public void sendRequest(HttpRequest httpRequest, EntityDetails entityDetails, HttpContext httpContext) throws HttpException, IOException {
        try {
            if (toPropagate == null) {
                throw new IllegalStateException("sendRequest was called before 'with'!");
            }

            if (httpRequest != null) {
                if (span != null) {
                    RequestBodyCaptureRegistry.potentiallyCaptureRequestBody(httpRequest, span, ApacheHttpClient5ApiAdapter.get(), RequestHeaderAccessor.INSTANCE);
                    String host = null;
                    String protocol = null;
                    int port = -1;
                    URI httpRequestURI = null;
                    try {
                        httpRequestURI = httpRequest.getUri();
                        if (httpRequestURI != null) {
                            host = httpRequestURI.getHost();
                            port = httpRequestURI.getPort();
                            protocol = httpRequestURI.getScheme();
                        }
                    } catch (Exception e) {
                        logger.warn("Failed to obtain Apache HttpClient 5 destination info, null httpRequestURI", e);
                    }
                    String method = httpRequest.getMethod();
                    span.withName(method).appendToName(" ");
                    if (host != null) {
                        span.appendToName(host);
                    }
                    String requestUri = httpRequest.getRequestUri();
                    // starting with httpclient 5.1 "getRequestUri" can return relative path, not always the absolute one
                    // thus we have to fallback on using the URI instance
                    if(requestUri != null && requestUri.startsWith("/") && httpRequestURI != null) {
                        requestUri = httpRequestURI.toString();
                    }
                    span.getContext().getHttp().withMethod(method).withUrl(requestUri);
                    HttpClientHelper.setDestinationServiceDetails(span, protocol, host, port);
                }

                toPropagate.propagateContext(httpRequest, RequestHeaderAccessor.INSTANCE, RequestHeaderAccessor.INSTANCE);
            }

            toPropagate.decrementReferences();
            toPropagate = null;
        } finally {
            delegate.sendRequest(httpRequest, entityDetails, httpContext);
        }
    }
}
