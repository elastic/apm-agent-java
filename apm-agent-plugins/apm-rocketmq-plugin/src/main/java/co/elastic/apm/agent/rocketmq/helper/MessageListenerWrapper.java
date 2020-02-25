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

import co.elastic.apm.agent.configuration.MessagingConfiguration;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.matcher.WildcardMatcher;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.common.message.MessageExt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.List;

abstract class MessageListenerWrapper<S, C> {

    protected Logger logger = LoggerFactory.getLogger(MessageListenerWrapper.class);

    @Nonnull
    private ElasticApmTracer tracer;

    MessageListenerWrapper(@Nonnull ElasticApmTracer tracer) {
        this.tracer = tracer;
    }

    S doConsumeMessage(List<MessageExt> msgs, C context){
        Transaction consumeTrans = startConsumeTrans(msgs);
        S ret = null;
        try {
            ret = consumeDelegateImpl(msgs, context);
            return ret;
        } catch (Exception exp) {
            consumeTrans.captureException(exp);
            throw exp;
        } finally {
            endConsumeTrans(consumeTrans, ret);
        }
    }

    private Transaction startConsumeTrans(List<MessageExt> msgs) {
        Transaction transaction = null;
        try {
            MessageExt firstMsgExt = msgs.get(0);
            String topic = firstMsgExt.getTopic();
            if (!ignoreTopic(topic)) {
                transaction = tracer.startRootTransaction(null)
                    .withType("messaging")
                    .withName("RocketMQ Consume Message#" + topic)
                    .activate();
                transaction.getContext().getMessage()
                    .withQueue(topic)
                    .withAge(System.currentTimeMillis() - firstMsgExt.getBornTimestamp());
            }
        } catch (Exception e) {
            logger.error("Error in RocketMQ consume transaction creation", e);
        }
        return transaction;
    }

    private boolean ignoreTopic(String topic) {
        MessagingConfiguration messagingConfiguration = tracer.getConfig(MessagingConfiguration.class);
        return WildcardMatcher.isAnyMatch(messagingConfiguration.getIgnoreMessageQueues(), topic);
    }

    private void endConsumeTrans(Transaction transaction, Object status) {
        try {
            if (transaction != null && "messaging".equals(transaction.getType())) {
                if (status instanceof ConsumeConcurrentlyStatus) {
                    transaction.withResult(((ConsumeConcurrentlyStatus)status).name());
                }
                transaction.deactivate().end();
            }
        } catch (Exception e) {
            logger.error("Error in RocketMQ consume transaction creation.", e);
        }
    }

    abstract S consumeDelegateImpl(List<MessageExt> msgs, C context);

}


