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
package co.elastic.apm.agent.rabbitmq;


import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.util.Iterator;

public class MessageBatchIteratorWrapper implements Iterator<Message> {

    public static final Logger logger = LoggerFactory.getLogger(MessageBatchIteratorWrapper.class);

    private final Iterator<Message> delegate;
    private final ElasticApmTracer tracer;
    private final SpringAmqpTransactionHelper transactionHelper;

    public MessageBatchIteratorWrapper(Iterator<Message> delegate, ElasticApmTracer tracer, SpringAmqpTransactionHelper transactionHelper) {
        this.delegate = delegate;
        this.tracer = tracer;
        this.transactionHelper = transactionHelper;
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
            logger.error("Error in Spring AMQP iterator wrapper", e);
        }
    }

    @Override
    public Message next() {
        endCurrentTransaction();

        Message message = delegate.next();
        try {
            MessageProperties messageProperties = message.getMessageProperties();
            transactionHelper.createTransaction(message, messageProperties, AmqpConstants.SPRING_AMQP_TRANSACTION_PREFIX);
        } catch (Throwable throwable) {
            logger.error("Error in transaction creation based on Spring AMQP batch message", throwable);
        }
        return message;
    }

    @Override
    public void remove() {
        delegate.remove();
    }
}
