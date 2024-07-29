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
package co.elastic.apm.agent.objectpool;

import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.error.ErrorCaptureImpl;
import co.elastic.apm.agent.impl.transaction.SpanImpl;
import co.elastic.apm.agent.impl.transaction.TraceContextImpl;
import co.elastic.apm.agent.impl.transaction.TransactionImpl;
import co.elastic.apm.agent.objectpool.impl.QueueBasedObjectPool;
import co.elastic.apm.agent.sdk.internal.pooling.ObjectPooling;
import co.elastic.apm.agent.tracer.pooling.Allocator;
import co.elastic.apm.agent.tracer.pooling.Recyclable;
import org.jctools.queues.atomic.MpmcAtomicArrayQueue;

import java.util.concurrent.Callable;

import static co.elastic.apm.agent.objectpool.ObjectHandleImpl.NOOP_RESETTER;

public class ObjectPoolFactoryImpl implements co.elastic.apm.agent.tracer.pooling.ObjectPoolFactory, ObjectPooling.ObjectPoolFactory {

    private static final int DEFAULT_RESOURCE_POOL_SIZE = Math.max(16, Runtime.getRuntime().availableProcessors() * 2);


    @Override
    public <T extends Recyclable> ObservableObjectPool<T> createRecyclableObjectPool(int maxCapacity, Allocator<T> allocator) {
        return QueueBasedObjectPool.ofRecyclable(new MpmcAtomicArrayQueue<T>((maxCapacity)), false, allocator);
    }

    @Override
    public <T> ObservableObjectPool<ObjectHandleImpl<T>> createHandlePool(Allocator<T> allocator) {
        ObjectHandleImpl.Allocator<T> handleAlloc = new ObjectHandleImpl.Allocator<T>(allocator);
        QueueBasedObjectPool<ObjectHandleImpl<T>> result = QueueBasedObjectPool.of(new MpmcAtomicArrayQueue<ObjectHandleImpl<T>>((DEFAULT_RESOURCE_POOL_SIZE)), false, handleAlloc, NOOP_RESETTER);
        handleAlloc.setPool(result);
        return result;
    }

    @Override
    public <T> ObservableObjectPool<ObjectHandleImpl<T>> createHandlePool(final Callable<T> allocator) {
        return createHandlePool(new Allocator<T>() {
            @Override
            public T createInstance() {
                try {
                    return allocator.call();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }

    public ObservableObjectPool<TransactionImpl> createTransactionPool(int maxCapacity, final ElasticApmTracer tracer) {
        return createRecyclableObjectPool(maxCapacity, new Allocator<TransactionImpl>() {
            @Override
            public TransactionImpl createInstance() {
                return new TransactionImpl(tracer);
            }
        });
    }

    public ObservableObjectPool<SpanImpl> createSpanPool(int maxCapacity, final ElasticApmTracer tracer) {
        return createRecyclableObjectPool(maxCapacity, new Allocator<SpanImpl>() {
            @Override
            public SpanImpl createInstance() {
                return new SpanImpl(tracer);
            }
        });
    }

    public ObservableObjectPool<ErrorCaptureImpl> createErrorPool(int maxCapacity, final ElasticApmTracer tracer) {
        return createRecyclableObjectPool(maxCapacity, new Allocator<ErrorCaptureImpl>() {
            @Override
            public ErrorCaptureImpl createInstance() {
                return new ErrorCaptureImpl(tracer);
            }
        });
    }

    public ObservableObjectPool<TraceContextImpl> createSpanLinkPool(int maxCapacity, final ElasticApmTracer tracer) {
        return createRecyclableObjectPool(maxCapacity, new Allocator<TraceContextImpl>() {
            @Override
            public TraceContextImpl createInstance() {
                return TraceContextImpl.with64BitId(tracer);
            }
        });
    }

}
