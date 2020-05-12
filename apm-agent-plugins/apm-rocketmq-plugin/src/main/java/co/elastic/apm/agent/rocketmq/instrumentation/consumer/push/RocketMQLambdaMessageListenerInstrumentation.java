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
import co.elastic.apm.agent.rocketmq.helper.RocketMQInstrumentationHelper;
import co.elastic.apm.agent.rocketmq.instrumentation.BaseRocketMQInstrumentation;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.rocketmq.client.consumer.MQConsumer;
import org.apache.rocketmq.client.consumer.PullCallback;
import org.apache.rocketmq.client.consumer.PullResult;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.consumer.listener.MessageListenerOrderly;
import org.apache.rocketmq.client.impl.CommunicationMode;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.common.message.MessageQueue;

import javax.annotation.Nullable;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;


/**
 * Wrap the message listener represented by lambda to make instrumentation work for it
 */
public abstract class RocketMQLambdaMessageListenerInstrumentation extends BaseRocketMQInstrumentation {

    public RocketMQLambdaMessageListenerInstrumentation(ElasticApmTracer tracer) {
        super(tracer);
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("org.apache.rocketmq.client.consumer.DefaultMQPushConsumer");
    }

    public static class RegisterMsgListenerConcurrentlyInstrumentation extends RocketMQLambdaMessageListenerInstrumentation {

        public RegisterMsgListenerConcurrentlyInstrumentation(ElasticApmTracer tracer) {
            super(tracer);
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("registerMessageListener")
                .and(takesArgument(0, named("org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently")));
        }

        @Override
        public Class<?> getAdviceClass() {
            return ListenerConcurrentlyWrappingAdvice.class;
        }

        public static class ListenerConcurrentlyWrappingAdvice {

            @Advice.OnMethodEnter(suppress = Throwable.class)
            private static void onEnter(@Nullable @Advice.Argument(value = 0, readOnly = false) MessageListenerConcurrently messageListener) {
                if (tracer == null || !tracer.isRunning() || helperClassManager == null) {
                    return;
                }

                final RocketMQInstrumentationHelper<Message, MessageQueue, CommunicationMode, SendCallback, MessageListenerConcurrently,
                    MessageListenerOrderly, PullResult, PullCallback, MessageExt> helper = helperClassManager.getForClassLoaderOfClass(MQConsumer.class);
                if (helper == null) {
                    return;
                }

                messageListener = helper.wrapMessageListenerConcurrently(messageListener);
            }
        }
    }

    public static class RegisterMsgListenerOrderlyInstrumentation extends RocketMQLambdaMessageListenerInstrumentation {

        public RegisterMsgListenerOrderlyInstrumentation(ElasticApmTracer tracer) {
            super(tracer);
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("registerMessageListener")
                .and(takesArgument(0, named("org.apache.rocketmq.client.consumer.listener.MessageListenerOrderly")));
        }

        @Override
        public Class<?> getAdviceClass() {
            return ListenerOrderlyWrappingAdvice.class;
        }

        public static class ListenerOrderlyWrappingAdvice {

            @Advice.OnMethodEnter(suppress = Throwable.class)
            private static void onEnter(@Nullable @Advice.Argument(value = 0, readOnly = false) MessageListenerOrderly messageListener) {
                if (tracer == null || !tracer.isRunning() || helperClassManager == null) {
                    return;
                }

                final RocketMQInstrumentationHelper<Message, MessageQueue, CommunicationMode, SendCallback, MessageListenerConcurrently,
                    MessageListenerOrderly, PullResult, PullCallback, MessageExt> helper = helperClassManager.getForClassLoaderOfClass(MQConsumer.class);
                if (helper == null) {
                    return;
                }

                messageListener = helper.wrapMessageListenerOrderly(messageListener);
            }
        }
    }
}
