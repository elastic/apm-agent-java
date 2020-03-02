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
package co.elastic.apm.agent.rocketmq.instrumentation.consumer.pull;

import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.rocketmq.helper.RocketMQInstrumentationHelper;
import co.elastic.apm.agent.rocketmq.instrumentation.BaseRocketMQInstrumentation;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.rocketmq.client.consumer.MQConsumer;
import org.apache.rocketmq.client.consumer.PullCallback;

import static net.bytebuddy.matcher.ElementMatchers.named;

/**
 * Instruments the {@link org.apache.rocketmq.client.impl.consumer.DefaultMQPullConsumerImpl#pullAsyncImpl} method.
 * By wrapping the {@link org.apache.rocketmq.client.consumer.PullCallback}, the 'msgFoundList' in
 * {@link org.apache.rocketmq.client.consumer.PullResult} is wrapped with
 * {@link co.elastic.apm.agent.rocketmq.helper.ConsumeMessageListWrapper},
 * so that a transaction will be started when the message is polled by the iterator.
 * Note: it is not an interface method, and there is a risk that this instrumentation will be invalid due to later changes.
 */
public class RocketMQPullCallbackInstrumentation extends BaseRocketMQInstrumentation {

    public RocketMQPullCallbackInstrumentation(ElasticApmTracer tracer) {
        super(tracer);
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("org.apache.rocketmq.client.impl.consumer.DefaultMQPullConsumerImpl");
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("pullAsyncImpl");
    }

    @Override
    public Class<?> getAdviceClass() {
        return PullCallbackAdvice.class;
    }

    public static class PullCallbackAdvice {

        @Advice.OnMethodEnter(suppress = Throwable.class)
        private static void onEnter( @Advice.Argument(value = 4, readOnly = false) PullCallback pullCallback) {
            if (tracer == null || helperClassManager == null) {
                return;
            }

            final RocketMQInstrumentationHelper helper = helperClassManager.getForClassLoaderOfClass(MQConsumer.class);
            if (helper == null) {
                return;
            }

            pullCallback = helper.wrapPullCallback(pullCallback);
        }

    }

}
