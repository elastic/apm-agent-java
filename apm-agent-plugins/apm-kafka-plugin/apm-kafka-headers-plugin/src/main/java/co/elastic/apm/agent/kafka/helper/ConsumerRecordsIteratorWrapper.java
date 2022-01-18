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
package co.elastic.apm.agent.kafka.helper;

import co.elastic.apm.agent.configuration.CoreConfiguration;
import co.elastic.apm.agent.configuration.MessagingConfiguration;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.context.Message;
import co.elastic.apm.agent.impl.transaction.TraceContext;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.matcher.WildcardMatcher;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.record.TimestampType;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;

import java.util.Iterator;

class ConsumerRecordsIteratorWrapper implements Iterator<ConsumerRecord<?, ?>> {

    public static final Logger logger = LoggerFactory.getLogger(ConsumerRecordsIteratorWrapper.class);
    public static final String FRAMEWORK_NAME = "Kafka";

    private final Iterator<ConsumerRecord<?, ?>> delegate;
    private final ElasticApmTracer tracer;
    private final CoreConfiguration coreConfiguration;
    private final MessagingConfiguration messagingConfiguration;

    public ConsumerRecordsIteratorWrapper(Iterator<ConsumerRecord<?, ?>> delegate, ElasticApmTracer tracer) {
        this.delegate = delegate;
        this.tracer = tracer;
        coreConfiguration = tracer.getConfig(CoreConfiguration.class);
        messagingConfiguration = tracer.getConfig(MessagingConfiguration.class);
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
    public ConsumerRecord<?, ?> next() {
        endCurrentTransaction();
        ConsumerRecord<?, ?> record = delegate.next();
        try {
            String topic = record.topic();
            if (!WildcardMatcher.isAnyMatch(messagingConfiguration.getIgnoreMessageQueues(), topic)) {
                Transaction transaction = tracer.startChildTransaction(record, KafkaRecordHeaderAccessor.instance(), ConsumerRecordsIteratorWrapper.class.getClassLoader());
                if (transaction != null) {
                    transaction.withType("messaging").withName("Kafka record from " + topic).activate();
                    transaction.setFrameworkName(FRAMEWORK_NAME);

                    Message message = transaction.getContext().getMessage();
                    message.withQueue(topic);
                    if (record.timestampType() == TimestampType.CREATE_TIME) {
                        message.withAge(System.currentTimeMillis() - record.timestamp());
                    }

                    if (transaction.isSampled() && coreConfiguration.isCaptureHeaders()) {
                        for (Header header : record.headers()) {
                            String key = header.key();
                            if (!TraceContext.TRACE_PARENT_BINARY_HEADER_NAME.equals(key) &&
                                WildcardMatcher.anyMatch(coreConfiguration.getSanitizeFieldNames(), key) == null) {
                                message.addHeader(key, header.value());
                            }
                        }
                    }

                    if (transaction.isSampled() && coreConfiguration.getCaptureBody() != CoreConfiguration.EventType.OFF) {
                        message.appendToBody("key=").appendToBody(String.valueOf(record.key())).appendToBody("; ")
                            .appendToBody("value=").appendToBody(String.valueOf(record.value()));
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
