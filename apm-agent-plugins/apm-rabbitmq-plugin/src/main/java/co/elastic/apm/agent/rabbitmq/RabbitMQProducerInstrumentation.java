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

import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.TextHeaderSetter;
import co.elastic.apm.agent.rabbitmq.header.RabbitMQTextHeaderSetter;
import co.elastic.apm.agent.sdk.advice.AssignTo;
import com.rabbitmq.client.AMQP;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

import static co.elastic.apm.agent.bci.bytebuddy.CustomElementMatchers.classLoaderCanLoadClass;
import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isBootstrapClassLoader;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

public class RabbitMQProducerInstrumentation extends RabbitMQBaseInstrumentation {

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return hasSuperType(named("com.rabbitmq.client.Channel"));
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("basicPublish")
            .and(takesArguments(6));
    }
    @Override
    public ElementMatcher.Junction<ClassLoader> getClassLoaderMatcher() {
        return not(isBootstrapClassLoader()).and(classLoaderCanLoadClass("com.rabbitmq.client.Channel"));
    }

    @Override
    public Class<?> getAdviceClass() {
        return RabbitProducerAdvice.class;
    }

    public static class RabbitProducerAdvice {

        private RabbitProducerAdvice() {}

        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        @AssignTo(
            arguments = @AssignTo.Argument(index = 0, value = 4)
        )
        @Nullable
        public static Object[] onBasicPublish(@Advice.Argument(0) final String exchange,
                                              @Advice.Argument(value = 4) @Nullable AMQP.BasicProperties originalBasicProperties) {
            if (!tracer.isRunning() || tracer.getActive() == null) {
                return null;
            }

            final AbstractSpan<?> activeSpan = tracer.getActive();
            if (activeSpan == null) {
                return null;
            }

            Span exitSpan = activeSpan.createExitSpan();
            if (null == exitSpan) {
                return null;
            }

            exitSpan.withType("messaging")
                .withSubtype("rabbitmq")
                .withAction("send")
                .withName("RabbitMQ message sent to ")
                .appendToName(exchange);

            final TextHeaderSetter<HashMap<String, Object>> textHeaderSetter = new RabbitMQTextHeaderSetter(); // TODO: Singleton?
            AMQP.BasicProperties basicProperties = propagateTraceContext(exitSpan, originalBasicProperties, textHeaderSetter);

            /*
            TODO: Transaction context
            exitSpan.getContext().getMessage().withQueue(exchange);

            exitSpan.getContext().getDestination().getService().withType("messaging").withName("rabbitmq")
                .getResource().append("rabbitmq/").append(exchange);*/

            exitSpan.activate();

            return new Object[]{basicProperties, exitSpan};
        }

        private static AMQP.BasicProperties propagateTraceContext(Span exitSpan,
                                                                  @Nullable AMQP.BasicProperties originalBasicProperties,
                                                                  TextHeaderSetter<HashMap<String, Object>> textHeaderSetter) {
            AMQP.BasicProperties properties = originalBasicProperties;
            if (properties == null) {
                properties = new AMQP.BasicProperties();
            }

            Map<String, Object> currentHeaders = properties.getHeaders();
            if (currentHeaders == null) {
                currentHeaders = new HashMap<>();
            }

            HashMap<String, Object> headersWithContext = new HashMap<>(currentHeaders);

            exitSpan.propagateTraceContext(headersWithContext, textHeaderSetter);

            return properties.builder().headers(headersWithContext).build();
        }

        @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
        public static void afterBasicPublish(@Advice.Enter @Nullable Object[] enterArray,
                                             @Advice.Thrown @Nullable Throwable throwable) {
            if (enterArray != null && enterArray.length >= 2 && enterArray[1] != null) {
                Span span = (Span) enterArray[1];
                span.captureException(throwable).deactivate().end();
            }
        }
    }
}
