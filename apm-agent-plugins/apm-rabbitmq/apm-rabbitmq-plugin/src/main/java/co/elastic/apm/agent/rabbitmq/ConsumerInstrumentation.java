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

import co.elastic.apm.agent.tracer.Transaction;
import co.elastic.apm.agent.rabbitmq.header.RabbitMQTextHeaderGetter;
import co.elastic.apm.agent.tracer.metadata.Message;
import co.elastic.apm.agent.util.PrivilegedActionUtils;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.Envelope;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;

import static co.elastic.apm.agent.bci.bytebuddy.CustomElementMatchers.classLoaderCanLoadClass;
import static net.bytebuddy.matcher.ElementMatchers.any;
import static net.bytebuddy.matcher.ElementMatchers.isBootstrapClassLoader;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;

/**
 * Instruments
 * <ul>
 *     <li>{@link com.rabbitmq.client.Consumer#handleDelivery}</li>
 * </ul>
 */
public class ConsumerInstrumentation extends RabbitmqBaseInstrumentation {

    @Override
    public ElementMatcher<? super NamedElement> getTypeMatcherPreFilter() {
        // Spring RabbitMQ is supported through Spring interfaces, rather than the RabbitMQ API (apm-rabbitmq-spring module)
        return not(nameStartsWith("org.springframework."));
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        // Instrumentation applied at runtime, thus no need to check type
        return any();
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("handleDelivery");
    }

    @Override
    public ElementMatcher.Junction<ClassLoader> getClassLoaderMatcher() {
        return not(isBootstrapClassLoader())
            .and(classLoaderCanLoadClass("com.rabbitmq.client.Consumer"));
    }

    @Override
    public String getAdviceClassName() {
        return "co.elastic.apm.agent.rabbitmq.ConsumerInstrumentation$RabbitConsumerAdvice";
    }

    public static class RabbitConsumerAdvice {

        private RabbitConsumerAdvice() {
        }

        @Nullable
        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static Object onHandleDelivery(@Advice.Origin Class<?> originClazz,
                                              @Advice.This Consumer consumer,
                                              @Advice.Argument(value = 1) @Nullable Envelope envelope,
                                              @Advice.Argument(value = 2) @Nullable AMQP.BasicProperties properties) {
            if (!tracer.isRunning()) {
                return null;
            }
            String exchange = envelope != null ? envelope.getExchange() : null;

            if (null == exchange || isIgnored(exchange)) {
                return null;
            }

            Transaction<?> transaction = tracer.currentTransaction();
            if (transaction != null) {
                return null;
            }

            transaction = tracer.startChildTransaction(properties, RabbitMQTextHeaderGetter.INSTANCE, PrivilegedActionUtils.getClassLoader(originClazz));
            if (transaction == null) {
                return null;
            }

            transaction.withType("messaging")
                .withName("RabbitMQ RECEIVE from ").appendToName(normalizeExchangeName(exchange));

            transaction.setFrameworkName("RabbitMQ");

            Message message = captureMessage(exchange, envelope.getRoutingKey(), getTimestamp(properties != null ? properties.getTimestamp() : null), transaction);
            // only capture incoming messages headers for now (consistent with other messaging plugins)
            if (properties != null) {
                captureHeaders(properties.getHeaders(), message);
            }
            return transaction.activate();
        }

        @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
        public static void afterHandleDelivery(@Advice.Enter @Nullable final Object transactionObject,
                                               @Advice.Thrown @Nullable final Throwable throwable) {
            if (transactionObject instanceof Transaction<?>) {
                Transaction<?> transaction = (Transaction<?>) transactionObject;
                transaction.captureException(throwable)
                    .deactivate()
                    .end();
            }
        }
    }
}
