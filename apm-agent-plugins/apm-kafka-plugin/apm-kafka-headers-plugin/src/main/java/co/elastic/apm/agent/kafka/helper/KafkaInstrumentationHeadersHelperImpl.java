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
import co.elastic.apm.agent.impl.transaction.TraceContext;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.List;

@SuppressWarnings("rawtypes")
public class KafkaInstrumentationHeadersHelperImpl implements KafkaInstrumentationHeadersHelper<ConsumerRecord, ProducerRecord> {

    public static final Logger logger = LoggerFactory.getLogger(KafkaInstrumentationHeadersHelperImpl.class);

    private final ElasticApmTracer tracer;

    public KafkaInstrumentationHeadersHelperImpl(ElasticApmTracer tracer) {
        this.tracer = tracer;
    }

    @Override
    public Iterator<ConsumerRecord> wrapConsumerRecordIterator(Iterator<ConsumerRecord> consumerRecordIterator) {
        try {
            return new ConsumerRecordsIteratorWrapper(consumerRecordIterator, tracer);
        } catch (Throwable throwable) {
            logger.debug("Failed to wrap Kafka ConsumerRecords iterator", throwable);
            return consumerRecordIterator;
        }
    }

    @Override
    public Iterable<ConsumerRecord> wrapConsumerRecordIterable(Iterable<ConsumerRecord> consumerRecordIterable) {
        try {
            return new ConsumerRecordsIterableWrapper(consumerRecordIterable, tracer);
        } catch (Throwable throwable) {
            logger.debug("Failed to wrap Kafka ConsumerRecords", throwable);
            return consumerRecordIterable;
        }
    }

    @Override
    public List<ConsumerRecord> wrapConsumerRecordList(List<ConsumerRecord> consumerRecordList) {
        try {
            return new ConsumerRecordsListWrapper(consumerRecordList, tracer);
        } catch (Throwable throwable) {
            logger.debug("Failed to wrap Kafka ConsumerRecords list", throwable);
            return consumerRecordList;
        }
    }

    @Override
    public void setOutgoingTraceContextHeaders(Span span, ProducerRecord producerRecord) {
        span.setOutgoingTraceContextHeaders(producerRecord, KafkaRecordHeaderAccessor.instance());
    }

    @Override
    public void removeTraceContextHeader(ProducerRecord producerRecord) {
        TraceContext.removeTraceContextHeaders(producerRecord, KafkaRecordHeaderAccessor.instance());
    }
}
