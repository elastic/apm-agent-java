/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2019 Elastic and contributors
 * %%
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
 * #L%
 */
package co.elastic.apm.agent.es.restclient;

import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.context.Destination;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.TraceContextHolder;
import co.elastic.apm.agent.objectpool.Allocator;
import co.elastic.apm.agent.objectpool.ObjectPool;
import co.elastic.apm.agent.objectpool.impl.QueueBasedObjectPool;
import co.elastic.apm.agent.util.IOUtils;
import org.apache.http.HttpEntity;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseException;
import org.elasticsearch.client.ResponseListener;
import org.jctools.queues.atomic.AtomicQueueFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;

import static org.jctools.queues.spec.ConcurrentQueueSpec.createBoundedMpmc;

public class ElasticsearchRestClientInstrumentationHelperImpl implements ElasticsearchRestClientInstrumentationHelper<HttpEntity, Response, ResponseListener> {

    private static final Logger logger = LoggerFactory.getLogger(ElasticsearchRestClientInstrumentationHelperImpl.class);

    public static final String SEARCH_QUERY_PATH_SUFFIX = "_search";
    public static final String SPAN_TYPE = "db";
    public static final String ELASTICSEARCH = "elasticsearch";
    public static final String SPAN_ACTION = "request";
    private static final int MAX_POOLED_ELEMENTS = 256;
    private final ElasticApmTracer tracer;

    private final ObjectPool<ResponseListenerWrapper> responseListenerObjectPool;

    public ElasticsearchRestClientInstrumentationHelperImpl(ElasticApmTracer tracer) {
        this.tracer = tracer;
        responseListenerObjectPool = QueueBasedObjectPool.ofRecyclable(
            AtomicQueueFactory.<ResponseListenerWrapper>newQueue(createBoundedMpmc(MAX_POOLED_ELEMENTS)),
            false,
            new ResponseListenerAllocator());
    }

    private class ResponseListenerAllocator implements Allocator<ResponseListenerWrapper> {
        @Override
        public ResponseListenerWrapper createInstance() {
            return new ResponseListenerWrapper(ElasticsearchRestClientInstrumentationHelperImpl.this);
        }
    }

    @Override
    @Nullable
    public Span createClientSpan(String method, String endpoint, @Nullable HttpEntity httpEntity) {
        final TraceContextHolder<?> activeSpan = tracer.getActive();
        if (activeSpan == null || !activeSpan.isSampled()) {
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
            if (endpoint.endsWith(SEARCH_QUERY_PATH_SUFFIX)) {
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

    @Override
    public void finishClientSpan(@Nullable Response response, Span span, @Nullable Throwable t) {
        try {
            String url = null;
            int statusCode = -1;
            if (response != null) {
                url = response.getHost().toURI();
                statusCode = response.getStatusLine().getStatusCode();
            } else if (t != null) {
                if (t instanceof ResponseException) {
                    ResponseException esre = (ResponseException) t;
                    url = esre.getResponse().getHost().toURI();
                    statusCode = esre.getResponse().getStatusLine().getStatusCode();
                }
                span.captureException(t);
            }

            if (url != null && !url.isEmpty()) {
                span.getContext().getHttp().withUrl(url);
            }
            if (statusCode > 0) {
                span.getContext().getHttp().withStatusCode(statusCode);
            }
        } finally {
            span.end();
        }
    }

    @Override
    public ResponseListener wrapResponseListener(ResponseListener listener, Span span) {
        return responseListenerObjectPool.createInstance().with(listener, span);
    }

    void recycle(ResponseListenerWrapper listenerWrapper) {
        responseListenerObjectPool.recycle(listenerWrapper);
    }
}
