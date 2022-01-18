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

import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.GlobalTracer;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Outcome;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.matcher.WildcardMatcher;
import co.elastic.apm.agent.objectpool.Allocator;
import co.elastic.apm.agent.objectpool.ObjectPool;
import co.elastic.apm.agent.objectpool.impl.QueueBasedObjectPool;
import co.elastic.apm.agent.util.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseException;
import org.elasticsearch.client.ResponseListener;
import org.jctools.queues.atomic.AtomicQueueFactory;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CancellationException;

import static org.jctools.queues.spec.ConcurrentQueueSpec.createBoundedMpmc;

public class ElasticsearchRestClientInstrumentationHelper {

    private static final Logger logger = LoggerFactory.getLogger(ElasticsearchRestClientInstrumentationHelper.class);
    private static final ElasticsearchRestClientInstrumentationHelper INSTANCE = new ElasticsearchRestClientInstrumentationHelper(GlobalTracer.requireTracerImpl());

    public static final List<WildcardMatcher> QUERY_WILDCARD_MATCHERS = Arrays.asList(
        WildcardMatcher.valueOf("*_search"),
        WildcardMatcher.valueOf("*_msearch"),
        WildcardMatcher.valueOf("*_msearch/template"),
        WildcardMatcher.valueOf("*_search/template"),
        WildcardMatcher.valueOf("*_count"));
    public static final String SPAN_TYPE = "db";
    public static final String ELASTICSEARCH = "elasticsearch";
    public static final String SPAN_ACTION = "request";
    private static final int MAX_POOLED_ELEMENTS = 256;
    private final ElasticApmTracer tracer;

    private final ObjectPool<ResponseListenerWrapper> responseListenerObjectPool;

    public static ElasticsearchRestClientInstrumentationHelper get() {
        return INSTANCE;
    }

    private ElasticsearchRestClientInstrumentationHelper(ElasticApmTracer tracer) {
        this.tracer = tracer;
        responseListenerObjectPool = QueueBasedObjectPool.ofRecyclable(
            AtomicQueueFactory.<ResponseListenerWrapper>newQueue(createBoundedMpmc(MAX_POOLED_ELEMENTS)),
            false,
            new ResponseListenerAllocator());
    }

    private class ResponseListenerAllocator implements Allocator<ResponseListenerWrapper> {
        @Override
        public ResponseListenerWrapper createInstance() {
            return new ResponseListenerWrapper(ElasticsearchRestClientInstrumentationHelper.this);
        }
    }

    @Nullable
    public Span createClientSpan(String method, String endpoint, @Nullable HttpEntity httpEntity) {
        final AbstractSpan<?> activeSpan = tracer.getActive();
        if (activeSpan == null) {
            return null;
        }

        Span span = activeSpan.createExitSpan();

        // Don't record nested spans. In 5.x clients the instrumented sync method is calling the instrumented async method
        if (span == null) {
            return null;
        }

        span.withType(SPAN_TYPE)
            .withSubtype(ELASTICSEARCH)
            .withAction(SPAN_ACTION)
            .appendToName("Elasticsearch: ").appendToName(method).appendToName(" ").appendToName(endpoint);
        span.getContext().getDb().withType(ELASTICSEARCH);
        span.activate();

        if (span.isSampled()) {
            span.getContext().getHttp().withMethod(method);
            if (WildcardMatcher.isAnyMatch(QUERY_WILDCARD_MATCHERS, endpoint)) {
                if (httpEntity != null && httpEntity.isRepeatable()) {
                    try {
                        IOUtils.readUtf8Stream(httpEntity.getContent(), span.getContext().getDb().withStatementBuffer());
                    } catch (IOException e) {
                        logger.error("Failed to read Elasticsearch client query from request body", e);
                    }
                }
            }
            span.getContext().getDestination().getService().withName(ELASTICSEARCH).withResource(ELASTICSEARCH).withType(SPAN_TYPE);
        }
        return span;
    }

    public void finishClientSpan(@Nullable Response response, Span span, @Nullable Throwable t) {
        try {
            String url = null;
            int statusCode = -1;
            String address = null;
            int port = -1;
            if (response != null) {
                HttpHost host = response.getHost();
                address = host.getHostName();
                port = host.getPort();
                url = host.toURI();
                statusCode = response.getStatusLine().getStatusCode();
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
        } finally {
            span.end();
        }
    }

    public ResponseListener wrapResponseListener(ResponseListener listener, Span span) {
        return responseListenerObjectPool.createInstance().with(listener, span);
    }

    void recycle(ResponseListenerWrapper listenerWrapper) {
        responseListenerObjectPool.recycle(listenerWrapper);
    }
}
