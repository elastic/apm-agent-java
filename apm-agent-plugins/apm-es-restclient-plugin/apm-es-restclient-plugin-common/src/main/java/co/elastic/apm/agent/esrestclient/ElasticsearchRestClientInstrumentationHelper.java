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

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class ElasticsearchRestClientInstrumentationHelper {

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

    private final HttpClientAdapter httpClientAdapter;

    private static final String DUMMY_CLUSTER_NAME = "";
    private final WeakMap<Object, String> clusterNames = WeakConcurrent.buildMap();

    protected ElasticsearchRestClientInstrumentationHelper(ElasticApmTracer tracer, HttpClientAdapter httpClientAdapter) {
        this.tracer = tracer;
        this.responseListenerObjectPool = tracer.getObjectPoolFactory().createRecyclableObjectPool(MAX_POOLED_ELEMENTS, new ResponseListenerAllocator());
        this.httpClientAdapter = httpClientAdapter;
    }

    @Nullable
    private String getOrFetchClusterName(final RestClient restClient, Object requestHeadersOrOptions) {
        // While there is already an in-progress request, the dummy value will prevent issuing more than one request to
        // Elasticsearch.
        String name = clusterNames.putIfAbsent(restClient, DUMMY_CLUSTER_NAME);
        if (name != null) {
            return name != DUMMY_CLUSTER_NAME ? name : null;
        }

        final CountDownLatch end = new CountDownLatch(1);
        httpClientAdapter.performRequestAsync(restClient, "GET", "/", requestHeadersOrOptions, new ResponseListener() {
            @Override
            public void onSuccess(Response response) {
                String clusterName = null;
                try {
                    byte[] buffer = new byte[1024];
                    DslJson<Object> dslJson = new DslJson<>(new DslJson.Settings<>());
                    JsonReader<Object> reader = dslJson.newReader(response.getEntity().getContent(), buffer);
                    reader.startObject();
                    Map<String, Object> map = (Map<String, Object>) ObjectConverter.deserializeObject(reader);
                    Object value = map.get("cluster_name");
                    if (value instanceof String) {
                        clusterName = (String) value;
                        clusterNames.put(restClient, clusterName);
                    }
                } catch (IOException e) {
                    logger.warn("unable to retrieve cluster name", e);
                } finally {
                    logger.debug("cluster name = {}", clusterName);
                    end.countDown();
                }
            }

            @Override
            public void onFailure(Exception e) {
                logger.warn("unable to retrieve cluster name", e);
                end.countDown();
            }
        });

        try {
            // Because retrieving cluster name is executed after the application request completes, it slows down the
            // first request to ES. In order to limit that potential impact we have to enforce a time budget, which
            // means that if the response is within budget the cluster name is properly captured, if it is not, then
            // one or more requests to ES will not have their cluster name set.
            if (!end.await(30, TimeUnit.MILLISECONDS)) {
                logger.warn("giving up on retrieving cluster name");
            }
        } catch (InterruptedException e) {
            // silently ignored
        }

        return clusterNames.get(restClient);
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

    public void finishClientSpan(@Nullable Response response, Span span, @Nullable Throwable t, RestClient restClient, Object requestHeadersOrOptions) {
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

                // response header has higher priority than fetched/cached value
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

            if (cluster == null) {
                cluster = getOrFetchClusterName(restClient, requestHeadersOrOptions);
            }
            span.getContext().getServiceTarget().withName(cluster);
        } finally {
            span.end();
        }
    }

    public ResponseListener wrapResponseListener(ResponseListener listener, Span span, RestClient restClient, Object requestHeadersOrOptions) {
        return responseListenerObjectPool.createInstance().with(listener, span, restClient, requestHeadersOrOptions);
    }

    void recycle(ResponseListenerWrapper listenerWrapper) {
        responseListenerObjectPool.recycle(listenerWrapper);
    }

    public interface HttpClientAdapter {

        /**
         * Executes an HTTP request asynchronously
         *
         * @param restClient              ES REST client
         * @param method                  HTTP method
         * @param endpoint                ES endpoint path
         * @param headersOrRequestOptions Request headers or options
         * @param responseListener        response listener
         */
        void performRequestAsync(RestClient restClient, String method, String endpoint, Object headersOrRequestOptions, ResponseListener responseListener);
    }

}
