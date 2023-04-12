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
package co.elastic.apm.agent.awssdk.common;


import co.elastic.apm.agent.tracer.AbstractSpan;
import co.elastic.apm.agent.tracer.Span;
import co.elastic.apm.agent.tracer.Tracer;
import co.elastic.apm.agent.tracer.Transaction;
import co.elastic.apm.agent.tracer.dispatch.TextHeaderGetter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.Iterator;

import static co.elastic.apm.agent.awssdk.common.AbstractSQSInstrumentationHelper.MESSAGE_PROCESSING_ACTION;
import static co.elastic.apm.agent.awssdk.common.AbstractSQSInstrumentationHelper.MESSAGING_TYPE;
import static co.elastic.apm.agent.awssdk.common.AbstractSQSInstrumentationHelper.SQS_TYPE;

public abstract class AbstractMessageIteratorWrapper<Message> implements Iterator<Message> {

    public static final Logger logger = LoggerFactory.getLogger(AbstractMessageIteratorWrapper.class);

    private final Iterator<Message> delegate;
    private final Tracer tracer;
    private final String queueName;
    private final AbstractSQSInstrumentationHelper<?, ?, Message> sqsInstrumentationHelper;
    private final TextHeaderGetter<Message> textHeaderGetter;

    public AbstractMessageIteratorWrapper(Iterator<Message> delegate, Tracer tracer,
                                          String queueName,
                                          AbstractSQSInstrumentationHelper<?, ?, Message> sqsInstrumentationHelper,
                                          TextHeaderGetter<Message> textHeaderGetter) {
        this.delegate = delegate;
        this.tracer = tracer;
        this.queueName = queueName;
        this.sqsInstrumentationHelper = sqsInstrumentationHelper;
        this.textHeaderGetter = textHeaderGetter;
    }

    @Override
    public boolean hasNext() {
        endCurrentTransaction();
        endMessageProcessingSpan();
        return delegate.hasNext();
    }

    @Nullable
    public Transaction<?> endCurrentTransaction() {
        Transaction<?> transaction = null;
        try {
            transaction = tracer.currentTransaction();
            if (transaction != null && MESSAGING_TYPE.equals(transaction.getType())) {
                transaction.deactivate().end();
                return null;
            }
        } catch (Exception e) {
            logger.error("Error in AWS SQS iterator wrapper", e);
        }
        return transaction;
    }

    public void endMessageProcessingSpan() {
        AbstractSpan<?> active = tracer.getActive();
        if (!(active instanceof Span<?>)) {
            return;
        }

        Span<?> span = (Span<?>) active;
        if (span.getType() != null && span.getType().equals(MESSAGING_TYPE)
            && span.getSubtype() != null && span.getSubtype().equals(SQS_TYPE)
            && span.getAction() != null && span.getAction().equals(MESSAGE_PROCESSING_ACTION)) {
            span.deactivate().end();
        }
    }

    @Override
    public Message next() {
        Transaction<?> currentTransaction = endCurrentTransaction();
        Message sqsMessage = delegate.next();
        if (currentTransaction == null) {
            sqsInstrumentationHelper.startTransactionOnMessage(sqsMessage, queueName, textHeaderGetter);
        }
        return sqsMessage;
    }


    @Override
    public void remove() {
        delegate.remove();
    }
}
