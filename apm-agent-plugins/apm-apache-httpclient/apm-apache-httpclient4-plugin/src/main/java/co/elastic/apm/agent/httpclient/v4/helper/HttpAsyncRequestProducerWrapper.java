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

import co.elastic.apm.agent.httpclient.HttpClientHelper;
import co.elastic.apm.agent.tracer.AbstractSpan;
import co.elastic.apm.agent.tracer.Span;
import co.elastic.apm.agent.tracer.dispatch.HeaderUtils;
import co.elastic.apm.agent.tracer.pooling.Recyclable;
import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.RequestLine;
import org.apache.http.nio.ContentEncoder;
import org.apache.http.nio.IOControl;
import org.apache.http.nio.protocol.HttpAsyncRequestProducer;
import org.apache.http.protocol.HttpContext;

import javax.annotation.Nullable;
import java.io.IOException;

class HttpAsyncRequestProducerWrapper implements HttpAsyncRequestProducer, Recyclable {
    private final ApacheHttpAsyncClientHelper asyncClientHelper;
    private volatile HttpAsyncRequestProducer delegate;

    @Nullable
    private AbstractSpan<?> parent;

    @Nullable
    private Span<?> span;

    HttpAsyncRequestProducerWrapper(ApacheHttpAsyncClientHelper helper) {
        this.asyncClientHelper = helper;
    }

    /**
     * Called in order to wrap the provided {@link HttpAsyncRequestProducer} with our wrapper that is capable of
     * populating the HTTP span with data and ending it, as well as propagating the trace context through the
     * generated request.
     * If the {@code span} is not {@code null}, it will be used for trace context propagation. Otherwise, the
     * {@code parent} will be used instead.
     *
     * @param delegate     the original {@link HttpAsyncRequestProducer}
     * @param span         the HTTP span corresponding the given {@link HttpAsyncRequestProducer}
     * @param parent       the active span when this method is called
     * @return the {@link HttpAsyncRequestProducer} wrapper
     */
    public HttpAsyncRequestProducerWrapper with(HttpAsyncRequestProducer delegate, @Nullable Span<?> span,
                                                @Nullable AbstractSpan<?> parent) {
        // Order is important due to visibility - write to delegate last on this (initiating) thread
        this.span = span;
        if (parent != null) {
            // preventing from the parent to be ended before we propagate the context
            parent.incrementReferences();
            this.parent = parent;
        }
        this.delegate = delegate;
        return this;
    }

    @Override
    public HttpHost getTarget() {
        return delegate.getTarget();
    }

    @Override
    public HttpRequest generateRequest() throws IOException, HttpException {
        // first read the volatile, span and parent will become visible as a result
        HttpRequest request = delegate.generateRequest();

        // trace context propagation
        if (request != null) {
            if (span != null) {
                RequestLine requestLine = request.getRequestLine();
                if (requestLine != null) {
                    String method = requestLine.getMethod();
                    span.withName(method).appendToName(" ");
                    span.getContext().getHttp().withMethod(method).withUrl(requestLine.getUri());
                }
            }

            if (!HeaderUtils.containsAny(asyncClientHelper.getTracer().getTraceHeaderNames(), request, RequestHeaderAccessor.INSTANCE)) {
                if (span != null) {
                    span.propagateTraceContext(request, RequestHeaderAccessor.INSTANCE);
                } else if (parent != null) {
                    parent.propagateTraceContext(request, RequestHeaderAccessor.INSTANCE);
                }
            }
        }

        if (parent != null) {
            parent.decrementReferences();
            parent = null;
        }

        // HTTP span details
        if (span != null) {
            HttpHost host = getTarget();
            //noinspection ConstantConditions
            if (host != null) {
                String hostname = host.getHostName();
                if (hostname != null) {
                    span.appendToName(hostname);
                    HttpClientHelper.setDestinationServiceDetails(span, host.getSchemeName(), hostname, host.getPort());
                }
            }
        }

        //noinspection ConstantConditions
        return request;
    }

    @Override
    public void produceContent(ContentEncoder encoder, IOControl ioctrl) throws IOException {
        delegate.produceContent(encoder, ioctrl);
    }

    @Override
    public void requestCompleted(HttpContext context) {
        delegate.requestCompleted(context);
    }

    @Override
    public void failed(Exception ex) {
        delegate.failed(ex);
    }

    @Override
    public boolean isRepeatable() {
        return delegate.isRepeatable();
    }

    @Override
    public void resetRequest() throws IOException {
        delegate.resetRequest();
    }

    @Override
    public void close() throws IOException {
        delegate.close();
        asyncClientHelper.recycle(this);
    }

    @Override
    public void resetState() {
        // Order is important due to visibility - write to delegate last
        span = null;
        if (parent != null) {
            parent.decrementReferences();
            parent = null;
        }
        delegate = null;
    }
}
