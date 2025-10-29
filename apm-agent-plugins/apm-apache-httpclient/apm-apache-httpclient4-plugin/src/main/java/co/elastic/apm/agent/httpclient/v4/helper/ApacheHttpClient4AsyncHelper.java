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

import co.elastic.apm.agent.httpclient.common.ApacheHttpClientAsyncHelper;
import co.elastic.apm.agent.tracer.TraceState;
import co.elastic.apm.agent.tracer.GlobalTracer;
import co.elastic.apm.agent.tracer.Span;
import co.elastic.apm.agent.tracer.Tracer;
import co.elastic.apm.agent.tracer.pooling.Allocator;
import co.elastic.apm.agent.tracer.pooling.ObjectPool;
import co.elastic.apm.agent.tracer.pooling.ObjectPoolFactory;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.nio.protocol.HttpAsyncRequestProducer;
import org.apache.http.protocol.HttpContext;

import javax.annotation.Nullable;

public class ApacheHttpClient4AsyncHelper implements ApacheHttpClientAsyncHelper<HttpAsyncRequestProducer, HttpAsyncRequestProducerWrapper, FutureCallback, FutureCallbackWrapper<?>, HttpContext> {

    private static final int MAX_POOLED_ELEMENTS = 256;

    private final Tracer tracer;
    private final ObjectPool<HttpAsyncRequestProducerWrapper> requestProducerWrapperObjectPool;
    private final ObjectPool<FutureCallbackWrapper<?>> futureCallbackWrapperObjectPool;

    public ApacheHttpClient4AsyncHelper() {
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
            return new HttpAsyncRequestProducerWrapper(ApacheHttpClient4AsyncHelper.this);
        }
    }

    private class FutureCallbackWrapperAllocator implements Allocator<FutureCallbackWrapper<?>> {
        @Override
        public FutureCallbackWrapper<?> createInstance() {
            return new FutureCallbackWrapper<>(ApacheHttpClient4AsyncHelper.this);
        }
    }

    public HttpAsyncRequestProducerWrapper wrapRequestProducer(HttpAsyncRequestProducer requestProducer, @Nullable Span<?> span,
                                                               TraceState<?> toPropagate) {
        return requestProducerWrapperObjectPool.createInstance().with(requestProducer, span, toPropagate);
    }

    @Override
    public FutureCallbackWrapper<?> wrapFutureCallback(FutureCallback futureCallback, HttpContext context, Span<?> span) {
        return futureCallbackWrapperObjectPool.createInstance().with(futureCallback, context, span);
    }

    @Override
    public void failedBeforeRequestStarted(FutureCallbackWrapper<?> cb, Throwable t) {
        cb.failedWithoutExecution(t);
    }

    @Override
    public void recycle(HttpAsyncRequestProducerWrapper requestProducerWrapper) {
        requestProducerWrapperObjectPool.recycle(requestProducerWrapper);
    }

    void recycle(FutureCallbackWrapper<?> futureCallbackWrapper) {
        futureCallbackWrapperObjectPool.recycle(futureCallbackWrapper);
    }
}
