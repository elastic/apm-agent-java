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
package co.elastic.apm.agent.httpclient.helper;

import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.TextHeaderSetter;
import co.elastic.apm.agent.objectpool.Allocator;
import co.elastic.apm.agent.objectpool.ObjectPool;
import co.elastic.apm.agent.objectpool.impl.QueueBasedObjectPool;
import org.apache.http.HttpRequest;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.nio.protocol.HttpAsyncRequestProducer;
import org.apache.http.protocol.HttpContext;
import org.jctools.queues.atomic.AtomicQueueFactory;

import static org.jctools.queues.spec.ConcurrentQueueSpec.createBoundedMpmc;

public class ApacheHttpAsyncClientHelper {

    private static final int MAX_POOLED_ELEMENTS = 256;

    private final ObjectPool<HttpAsyncRequestProducerWrapper> requestProducerWrapperObjectPool;
    private final ObjectPool<FutureCallbackWrapper<?>> futureCallbackWrapperObjectPool;

    public ApacheHttpAsyncClientHelper() {
        requestProducerWrapperObjectPool = QueueBasedObjectPool.ofRecyclable(
            AtomicQueueFactory.<HttpAsyncRequestProducerWrapper>newQueue(createBoundedMpmc(MAX_POOLED_ELEMENTS)),
            false, new RequestProducerWrapperAllocator());

        futureCallbackWrapperObjectPool = QueueBasedObjectPool.ofRecyclable(
            AtomicQueueFactory.<FutureCallbackWrapper<?>>newQueue(createBoundedMpmc(MAX_POOLED_ELEMENTS)),
            false, new FutureCallbackWrapperAllocator());
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

    public HttpAsyncRequestProducer wrapRequestProducer(HttpAsyncRequestProducer requestProducer, Span span, TextHeaderSetter<HttpRequest> headerSetter) {
        return requestProducerWrapperObjectPool.createInstance().with(requestProducer, span, headerSetter);
    }

    public <T> FutureCallback<T> wrapFutureCallback(FutureCallback<T> futureCallback, HttpContext context, Span span) {
        return ((FutureCallbackWrapper<T>) futureCallbackWrapperObjectPool.createInstance()).with(futureCallback, context, span);
    }

    void recycle(HttpAsyncRequestProducerWrapper requestProducerWrapper) {
        requestProducerWrapperObjectPool.recycle(requestProducerWrapper);
    }

    void recycle(FutureCallbackWrapper<?> futureCallbackWrapper) {
        futureCallbackWrapperObjectPool.recycle(futureCallbackWrapper);
    }
}
