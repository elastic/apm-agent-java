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

import static co.elastic.apm.agent.jms.JmsInstrumentationHelper.JMS_TRACE_PARENT_HEADER;
import static co.elastic.apm.agent.jms.JmsInstrumentationHelper.MESSAGE_HANDLING;
import static co.elastic.apm.agent.jms.JmsInstrumentationHelper.MESSAGE_POLLING;
import static co.elastic.apm.agent.jms.JmsInstrumentationHelper.MESSAGING_TYPE;
import static co.elastic.apm.agent.jms.JmsInstrumentationHelper.RECEIVE_NAME_PREFIX;
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
        return nameContains("Message")
            .or(nameContains("Consumer"))
            .or(nameContains("Receiver"))
            .or(nameContains("Subscriber"));
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
            public static AbstractSpan beforeReceive(@Advice.Origin Class<?> clazz,
                                                     @Advice.Origin("#m") String methodName) {

                AbstractSpan createdSpan = null;
                boolean createPollingTransaction = false;
                boolean createPollingSpan = false;
                if (tracer != null) {
                    final TraceContextHolder<?> parent = tracer.getActive();
                    if (parent == null) {
                        createPollingTransaction = true;
                    } else {
                        if (parent instanceof Transaction) {
                            Transaction transaction = (Transaction) parent;
                            if (MESSAGE_POLLING.equals(transaction.getType())) {
                                // Avoid duplications for nested calls
                                return null;
                            } else if (MESSAGE_HANDLING.equals(transaction.getType())) {
                                // A transaction created in the OnMethodExit of the poll- end it here
                                // Type must be changed to "messaging"
                                transaction.withType(MESSAGING_TYPE);
                                transaction.deactivate().end();
                                createPollingTransaction = true;
                            } else {
                                createPollingSpan = true;
                            }
                        } else if (parent instanceof Span) {
                            Span parentSpan = (Span) parent;
                            if (MESSAGING_TYPE.equals(parentSpan.getType()) && "receive".equals(parentSpan.getAction())) {
                                // Avoid duplication for nested calls
                                return null;
                            }
                            createPollingSpan = true;
                        }
                    }

                    if (messagingConfiguration != null) {
                        createPollingTransaction &= messagingConfiguration.getMessagePollingTransactionStrategy() != MessagingConfiguration.Strategy.HANDLING;
                        createPollingTransaction |= "receiveNoWait".equals(methodName);
                    }

                    if (createPollingSpan) {
                        createdSpan = parent.createSpan()
                            .withType(MESSAGING_TYPE)
                            .withSubtype("jms")
                            .withAction("receive");
                    } else if (createPollingTransaction) {
                        createdSpan = tracer.startTransaction(TraceContext.asRoot(), null, clazz.getClassLoader())
                            .withType(MESSAGE_POLLING);
                    }

                    if (createdSpan != null) {
                        createdSpan.withName(RECEIVE_NAME_PREFIX);
                        createdSpan.activate();
                    }
                }
                return createdSpan;
            }

            @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
            public static void afterReceive(@Advice.Origin Class<?> clazz,
                                            @Advice.Origin("#m") String methodName,
                                            @Advice.Enter @Nullable final AbstractSpan abstractSpan,
                                            @Advice.Return @Nullable final Message message,
                                            @Advice.Thrown final Throwable throwable) {

                String messageSenderContext = null;
                Destination destination = null;
                boolean discard = false;
                if (message != null) {
                    try {
                        messageSenderContext = message.getStringProperty(JMS_TRACE_PARENT_HEADER);
                        destination = message.getJMSDestination();
                    } catch (JMSException e) {
                        logger.error("Failed to retrieve meta info from Message", e);
                    }

                    if (abstractSpan instanceof Transaction) {
                        if (messageSenderContext != null) {
                            abstractSpan.getTraceContext().asChildOf(messageSenderContext);
                        }
                        ((Transaction) abstractSpan).withType(MESSAGING_TYPE);
                    }
                } else if (abstractSpan instanceof Transaction) {
                    // Do not report polling transactions if not yielding messages
                    ((Transaction) abstractSpan).ignoreTransaction();
                    discard = true;
                }

                //noinspection ConstantConditions
                JmsInstrumentationHelper<Destination, Message, MessageListener> helper =
                    jmsInstrHelperManager.getForClassLoaderOfClass(MessageListener.class);

                if (abstractSpan != null) {
                    try {
                        if (!discard) {
                            abstractSpan.captureException(throwable);
                            if (message != null && helper != null && destination != null) {
                                abstractSpan.appendToName(" from ");
                                helper.appendDestinationToName(destination, abstractSpan);

                            }
                        }
                    } finally {
                        abstractSpan.deactivate().end();
                    }
                }

                if (messageSenderContext != null && tracer != null && tracer.currentTransaction() == null
                    && messagingConfiguration != null
                    && messagingConfiguration.getMessagePollingTransactionStrategy() != MessagingConfiguration.Strategy.POLLING
                    && !"receiveNoWait".equals(methodName)) {

                    Transaction messageHandlingTransaction = tracer.startTransaction(TraceContext.fromTraceparentHeader(), messageSenderContext, clazz.getClassLoader())
                        .withType(MESSAGE_HANDLING)
                        .withName(RECEIVE_NAME_PREFIX);

                    if (helper != null && destination != null) {
                        messageHandlingTransaction.appendToName(" from ");
                        helper.appendDestinationToName(destination, messageHandlingTransaction);

                    }

                    messageHandlingTransaction.activate();
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
