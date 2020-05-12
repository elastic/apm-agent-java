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
package co.elastic.apm.agent.rocketmq.instrumentation.consumer.push;

import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.rocketmq.helper.RocketMQInstrumentationHelper;
import co.elastic.apm.agent.rocketmq.instrumentation.BaseRocketMQInstrumentation;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.rocketmq.client.consumer.MQConsumer;
import org.apache.rocketmq.client.consumer.PullCallback;
import org.apache.rocketmq.client.consumer.PullResult;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.consumer.listener.MessageListenerOrderly;
import org.apache.rocketmq.client.impl.CommunicationMode;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.common.message.MessageQueue;

import javax.annotation.Nullable;
import java.util.List;

import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;

/**
 *  Create a transaction for each message.
 *  When DefaultMQPushConsumer.consumeMessageBatchMaxSize = 1（msgs.size() == 1）,
 *  create a transaction directly around the method and collect the consume results.
 *  In another case, we will replace the argument 'msgs' with {@link co.elastic.apm.agent.rocketmq.helper.ConsumeMessageListWrapper},
 *  so that a transaction will be started when the message is polled by the iterator.
 */
public class RocketMQMessageListenerConcurrentlyInstrumentation extends BaseRocketMQInstrumentation {

    public RocketMQMessageListenerConcurrentlyInstrumentation(ElasticApmTracer tracer) {
        super(tracer);
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return not(isInterface())
        .and(hasSuperType(named("org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently")));
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("consumeMessage");
    }

    @Override
    public Class<?> getAdviceClass() {
        return MessageListenerConcurrentlyAdvice.class;
    }

    public static class MessageListenerConcurrentlyAdvice {

        @Advice.OnMethodEnter(suppress = Throwable.class)
        private static void onEnter(@Advice.Local("transaction") Transaction transaction,
                                    @Advice.Argument(value = 0, readOnly = false) List<MessageExt> msgs) {
            if (tracer == null || !tracer.isRunning() || tracer.currentTransaction() != null || helperClassManager == null) {
                return;
            }

            final RocketMQInstrumentationHelper<Message, MessageQueue, CommunicationMode, SendCallback, MessageListenerConcurrently,
                MessageListenerOrderly, PullResult, PullCallback, MessageExt> helper = helperClassManager.getForClassLoaderOfClass(MQConsumer.class);
            if (helper == null) {
                return;
            }

            // by default, DefaultMQPushConsumer.consumeMessageBatchMaxSize=1
            // create a transaction around the method so that the re make it
            if (msgs.size() == 1) {
                transaction = helper.onConsumeStart(msgs.get(0));
            } else if (msgs.size() > 1) {
                msgs = helper.wrapMessageList(msgs);
            }
        }

        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
        private static void onExit(@Advice.Thrown Throwable thrown,
                                   @Nullable @Advice.Local("transaction") Transaction transaction,
                                   @Advice.Return ConsumeConcurrentlyStatus status) {
            if (transaction != null && helperClassManager != null) {
                final RocketMQInstrumentationHelper<Message, MessageQueue, CommunicationMode, SendCallback, MessageListenerConcurrently,
                    MessageListenerOrderly, PullResult, PullCallback, MessageExt> helper = helperClassManager.getForClassLoaderOfClass(MQConsumer.class);
                if (helper != null) {
                    helper.onConsumeEnd(transaction, thrown, status);
                }
            }
        }
    }
}
