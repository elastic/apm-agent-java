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
package co.elastic.apm.agent.esrestclient.v6_4;

import co.elastic.apm.agent.esrestclient.ElasticsearchRestClientInstrumentationHelper;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.GlobalTracer;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.ResponseListener;
import org.elasticsearch.client.RestClient;

import javax.annotation.Nullable;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

public class ElasticsearchRestClientHelper extends ElasticsearchRestClientInstrumentationHelper {

    private static final Logger log = LoggerFactory.getLogger(ElasticsearchRestClientHelper.class);

    private static final ElasticsearchRestClientHelper INSTANCE = new ElasticsearchRestClientHelper(GlobalTracer.requireTracerImpl());

    public static ElasticsearchRestClientHelper get() {
        return INSTANCE;
    }

    private ElasticsearchRestClientHelper(ElasticApmTracer tracer) {
        super(tracer, new ClientAdapter());
    }

    private static class ClientAdapter implements HttpClientAdapter {

        @Override
        public void performRequestAsync(RestClient restClient, String method, String endpoint, Object headersOrRequestOptions, ResponseListener responseListener) {
            if (!(headersOrRequestOptions instanceof RequestOptions)) {
                throw new IllegalArgumentException("unexpected header type");
            }
            Request request = new Request(method, endpoint);
            request.setOptions(((RequestOptions) headersOrRequestOptions));

            invokeAsyncPerformRequest(restClient, request, responseListener);
        }

        private void invokeAsyncPerformRequest(RestClient restClient, Request request, ResponseListener responseListener) {

            if (methodHandle == null) {
                try {
                    // 7.x with Cancellable return type
                    Class<?> cancellableType = Class.forName("org.elasticsearch.client.Cancellable", false, RestClient.class.getClassLoader());
                    methodHandle = lookupMethod(cancellableType);
                } catch (ClassNotFoundException e) {
                    // silently ignored
                }

                if (methodHandle == null) {
                    // 6.x with void return type
                    methodHandle = lookupMethod(void.class);
                }
            }

            // in case method is not found, fallback on no-op as cluster name is not required
            if (methodHandle == null) {
                log.warn("unable to resolve performRequestAsync");
                return;
            }

            try {
                methodHandle.invoke(restClient, request, responseListener);
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }

        }

        @Nullable
        private MethodHandle methodHandle;

        @Nullable
        private MethodHandle lookupMethod(Class<?> returnType) {
            try {
                return MethodHandles.lookup().findVirtual(RestClient.class, "performRequestAsync", MethodType.methodType(returnType, Request.class, ResponseListener.class));
            } catch (NoSuchMethodException | IllegalAccessException e) {
                log.debug("unable to resolve method", e);
                return null;
            }
        }

    }


}
