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
import co.elastic.apm.agent.impl.context.Message;
import co.elastic.apm.agent.impl.transaction.TraceContext;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.util.BinaryHeaderMap;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.record.TimestampType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;

@SuppressWarnings("rawtypes")
class ConsumerRecordsIteratorWrapper implements Iterator<ConsumerRecord> {

    public static final Logger logger = LoggerFactory.getLogger(ConsumerRecordsIteratorWrapper.class);

    private final Iterator<ConsumerRecord> delegate;
    private final ElasticApmTracer tracer;

    public ConsumerRecordsIteratorWrapper(Iterator<ConsumerRecord> delegate, ElasticApmTracer tracer) {
        this.delegate = delegate;
        this.tracer = tracer;
    }

    @Override
    public boolean hasNext() {
        endCurrentTransaction();
        return delegate.hasNext();
    }

    public void endCurrentTransaction() {
        try {
            Transaction transaction = tracer.currentTransaction();
            if (transaction != null && "messaging".equals(transaction.getType())) {
                transaction.deactivate().end();
            }
        } catch (Exception e) {
            logger.error("Error in Kafka iterator wrapper", e);
        }
    }

    @Override
    public ConsumerRecord next() {
        endCurrentTransaction();
        ConsumerRecord record = delegate.next();
        try {
            Header traceParentHeader = record.headers().lastHeader(TraceContext.TRACE_PARENT_HEADER);
            Transaction transaction;
            if (traceParentHeader != null) {
                transaction = tracer.startTransaction(
                    TraceContext.fromTraceparentBinaryHeader(),
                    traceParentHeader.value(),
                    ConsumerRecordsIteratorWrapper.class.getClassLoader()
                );
            } else {
                transaction = tracer.startRootTransaction(ConsumerRecordsIteratorWrapper.class.getClassLoader());
            }
            transaction.withType("messaging").withName("Kafka record from " + record.topic()).activate();
            Message message = transaction.getContext().getMessage();
            message.withQueue(record.topic());
            if (record.timestampType() == TimestampType.CREATE_TIME) {
                message.withAge(System.currentTimeMillis() - record.timestamp());
            }
            // todo - add destination fields
            for (Header header : record.headers()) {
                if (!TraceContext.TRACE_PARENT_HEADER.equals(header.key())) {
                    try {
                        message.addHeader(header.key(), header.value());
                    } catch (BinaryHeaderMap.InsufficientCapacityException e) {
                        logger.error("Failed to trace Kafka header", e);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error in transaction creation based on Kafka record", e);
        }
        return record;
    }

    @Override
    public void remove() {
        delegate.remove();
    }
}
