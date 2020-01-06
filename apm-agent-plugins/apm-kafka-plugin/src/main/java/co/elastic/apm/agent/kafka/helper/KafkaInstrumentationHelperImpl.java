/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2020 Elastic and contributors
 * %%
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
 * #L%
 */
package co.elastic.apm.agent.kafka.helper;

import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.objectpool.Allocator;
import co.elastic.apm.agent.objectpool.ObjectPool;
import co.elastic.apm.agent.objectpool.impl.QueueBasedObjectPool;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.Callback;
import org.jctools.queues.atomic.AtomicQueueFactory;

import javax.annotation.Nullable;

import java.util.Iterator;
import java.util.List;

import static org.jctools.queues.spec.ConcurrentQueueSpec.createBoundedMpmc;

@SuppressWarnings("rawtypes")
public class KafkaInstrumentationHelperImpl implements KafkaInstrumentationHelper<Callback, ConsumerRecord> {

    private final ObjectPool<CallbackWrapper> callbackWrapperObjectPool;
    private final ElasticApmTracer tracer;

    public KafkaInstrumentationHelperImpl(ElasticApmTracer tracer) {
        this.tracer = tracer;
        this.callbackWrapperObjectPool = QueueBasedObjectPool.ofRecyclable(
            AtomicQueueFactory.<CallbackWrapper>newQueue(createBoundedMpmc(256)),
            false,
            new CallbackWrapperAllocator()
        );
    }

    private final class CallbackWrapperAllocator implements Allocator<CallbackWrapper> {
        @Override
        public CallbackWrapper createInstance() {
            return new CallbackWrapper(KafkaInstrumentationHelperImpl.this);
        }
    }

    @Override
    public Callback wrapCallback(@Nullable Callback callback, Span span) {
        if (callback instanceof CallbackWrapper) {
            // don't wrap twice
            return callback;
        }
        return callbackWrapperObjectPool.createInstance().wrap(callback, span);
    }

    void recycle(CallbackWrapper callbackWrapper) {
        callbackWrapperObjectPool.recycle(callbackWrapper);
    }

    @Override
    public Iterator<ConsumerRecord> wrapConsumerRecordIterator(Iterator<ConsumerRecord> iterator) {
        return new ConsumerRecordsIteratorWrapper(iterator, tracer);
    }

    @Override
    public Iterable<ConsumerRecord> wrapConsumerRecordIterable(Iterable<ConsumerRecord> iterable) {
        return new ConsumerRecordsIterableWrapper(iterable, tracer);
    }

    @Override
    public List<ConsumerRecord> wrapConsumerRecordList(List<ConsumerRecord> list) {
        return new ConsumerRecordsListWrapper(list, tracer);
    }
}
