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
package co.elastic.apm.agent.esrestclient;

import co.elastic.apm.agent.common.util.WildcardMatcher;
import co.elastic.apm.agent.tracer.GlobalTracer;
import co.elastic.apm.agent.tracer.AbstractSpan;
import co.elastic.apm.agent.tracer.Outcome;
import co.elastic.apm.agent.tracer.Span;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;
import co.elastic.apm.agent.tracer.Tracer;
import co.elastic.apm.agent.tracer.pooling.ObjectPool;
import co.elastic.apm.agent.util.IOUtils;
import co.elastic.apm.agent.util.LoggerUtils;
import co.elastic.apm.agent.tracer.pooling.Allocator;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseException;
import org.elasticsearch.client.ResponseListener;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CancellationException;

public class ElasticsearchRestClientInstrumentationHelper {

    private static final Logger logger = LoggerFactory.getLogger(ElasticsearchRestClientInstrumentationHelper.class);

    private static final Logger unsupportedOperationOnceLogger = LoggerUtils.logOnce(logger);
    private static final ElasticsearchRestClientInstrumentationHelper INSTANCE = new ElasticsearchRestClientInstrumentationHelper(GlobalTracer.get());

    public static final String SPAN_TYPE = "db";
    public static final String ELASTICSEARCH = "elasticsearch";
    public static final String SPAN_ACTION = "request";
    private static final int MAX_POOLED_ELEMENTS = 256;
    private final Tracer tracer;
    private final ElasticsearchConfiguration config;

    private final ObjectPool<ResponseListenerWrapper> responseListenerObjectPool;

    public static ElasticsearchRestClientInstrumentationHelper get() {
        return INSTANCE;
    }


    private ElasticsearchRestClientInstrumentationHelper(Tracer tracer) {
        this.tracer = tracer;
        this.responseListenerObjectPool = tracer.getObjectPoolFactory().createRecyclableObjectPool(MAX_POOLED_ELEMENTS, new ResponseListenerAllocator());
        this.config = tracer.getConfig(ElasticsearchConfiguration.class);
    }

    private class ResponseListenerAllocator implements Allocator<ResponseListenerWrapper> {
        @Override
        public ResponseListenerWrapper createInstance() {
            return new ResponseListenerWrapper(ElasticsearchRestClientInstrumentationHelper.this, ElasticsearchRestClientInstrumentationHelper.this.tracer);
        }
    }

    @Nullable
    public Span<?> createClientSpan(String method, String endpoint, @Nullable HttpEntity httpEntity) {
        final AbstractSpan<?> activeSpan = tracer.getActive();
        if (activeSpan == null) {
            return null;
        }

        Span<?> span = activeSpan.createExitSpan();

        // Don't record nested spans. In 5.x clients the instrumented sync method is calling the instrumented async method
        if (span == null) {
            return null;
        }

        span.withType(SPAN_TYPE)
            .withSubtype(ELASTICSEARCH)
            .withAction(SPAN_ACTION)
            .appendToName("Elasticsearch: ").appendToName(method).appendToName(" ").appendToName(endpoint);
        span.getContext().getDb().withType(ELASTICSEARCH);
        span.getContext().getServiceTarget().withType(ELASTICSEARCH);
        span.activate();
        if (span.isSampled()) {
            span.getContext().getHttp().withMethod(method);
            if (WildcardMatcher.isAnyMatch(config.getCaptureBodyUrls(), endpoint)) {
                if (httpEntity != null && httpEntity.isRepeatable()) {
                    try {
                        IOUtils.readUtf8Stream(httpEntity.getContent(), span.getContext().getDb().withStatementBuffer());
                    } catch (UnsupportedOperationException e) {
                        // special case for hibernatesearch versions pre 6.0:
                        // those don't support httpEntity.getContent() and throw an UnsupportedException when called.
                        unsupportedOperationOnceLogger.error(
                            "Failed to read Elasticsearch client query from request body, most likely because you are using hibernatesearch pre 6.0", e);
                    } catch (Exception e) {
                        logger.error("Failed to read Elasticsearch client query from request body", e);
                    }
                }
            }
        }
        return span;
    }

    public void finishClientSpan(@Nullable Response response, Span<?> span, @Nullable Throwable t) {
        try {
            String url = null;
            int statusCode = -1;
            String address = null;
            int port = -1;
            String cluster = null;
            if (response != null) {
                HttpHost host = response.getHost();
                address = host.getHostName();
                port = host.getPort();
                url = host.toURI();
                statusCode = response.getStatusLine().getStatusCode();

                cluster = response.getHeader("x-found-handling-cluster");

            } else if (t != null) {
                if (t instanceof ResponseException) {
                    ResponseException esre = (ResponseException) t;
                    HttpHost host = esre.getResponse().getHost();
                    address = host.getHostName();
                    port = host.getPort();
                    url = host.toURI();
                    statusCode = esre.getResponse().getStatusLine().getStatusCode();
                } else if (t instanceof CancellationException) {
                    // We can't tell whether a cancelled search is related to a failure or not
                    span.withOutcome(Outcome.UNKNOWN);
                }
                span.captureException(t);
            }

            if (url != null && !url.isEmpty()) {
                span.getContext().getHttp().withUrl(url);
            }
            span.getContext().getHttp().withStatusCode(statusCode);
            span.getContext().getDestination().withAddress(address).withPort(port);
            span.getContext().getServiceTarget().withName(cluster);
        } finally {
            span.end();
        }
    }

    public ResponseListener wrapClientResponseListener(ResponseListener listener, Span<?> span) {
        return responseListenerObjectPool.createInstance().withClientSpan(listener, span);
    }

    public ResponseListener wrapContextPropagationContextListener(ResponseListener listener, AbstractSpan<?> activeContext) {
        return responseListenerObjectPool.createInstance().withContextPropagation(listener, activeContext);
    }

    void recycle(ResponseListenerWrapper listenerWrapper) {
        responseListenerObjectPool.recycle(listenerWrapper);
    }
}
