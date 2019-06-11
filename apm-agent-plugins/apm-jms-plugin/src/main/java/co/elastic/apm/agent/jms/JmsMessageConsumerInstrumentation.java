/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2019 Elastic and contributors
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
package co.elastic.apm.agent.jms;

import co.elastic.apm.agent.bci.VisibleForAdvice;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.TraceContext;
import co.elastic.apm.agent.impl.transaction.TraceContextHolder;
import co.elastic.apm.agent.impl.transaction.Transaction;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.Queue;
import javax.jms.Topic;

import static co.elastic.apm.agent.jms.JmsInstrumentationHelper.JMS_TRACE_PARENT_HEADER;
import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.nameContains;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

public abstract class JmsMessageConsumerInstrumentation extends BaseJmsInstrumentation {

    @VisibleForAdvice
    @SuppressWarnings("WeakerAccess")
    public static final Logger logger = LoggerFactory.getLogger(JmsMessageConsumerInstrumentation.class);

    JmsMessageConsumerInstrumentation(ElasticApmTracer tracer) {
        super(tracer);
    }

    @Override
    public ElementMatcher<? super NamedElement> getTypeMatcherPreFilter() {
        return nameContains("Message").or(nameContains("Consumer"));
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return not(isInterface()).and(hasSuperType(named("javax.jms.MessageConsumer")));
    }

    public static class ReceiveInstrumentation extends JmsMessageConsumerInstrumentation {
        public ReceiveInstrumentation(ElasticApmTracer tracer) {
            super(tracer);
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("receive").and(takesArguments(0).or(takesArguments(1))).and(isPublic())
                .or(named("receiveNoWait").and(takesArguments(0).and(isPublic())));
        }

        @Override
        public Class<?> getAdviceClass() {
            return MessageConsumerAdvice.class;
        }

        public static class MessageConsumerAdvice {

            @Advice.OnMethodEnter(suppress = Throwable.class)
            @Nullable
            public static AbstractSpan beforeReceive(@Advice.Origin Class<?> clazz) {
                AbstractSpan abstractSpan = null;
                if (tracer != null) {
                    final TraceContextHolder<?> parent = tracer.getActive();
                    if (parent == null) {
                        abstractSpan = tracer.startTransaction(TraceContext.asRoot(), null, clazz.getClassLoader())
                            .withType("messaging");
                    } else {
                        String parentType = null;
                        if (parent instanceof Transaction) {
                            parentType = ((Transaction) parent).getType();
                        } else if (parent instanceof Span) {
                            parentType = ((Span) parent).getType();
                        }

                        // Not creating duplicates for nested receive
                        if (parentType != null && parentType.equals("messaging")) {
                            return null;
                        }

                        abstractSpan = parent.createSpan()
                            .withType("messaging")
                            .withSubtype("jms")
                            .withAction("receive");
                    }
                    abstractSpan.setName("JMS RECEIVE");
                    abstractSpan.activate();
                }
                return abstractSpan;
            }

            @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
            public static void afterReceive(@Advice.Enter @Nullable final AbstractSpan abstractSpan,
                                            @Advice.Return @Nullable final Message message,
                                            @Advice.Thrown final Throwable throwable) {

                if (abstractSpan != null) {
                    try {
                        if (message != null) {
                            if (abstractSpan instanceof Transaction) {
                                try {
                                    String messageSenderContext = message.getStringProperty(JMS_TRACE_PARENT_HEADER);
                                    if (messageSenderContext != null) {
                                        abstractSpan.getTraceContext().asChildOf(messageSenderContext);
                                    }
                                } catch (JMSException e) {
                                    logger.error("Failed to retrieve trace context from Message", e);
                                }
                            }

                            try {
                                Destination destination = message.getJMSDestination();
                                if (destination instanceof Queue) {
                                    abstractSpan.appendToName(" from queue ").appendToName(((Queue) destination).getQueueName());
                                } else if (destination instanceof Topic) {
                                    abstractSpan.appendToName(" from topic ").appendToName(((Topic) destination).getTopicName());
                                }
                            } catch (JMSException e) {
                                logger.warn("Failed to retrieve message's destination", e);
                            }
                        }
                    } finally {
                        abstractSpan.captureException(throwable);
                        abstractSpan.deactivate().end();
                    }
                }
            }
        }
    }

    public static class SetMessageListenerInstrumentation extends JmsMessageConsumerInstrumentation {
        public SetMessageListenerInstrumentation(ElasticApmTracer tracer) {
            super(tracer);
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("setMessageListener").and(takesArgument(0, named("javax.jms.MessageListener")));
        }

        @Override
        public Class<?> getAdviceClass() {
            return ListenerWrappingAdvice.class;
        }

        public static class ListenerWrappingAdvice {

            @Advice.OnMethodEnter(suppress = Throwable.class)
            public static void beforeSetListener(@Advice.Argument(value = 0, readOnly = false) @Nullable MessageListener original) {
                //noinspection ConstantConditions - the Advice must be invoked only if the BaseJmsInstrumentation constructor was invoked
                JmsInstrumentationHelper<Destination, Message, MessageListener> helper =
                    jmsInstrHelperManager.getForClassLoaderOfClass(MessageListener.class);
                if (helper != null) {
                    original = helper.wrapLambda(original);
                }
            }
        }
    }
}
