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


import co.elastic.apm.agent.httpclient.common.ApacheHttpClientAsyncHelper;
import co.elastic.apm.agent.tracer.TraceState;
import co.elastic.apm.agent.tracer.GlobalTracer;
import co.elastic.apm.agent.tracer.Span;
import co.elastic.apm.agent.tracer.Tracer;
import co.elastic.apm.agent.tracer.pooling.Allocator;
import co.elastic.apm.agent.tracer.pooling.ObjectPool;
import co.elastic.apm.agent.tracer.pooling.ObjectPoolFactory;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.nio.AsyncRequestProducer;
import org.apache.hc.core5.http.nio.RequestChannel;
import org.apache.hc.core5.http.protocol.HttpContext;

import javax.annotation.Nullable;

public class ApacheHttpClient5AsyncHelper implements ApacheHttpClientAsyncHelper<AsyncRequestProducer, AsyncRequestProducerWrapper, FutureCallback, FutureCallbackWrapper<?>, HttpContext> {

    private static final int MAX_POOLED_ELEMENTS = 256;

    private final ObjectPool<FutureCallbackWrapper<?>> futureCallbackWrapperObjectPool;
    private final ObjectPool<RequestChannelWrapper> requestChannelWrapperObjectPool;

    public ApacheHttpClient5AsyncHelper() {
        Tracer tracer = GlobalTracer.get();

        ObjectPoolFactory factory = tracer.getObjectPoolFactory();
        futureCallbackWrapperObjectPool = factory.createRecyclableObjectPool(MAX_POOLED_ELEMENTS, new FutureCallbackWrapperAllocator());
        requestChannelWrapperObjectPool = factory.createRecyclableObjectPool(MAX_POOLED_ELEMENTS, new RequestChannelWrapperAllocator());
    }

    private class FutureCallbackWrapperAllocator implements Allocator<FutureCallbackWrapper<?>> {
        @Override
        public FutureCallbackWrapper<?> createInstance() {
            return new FutureCallbackWrapper<>(ApacheHttpClient5AsyncHelper.this);
        }
    }

    private static class RequestChannelWrapperAllocator implements Allocator<RequestChannelWrapper> {
        @Override
        public RequestChannelWrapper createInstance() {
            return new RequestChannelWrapper();
        }
    }

    public AsyncRequestProducerWrapper wrapRequestProducer(AsyncRequestProducer requestProducer,
                                                           @Nullable Span<?> span,
                                                           TraceState<?> toPropagate) {
        // with 5.x async request producer is not pooled
        return new AsyncRequestProducerWrapper(this, requestProducer, span, toPropagate);
    }

    @Override
    public FutureCallbackWrapper<?> wrapFutureCallback(FutureCallback futureCallback, HttpContext context, Span<?> span) {
        return futureCallbackWrapperObjectPool.createInstance().with(futureCallback, context, span);
    }

    @Override
    public void failedBeforeRequestStarted(FutureCallbackWrapper<?> cb, Throwable t) {
        cb.failedWithoutExecution(t);
    }

    public RequestChannelWrapper wrapRequestChannel(RequestChannel requestChannel, @Nullable Span<?> span, @Nullable TraceState<?> toPropagate) {
        return requestChannelWrapperObjectPool.createInstance().with(requestChannel, span, toPropagate);
    }

    public void recycle(AsyncRequestProducerWrapper requestProducerWrapper) {
        // no-op as with 5.x async request producer is not pooled
    }

    void recycle(FutureCallbackWrapper<?> futureCallbackWrapper) {
        futureCallbackWrapperObjectPool.recycle(futureCallbackWrapper);
    }

    void recycle(RequestChannelWrapper requestChannelWrapper) {
        requestChannelWrapperObjectPool.recycle(requestChannelWrapper);
    }
}
