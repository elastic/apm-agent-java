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

import co.elastic.apm.agent.impl.GlobalTracer;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.objectpool.Allocator;
import co.elastic.apm.agent.objectpool.ObjectPool;
import co.elastic.apm.agent.objectpool.ObjectPoolFactory;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.nio.AsyncRequestProducer;
import org.apache.hc.core5.http.nio.RequestChannel;
import org.apache.hc.core5.http.protocol.HttpContext;

import javax.annotation.Nullable;

public class ApacheHttpAsyncClientHelper {

    private static final int MAX_POOLED_ELEMENTS = 256;

    private final ObjectPool<AsyncRequestProducerWrapper> requestProducerWrapperObjectPool;
    private final ObjectPool<FutureCallbackWrapper<?>> futureCallbackWrapperObjectPool;

    private final ObjectPool<RequestChannelWrapper> requestChannelWrapperObjectPool;

    public ApacheHttpAsyncClientHelper() {
        ObjectPoolFactory factory = GlobalTracer.requireTracerImpl().getObjectPoolFactory();
        requestProducerWrapperObjectPool = factory.createRecyclableObjectPool(MAX_POOLED_ELEMENTS, new RequestProducerWrapperAllocator());
        futureCallbackWrapperObjectPool = factory.createRecyclableObjectPool(MAX_POOLED_ELEMENTS, new FutureCallbackWrapperAllocator());
        requestChannelWrapperObjectPool = factory.createRecyclableObjectPool(MAX_POOLED_ELEMENTS, new RequestChannelWrapperAllocator());
    }

    private class RequestProducerWrapperAllocator implements Allocator<AsyncRequestProducerWrapper> {
        @Override
        public AsyncRequestProducerWrapper createInstance() {
            return new AsyncRequestProducerWrapper(ApacheHttpAsyncClientHelper.this);
        }
    }

    private class FutureCallbackWrapperAllocator implements Allocator<FutureCallbackWrapper<?>> {
        @Override
        public FutureCallbackWrapper<?> createInstance() {
            return new FutureCallbackWrapper(ApacheHttpAsyncClientHelper.this);
        }
    }

    private class RequestChannelWrapperAllocator implements Allocator<RequestChannelWrapper> {
        @Override
        public RequestChannelWrapper createInstance() {
            return new RequestChannelWrapper(ApacheHttpAsyncClientHelper.this);
        }
    }

    public AsyncRequestProducer wrapRequestProducer(AsyncRequestProducer requestProducer, @Nullable Span span,
                                                    @Nullable AbstractSpan<?> parent) {
        return requestProducerWrapperObjectPool.createInstance().with(requestProducer, span, parent);
    }

    public <T> FutureCallback<T> wrapFutureCallback(FutureCallback<T> futureCallback, HttpContext context, Span span) {
        return ((FutureCallbackWrapper<T>) futureCallbackWrapperObjectPool.createInstance()).with(futureCallback, context, span);
    }

    public RequestChannelWrapper wrapRequestChannel(RequestChannel requestChannel, @Nullable Span span, @Nullable AbstractSpan<?> parent) {
        return requestChannelWrapperObjectPool.createInstance().with(requestChannel, span, parent);
    }

    void recycle(AsyncRequestProducerWrapper requestProducerWrapper) {
        requestProducerWrapperObjectPool.recycle(requestProducerWrapper);
    }

    void recycle(FutureCallbackWrapper<?> futureCallbackWrapper) {
        futureCallbackWrapperObjectPool.recycle(futureCallbackWrapper);
    }

    void recycle(RequestChannelWrapper requestChannelWrapper) {
        requestChannelWrapperObjectPool.recycle(requestChannelWrapper);
    }
}