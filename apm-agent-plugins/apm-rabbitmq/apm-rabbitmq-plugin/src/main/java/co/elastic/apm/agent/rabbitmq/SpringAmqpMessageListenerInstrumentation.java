/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2021 Elastic and contributors
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
package co.elastic.apm.agent.rabbitmq;

import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.rabbitmq.header.SpringRabbitMQTextHeaderGetter;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.springframework.amqp.core.Message;

import javax.annotation.Nullable;

import static co.elastic.apm.agent.bci.bytebuddy.CustomElementMatchers.classLoaderCanLoadClass;
import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isBootstrapClassLoader;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

public class SpringAmqpMessageListenerInstrumentation extends SpringBaseInstrumentation {
    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return not(isInterface()).and(hasSuperType(named("org.springframework.amqp.core.MessageListener")));
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("onMessage")
            .and(takesArgument(0, hasSuperType(named("org.springframework.amqp.core.Message")))).and(isPublic());
    }

    @Override
    public Class<?> getAdviceClass() {
        return SpringAmqpMessageListenerAdvice.class;
    }

    public static class SpringAmqpMessageListenerAdvice extends SpringBaseAdvice {

        @Nullable
        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static Object beforeMessageHandle(@Advice.Origin Class<?> originClazz,
                                                 @Advice.Argument(value = 0) @Nullable final Message message) {
            System.out.println("### BEFORE");
            if (message == null) {
                return null;
            }
            String exchangeOrQueue = message.getMessageProperties().getReceivedExchange();
            if (null == exchangeOrQueue || isIgnored(exchangeOrQueue)) {
                return null;
            }

            Transaction transaction = tracer.currentTransaction();
            if (transaction != null) {
                return null;
            }
            transaction = tracer.startChildTransaction(message.getMessageProperties(), SpringRabbitMQTextHeaderGetter.INSTANCE, originClazz.getClassLoader());
            if (transaction == null) {
                return null;
            }

            transaction.withType("messaging")
                .withName("RabbitMQ RECEIVE from ").appendToName(normalizeExchangeName(exchangeOrQueue));

            transaction.setFrameworkName("Spring AMQP");

            co.elastic.apm.agent.impl.context.Message internalMessage = captureMessage(exchangeOrQueue, getTimestamp(message.getMessageProperties()), transaction);
            // only capture incoming messages headers for now (consistent with other messaging plugins)
            captureHeaders(message.getMessageProperties(), internalMessage);
            return transaction.activate();
        }

        @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
        public static void afterMessageHandle(@Advice.Enter @Nullable final Object transactionObject,
                                              @Advice.Thrown @Nullable final Throwable throwable) {
            System.out.println("### AFTER");
            if (transactionObject instanceof Transaction) {
                Transaction transaction = (Transaction) transactionObject;
                transaction.captureException(throwable)
                    .deactivate()
                    .end();
            }
        }
    }

}
