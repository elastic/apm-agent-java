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
package co.elastic.apm.agent.rabbitmq;

import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.rabbitmq.header.RabbitMQTextHeaderGetter;
import com.rabbitmq.client.AMQP;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;

import static co.elastic.apm.agent.bci.bytebuddy.CustomElementMatchers.classLoaderCanLoadClass;
import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isBootstrapClassLoader;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;

public class RabbitMQConsumerInstrumentation extends RabbitMQBaseInstrumentation {

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return hasSuperType(named("com.rabbitmq.client.Consumer"));
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("handleDelivery");
    }

    @Override
    public ElementMatcher.Junction<ClassLoader> getClassLoaderMatcher() {
        return not(isBootstrapClassLoader()).and(classLoaderCanLoadClass("com.rabbitmq.client.Consumer"));
    }

    @Override
    public Class<?> getAdviceClass() {
        return RabbitConsumerAdvice.class;
    }

    public static class RabbitConsumerAdvice {

        private RabbitConsumerAdvice() {}

        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        @Nullable
        public static Object onHandleDelivery(@Advice.Origin Class<?> originClazz,
                                              @Advice.Argument(value = 2) AMQP.BasicProperties properties) {
            if (!tracer.isRunning() || tracer.currentTransaction() != null) {
                return null;
            }

            Transaction transaction = tracer.startChildTransaction(properties, RabbitMQTextHeaderGetter.INSTANCE, originClazz.getClassLoader());

            if (transaction == null) {
                return null;
            }

            transaction.withType("messaging").withName("RabbitMQ message received");

            return transaction.activate();
        }

        @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
        public static void afterHandleDelivery(@Advice.Enter @Nullable final Object transactionObject,
                                               @Advice.Thrown final Throwable throwable) {
            if (transactionObject instanceof Transaction) {
                Transaction transaction = (Transaction) transactionObject;
                transaction.captureException(throwable)
                    .deactivate()
                    .end();
            }
        }
    }
}
