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

import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.context.Destination;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.rabbitmq.header.RabbitMQTextHeaderSetter;
import co.elastic.apm.agent.sdk.DynamicTransformer;
import co.elastic.apm.agent.sdk.ElasticApmInstrumentation;
import co.elastic.apm.agent.sdk.advice.AssignTo;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.Consumer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static co.elastic.apm.agent.bci.bytebuddy.CustomElementMatchers.classLoaderCanLoadClass;
import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isBootstrapClassLoader;
import static net.bytebuddy.matcher.ElementMatchers.nameContains;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

/**
 * Instruments implementations of {@link com.rabbitmq.client.Channel}
 */
public abstract class ChannelInstrumentation extends BaseInstrumentation {

    public ChannelInstrumentation(ElasticApmTracer tracer) {
        super(tracer);
    }

    @Override
    public ElementMatcher<? super NamedElement> getTypeMatcherPreFilter() {
        return nameStartsWith("com.rabbitmq.client")
            .and(nameContains("Channel"));
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return hasSuperType(named("com.rabbitmq.client.Channel"));
    }


    @Override
    public ElementMatcher.Junction<ClassLoader> getClassLoaderMatcher() {
        return not(isBootstrapClassLoader()).and(classLoaderCanLoadClass("com.rabbitmq.client.Channel"));
    }

    /**
     * Instruments
     * <ul>
     *     <li>{@link com.rabbitmq.client.Channel#basicConsume} to ensure instrumentation of {@link com.rabbitmq.client.Consumer} implementation</li>
     * </ul>
     */
    public static class BasicConsume extends ChannelInstrumentation {

        public static final Collection<Class<? extends ElasticApmInstrumentation>> CONSUMER_INSTRUMENTATION =
            Collections.<Class<? extends ElasticApmInstrumentation>>singleton(ConsumerInstrumentation.class);

        public BasicConsume(ElasticApmTracer tracer) {
            super(tracer);
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("basicConsume")
                .and(takesArguments(7))
                .and(takesArgument(6, named("com.rabbitmq.client.Consumer")));
        }

        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static void onEnter(@Advice.This Channel channel,
                                   @Advice.Argument(6) @Nullable Consumer consumer) {
            if (consumer == null) {
                return;
            }

            DynamicTransformer.Accessor.get().ensureInstrumented(consumer.getClass(), CONSUMER_INSTRUMENTATION);
        }
    }

    /**
     * Instruments
     * <ul>
     *     <li>{@link com.rabbitmq.client.Channel#basicPublish}</li>
     * </ul>
     */
    public static class BasicPublish extends ChannelInstrumentation {

        public BasicPublish(ElasticApmTracer tracer) {
            super(tracer);
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("basicPublish")
                .and(takesArguments(6));
        }

        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        @AssignTo(arguments = @AssignTo.Argument(index = 0, value = 4))
        @Nullable
        public static Object[] onBasicPublish(@Advice.This Channel channel,
                                              @Advice.Argument(0) String exchange,
                                              @Advice.Argument(1) String routingKey,
                                              @Advice.Argument(4) @Nullable AMQP.BasicProperties properties) {
            if (!tracer.isRunning()) {
                return null;
            }

            exchange = normalizeExchangeName(exchange);
            routingKey = normalizeRoutingKey(routingKey);

            Span exitSpan = createExitSpan(tracer.getActive(), properties, exchange, routingKey);
            if (null == exitSpan) {
                return null;
            }

            properties = propagateTraceContext(exitSpan, properties);

            captureMessage(exchange, properties, exitSpan);
            captureDestination(exchange, channel, exitSpan);

            return new Object[]{properties, exitSpan.activate()};
        }

        @Nullable
        private static Span createExitSpan(@Nullable AbstractSpan<?> context, @Nullable AMQP.BasicProperties properties, String exchange, String routingKey){
            if (context == null || isExchangeIgnored(exchange)) {
                return null;
            }
            Span exitSpan = context.createExitSpan();
            if (null == exitSpan) {
                return null;
            }

            exitSpan.withType("messaging")
                .withSubtype("rabbitmq")
                .withAction("send")
                .withName("RabbitMQ SEND to ").appendToName(exchange).appendToName("/").appendToName(routingKey);

            return exitSpan;
        }

        private static void captureDestination(String exchange, Channel channel, Span span){
            Destination destination = span.getContext().getDestination();

            destination.getService()
                .withType("messaging")
                .withName("rabbitmq")
                .getResource().append("rabbitmq/").append(exchange);

            Connection connection = channel.getConnection();
            destination.withAddress(connection.getAddress().getHostName());
            destination.withPort(connection.getPort());
        }

        private static AMQP.BasicProperties propagateTraceContext(Span exitSpan,
                                                                  @Nullable AMQP.BasicProperties originalBasicProperties) {
            AMQP.BasicProperties properties = originalBasicProperties;
            if (properties == null) {
                properties = new AMQP.BasicProperties();
            }

            Map<String, Object> currentHeaders = properties.getHeaders();
            if (currentHeaders == null) {
                currentHeaders = new HashMap<>();
            }

            HashMap<String, Object> headersWithContext = new HashMap<>(currentHeaders);

            exitSpan.propagateTraceContext(headersWithContext, RabbitMQTextHeaderSetter.INSTANCE);

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
