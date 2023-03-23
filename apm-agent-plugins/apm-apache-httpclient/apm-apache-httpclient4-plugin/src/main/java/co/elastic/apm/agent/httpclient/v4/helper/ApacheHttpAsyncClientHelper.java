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

import co.elastic.apm.agent.tracer.GlobalTracer;
import co.elastic.apm.agent.tracer.AbstractSpan;
import co.elastic.apm.agent.tracer.Span;
import co.elastic.apm.agent.tracer.Tracer;
import co.elastic.apm.agent.tracer.pooling.Allocator;
import co.elastic.apm.agent.tracer.pooling.ObjectPool;
import co.elastic.apm.agent.tracer.pooling.ObjectPoolFactory;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.nio.protocol.HttpAsyncRequestProducer;
import org.apache.http.protocol.HttpContext;

import javax.annotation.Nullable;

public class ApacheHttpAsyncClientHelper {

    private static final int MAX_POOLED_ELEMENTS = 256;

    private final Tracer tracer;
    private final ObjectPool<HttpAsyncRequestProducerWrapper> requestProducerWrapperObjectPool;
    private final ObjectPool<FutureCallbackWrapper<?>> futureCallbackWrapperObjectPool;

    public ApacheHttpAsyncClientHelper() {
        tracer = GlobalTracer.get();
        ObjectPoolFactory factory = tracer.getObjectPoolFactory();
        requestProducerWrapperObjectPool = factory.createRecyclableObjectPool(MAX_POOLED_ELEMENTS, new RequestProducerWrapperAllocator());
        futureCallbackWrapperObjectPool = factory.createRecyclableObjectPool(MAX_POOLED_ELEMENTS, new FutureCallbackWrapperAllocator());
    }

    public Tracer getTracer() {
        return tracer;
    }

    private class RequestProducerWrapperAllocator implements Allocator<HttpAsyncRequestProducerWrapper> {
        @Override
        public HttpAsyncRequestProducerWrapper createInstance() {
            return new HttpAsyncRequestProducerWrapper(ApacheHttpAsyncClientHelper.this);
        }
    }

    private class FutureCallbackWrapperAllocator implements Allocator<FutureCallbackWrapper<?>> {
        @Override
        public FutureCallbackWrapper<?> createInstance() {
            return new FutureCallbackWrapper(ApacheHttpAsyncClientHelper.this);
        }
    }

    public HttpAsyncRequestProducer wrapRequestProducer(HttpAsyncRequestProducer requestProducer, @Nullable Span<?> span,
                                                        @Nullable AbstractSpan<?> parent) {
        return requestProducerWrapperObjectPool.createInstance().with(requestProducer, span, parent);
    }

    public <T> FutureCallback<T> wrapFutureCallback(FutureCallback<T> futureCallback, HttpContext context, Span<?> span) {
        return ((FutureCallbackWrapper<T>) futureCallbackWrapperObjectPool.createInstance()).with(futureCallback, context, span);
    }

    void recycle(HttpAsyncRequestProducerWrapper requestProducerWrapper) {
        requestProducerWrapperObjectPool.recycle(requestProducerWrapper);
    }

    void recycle(FutureCallbackWrapper<?> futureCallbackWrapper) {
        futureCallbackWrapperObjectPool.recycle(futureCallbackWrapper);
    }
}
