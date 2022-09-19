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
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Outcome;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.matcher.WildcardMatcher;
import co.elastic.apm.agent.objectpool.Allocator;
import co.elastic.apm.agent.objectpool.ObjectPool;
import co.elastic.apm.agent.objectpool.impl.QueueBasedObjectPool;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;
import co.elastic.apm.agent.sdk.weakconcurrent.WeakConcurrent;
import co.elastic.apm.agent.sdk.weakconcurrent.WeakMap;
import co.elastic.apm.agent.util.IOUtils;
import com.dslplatform.json.DslJson;
import com.dslplatform.json.JsonReader;
import com.dslplatform.json.ObjectConverter;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseException;
import org.elasticsearch.client.ResponseListener;
import org.elasticsearch.client.RestClient;
import org.jctools.queues.atomic.AtomicQueueFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;

import static org.jctools.queues.spec.ConcurrentQueueSpec.createBoundedMpmc;

public abstract class ElasticsearchRestClientInstrumentationHelper {

    private static final Logger logger = LoggerFactory.getLogger(ElasticsearchRestClientInstrumentationHelper.class);

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

    protected ElasticsearchRestClientInstrumentationHelper(ElasticApmTracer tracer) {
        this.tracer = tracer;
        responseListenerObjectPool = QueueBasedObjectPool.ofRecyclable(
            AtomicQueueFactory.<ResponseListenerWrapper>newQueue(createBoundedMpmc(MAX_POOLED_ELEMENTS)),
            false,
            new ResponseListenerAllocator());
    }

    private static final String DUMMY_NAME = "";
    private final WeakMap<Object,String> clusterName = WeakConcurrent.buildMap();

    /**
     * Starts the cluster name capture, if needed. Will return {@literal true} only for the 1st call for a given key.
     * Will also set `service.target.name` from cached value if there is such.
     *
     * @param cacheKey cache key
     * @param span     active span, if there is such
     * @return {@literal true} if cluster name needs to be fetched, {@literal false} otherwise
     */
    public boolean startGetClusterName(Object cacheKey, @Nullable Span span) {
        if (span == null) {
            return false;
        }
        String name = clusterName.putIfAbsent(cacheKey, DUMMY_NAME);
        if (name == null) {
            // no cached value
            return true;
        }

        if (name != DUMMY_NAME) {
            // use cached value when available
            span.getContext().getServiceTarget().withName(name);
        }
        return false;
    }

    /**
     * Ends the cluster name capture, should only be called once after {@link  #startGetClusterName(Object, Span)}
     * returned  {@literal true}.
     *
     * @param cacheKey   cache key
     * @param fetchReply function to retrieve reply content
     */
    public void endGetClusterName(Object cacheKey, Callable<InputStream> fetchReply){
        try {
            InputStream jsonInput = fetchReply.call();
            if (jsonInput == null) {
                logger.warn("unable to capture ES cluster name");
                return;
            }

            byte[] buffer = new byte[1024];
            DslJson<Object> dslJson = new DslJson<>(new DslJson.Settings<>());
            JsonReader<Object> reader = dslJson.newReader(jsonInput, buffer);
            reader.startObject();
            Map<String,Object> map = (Map<String, Object>) ObjectConverter.deserializeObject(reader);
            Object value = map.get("cluster_name");
            if(value instanceof String) {
                String name = (String) value;
                clusterName.put(cacheKey, name);
            }

        } catch (Exception e) {
            logger.warn("unable to capture ES cluster name", e);
        }
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
            span.getContext().getServiceTarget().withType(ELASTICSEARCH);
        }
        return span;
    }

    public void finishClientSpan(@Nullable Response response, Span span, @Nullable Throwable t, RestClient restClient){
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

                // response header has higher priority than cached value
                String cluster = response.getHeader("x-found-handling-cluster");
                if (cluster == null) {
                    String cachedCluster = clusterName.get(restClient);
                    cluster = cachedCluster != DUMMY_NAME ? cachedCluster : null;
                }
                span.getContext().getServiceTarget().withName(cluster);

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

    public ResponseListener wrapResponseListener(ResponseListener listener, Span span, RestClient restClient) {
        return responseListenerObjectPool.createInstance().with(listener, span, restClient);
    }

    void recycle(ResponseListenerWrapper listenerWrapper) {
        responseListenerObjectPool.recycle(listenerWrapper);
    }
}
