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
import co.elastic.apm.agent.impl.error.ErrorCapture;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.TraceContext;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.objectpool.impl.QueueBasedObjectPool;
import co.elastic.apm.agent.tracer.pooling.Allocator;
import co.elastic.apm.agent.tracer.pooling.Recyclable;
import org.jctools.queues.atomic.MpmcAtomicArrayQueue;

public class ObjectPoolFactory implements co.elastic.apm.agent.tracer.pooling.ObjectPoolFactory {

    @Override
    public <T extends Recyclable> ObjectPool<T> createRecyclableObjectPool(int maxCapacity, Allocator<T> allocator) {
        return QueueBasedObjectPool.ofRecyclable(new MpmcAtomicArrayQueue<T>((maxCapacity)), false, allocator);
    }

    public ObjectPool<Transaction> createTransactionPool(int maxCapacity, final ElasticApmTracer tracer) {
        return createRecyclableObjectPool(maxCapacity, new Allocator<Transaction>() {
            @Override
            public Transaction createInstance() {
                return new Transaction(tracer);
            }
        });
    }

    public ObjectPool<Span> createSpanPool(int maxCapacity, final ElasticApmTracer tracer) {
        return createRecyclableObjectPool(maxCapacity, new Allocator<Span>() {
            @Override
            public Span createInstance() {
                return new Span(tracer);
            }
        });
    }

    public ObjectPool<ErrorCapture> createErrorPool(int maxCapacity, final ElasticApmTracer tracer) {
        return createRecyclableObjectPool(maxCapacity, new Allocator<ErrorCapture>() {
            @Override
            public ErrorCapture createInstance() {
                return new ErrorCapture(tracer);
            }
        });
    }

    public ObjectPool<TraceContext> createSpanLinkPool(int maxCapacity, final ElasticApmTracer tracer) {
        return createRecyclableObjectPool(maxCapacity, new Allocator<TraceContext>() {
            @Override
            public TraceContext createInstance() {
                return TraceContext.with64BitId(tracer);
            }
        });
    }
}
