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
package co.elastic.apm.agent.rocketmq.instrumentation.producer;

import co.elastic.apm.agent.bci.VisibleForAdvice;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.rocketmq.helper.RocketMQInstrumentationHelper;
import co.elastic.apm.agent.rocketmq.instrumentation.BaseRocketMQInstrumentation;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.rocketmq.client.impl.CommunicationMode;
import org.apache.rocketmq.client.producer.MQProducer;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageQueue;

import static net.bytebuddy.matcher.ElementMatchers.named;

/**
 * Instruments the {@link org.apache.rocketmq.client.impl.producer.DefaultMQProducerImpl#sendKernelImpl} method.
 * It is the core internal method in the {@link org.apache.rocketmq.client.producer.DefaultMQProducer}
 * to send messages through various {@link CommunicationMode}
 * Note: it is not an interface method, and there is a risk that this instrumentation will be invalid due to later changes.
 */
public class RocketMQProducerInstrumentation extends BaseRocketMQInstrumentation {

    public RocketMQProducerInstrumentation(ElasticApmTracer tracer) {
        super(tracer);
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("org.apache.rocketmq.client.impl.producer.DefaultMQProducerImpl");
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("sendKernelImpl");
    }

    @Override
    public Class<?> getAdviceClass() {
        return RocketMQProducerAdvice.class;
    }

    @SuppressWarnings("rawtypes")
    @VisibleForAdvice
    public static class RocketMQProducerAdvice {

        @Advice.OnMethodEnter(suppress = Throwable.class)
        public static void onBeforeSendDefaultImpl(@Advice.Local("span") Span span,
                                                   @Advice.Argument(value = 0, readOnly = false) Message msg,
                                                   @Advice.Argument(1) MessageQueue mq,
                                                   @Advice.Argument(2) CommunicationMode communicationMode,
                                                   @Advice.Argument(value = 3, readOnly = false) SendCallback sendCallback) {
            if (tracer == null || !tracer.isRunning() || tracer.getActive() == null) {
                return;
            }

            if (helperClassManager == null) {
                return;
            }
            final RocketMQInstrumentationHelper helper = helperClassManager.getForClassLoaderOfClass(MQProducer.class);
            if (helper == null) {
                return;
            }

            span = helper.onSendStart(msg, mq, communicationMode);

            if (span == null) {
                return;
            }

            sendCallback = helper.wrapSendCallback(sendCallback, span);
        }

        @Advice.OnMethodExit(suppress = Throwable.class)
        public static void onAfterSendDefaultImpl(@Advice.Local("span") Span span,
                                                  @Advice.Argument(2) CommunicationMode communicationMode) {
            if (span != null) {
                span.deactivate();
                if (communicationMode == CommunicationMode.SYNC || communicationMode == CommunicationMode.ONEWAY) {
                    span.end();
                }
            }
        }

    }

}
