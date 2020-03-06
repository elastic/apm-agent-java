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
package co.elastic.apm.agent.rocketmq.helper;

import co.elastic.apm.agent.configuration.CoreConfiguration;
import co.elastic.apm.agent.configuration.MessagingConfiguration;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.context.Message;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.matcher.WildcardMatcher;
import org.apache.rocketmq.common.message.MessageExt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.Map;

public class ConsumeMessageIteratorWrapper implements Iterator<MessageExt> {

    private static final Logger logger = LoggerFactory.getLogger(ConsumeMessageIteratorWrapper.class);

    private final Iterator<MessageExt> delegate;

    private final ElasticApmTracer tracer;

    private final CoreConfiguration coreConfiguration;

    private final MessagingConfiguration messagingConfiguration;

    ConsumeMessageIteratorWrapper(Iterator<MessageExt> delegate, ElasticApmTracer tracer) {
        this.delegate = delegate;
        this.tracer = tracer;
        this.coreConfiguration = tracer.getConfig(CoreConfiguration.class);
        this.messagingConfiguration = tracer.getConfig(MessagingConfiguration.class);
    }

    @Override
    public boolean hasNext() {
        endCurrentMessagingTransaction();
        return delegate.hasNext();
    }

    @Override
    public MessageExt next() {
        endCurrentMessagingTransaction();
        MessageExt messageExt = delegate.next();
        startMessagingTransaction(messageExt);
        return messageExt;
    }

    @Override
    public void remove() {
        delegate.remove();
    }

    private void endCurrentMessagingTransaction() {
        try {
            Transaction transaction = tracer.currentTransaction();
            if (transaction != null && "messaging".equals(transaction.getType())) {
                transaction.deactivate().end();
            }
        } catch (Exception e) {
            logger.error("Error in RocketMQ iterator wrapper", e);
        }
    }

    private void startMessagingTransaction(MessageExt messageExt) {
        try {
            String topic = messageExt.getTopic();
            if (!WildcardMatcher.isAnyMatch(messagingConfiguration.getIgnoreMessageQueues(), topic)) {
                Transaction transaction = tracer.startChildTransaction(messageExt,
                    RocketMQMessageHeaderAccessor.getInstance(),
                    ConsumeMessageIteratorWrapper.class.getClassLoader());
                if (transaction != null) {
                    transaction.withType("messaging")
                        .withName("RocketMQ Consume Message#" + topic)
                        .activate();

                    Message messageContext = transaction.getContext().getMessage();
                    messageContext.withQueue(topic).withAge(System.currentTimeMillis() - messageExt.getBornTimestamp());

                    if (transaction.isSampled() && coreConfiguration.isCaptureHeaders()) {
                        for (Map.Entry<String, String> property: messageExt.getProperties().entrySet()) {
                            messageContext.addHeader( property.getKey(), property.getValue());
                        }
                    }

                    if (transaction.isSampled() && coreConfiguration.getCaptureBody() != CoreConfiguration.EventType.OFF) {
                        messageContext.appendToBody("msgId=").appendToBody(messageExt.getMsgId()).appendToBody("; ")
                            .appendToBody("body=").appendToBody(new String(messageExt.getBody()));
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error in transaction creation based on RocketMQ message", e);
        }
    }

}
