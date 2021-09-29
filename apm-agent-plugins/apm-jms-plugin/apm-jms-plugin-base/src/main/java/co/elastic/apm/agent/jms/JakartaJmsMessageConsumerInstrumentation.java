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
package co.elastic.apm.agent.jms;

import co.elastic.apm.agent.configuration.MessagingConfiguration;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.sdk.advice.AssignTo;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import jakarta.jms.Destination;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.MessageListener;

import static co.elastic.apm.agent.jms.JakartaJmsInstrumentationHelper.*;
import static net.bytebuddy.matcher.ElementMatchers.*;

public abstract class JakartaJmsMessageConsumerInstrumentation extends JakartaBaseJmsInstrumentation {

    @SuppressWarnings("WeakerAccess")
    public static final Logger logger = LoggerFactory.getLogger(JakartaJmsMessageConsumerInstrumentation.class);

    @Override
    public ElementMatcher<? super NamedElement> getTypeMatcherPreFilter() {
        return nameContains("Message")
            .or(nameContains("Consumer"))
            .or(nameContains("Receiver"))
            .or(nameContains("Subscriber"));
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return not(isInterface()).and(hasSuperType(named("jakarta.jms.MessageConsumer")));
    }

    public static class ReceiveInstrumentation extends JakartaJmsMessageConsumerInstrumentation {

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("receive").and(takesArguments(0).or(takesArguments(1))).and(isPublic())
                .or(named("receiveNoWait").and(takesArguments(0).and(isPublic())));
        }

        @Override
        public String getAdviceClassName() {
            return "co.elastic.apm.agent.jms.JakartaJmsMessageConsumerInstrumentation$ReceiveInstrumentation$MessageConsumerAdvice";
        }

        public static class MessageConsumerAdvice extends JakartaBaseAdvice {

            @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
            @Nullable
            public static Object beforeReceive(@Advice.Origin Class<?> clazz,
                                               @Advice.Origin("#m") String methodName) {

                AbstractSpan<?> createdSpan = null;
                boolean createPollingTransaction = false;
                boolean createPollingSpan = false;
                final AbstractSpan<?> parent = tracer.getActive();
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

                createPollingTransaction &= messagingConfiguration.getMessagePollingTransactionStrategy() != MessagingConfiguration.Strategy.HANDLING;
                createPollingTransaction |= "receiveNoWait".equals(methodName);

                if (createPollingSpan) {
                    createdSpan = parent.createSpan()
                        .withType(MESSAGING_TYPE)
                        .withSubtype("jms")
                        .withAction("receive");
                } else if (createPollingTransaction) {
                    createdSpan = tracer.startRootTransaction(clazz.getClassLoader());
                    if (createdSpan != null) {
                        ((Transaction) createdSpan).withType(MESSAGE_POLLING);
                    }
                }

                if (createdSpan != null) {
                    createdSpan.withName(RECEIVE_NAME_PREFIX);
                    createdSpan.activate();
                }
                return createdSpan;
            }

            @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
            public static void afterReceive(@Advice.Origin Class<?> clazz,
                                            @Advice.Origin("#m") String methodName,
                                            @Advice.Enter @Nullable final Object abstractSpanObj,
                                            @Advice.Return @Nullable final Message message,
                                            @Advice.Thrown @Nullable final Throwable throwable) {
                AbstractSpan<?> abstractSpan = null;
                if (abstractSpanObj instanceof AbstractSpan<?>) {
                    abstractSpan = (AbstractSpan<?>) abstractSpanObj;
                }
                Destination destination = null;
                String destinationName = null;
                boolean discard = false;
                boolean addDetails = true;
                if (message != null) {
                    try {
                        destination = message.getJMSDestination();
                        destinationName = helper.extractDestinationName(message, destination);
                        discard = helper.ignoreDestination(destinationName);
                    } catch (JMSException e) {
                        logger.error("Failed to retrieve meta info from Message", e);
                    }

                    if (abstractSpan instanceof Transaction) {
                        Transaction transaction = (Transaction) abstractSpan;
                        if (discard) {
                            transaction.ignoreTransaction();
                        } else {
                            helper.makeChildOf(transaction, message);
                            transaction.withType(MESSAGING_TYPE);
                            helper.addMessageDetails(message, abstractSpan);
                        }
                    }
                } else if (abstractSpan instanceof Transaction) {
                    // Do not report polling transactions if not yielding messages
                    ((Transaction) abstractSpan).ignoreTransaction();
                    addDetails = false;
                }

                if (abstractSpan != null) {
                    try {
                        if (discard) {
                            abstractSpan.requestDiscarding();
                        } else if (addDetails) {
                            if (message != null && destinationName != null) {
                                abstractSpan.appendToName(" from ");
                                helper.addDestinationDetails(destination, destinationName, abstractSpan);
                                helper.setMessageAge(message, abstractSpan);
                            }
                            abstractSpan.captureException(throwable);
                        }
                    } finally {
                        abstractSpan.deactivate().end();
                    }
                }

                if (!discard && tracer.currentTransaction() == null
                    && message != null
                    && messagingConfiguration.getMessagePollingTransactionStrategy() != MessagingConfiguration.Strategy.POLLING
                    && !"receiveNoWait".equals(methodName)) {

                    Transaction messageHandlingTransaction = helper.startJmsTransaction(message, clazz);
                    if (messageHandlingTransaction != null) {
                        messageHandlingTransaction.withType(MESSAGE_HANDLING)
                            .withName(RECEIVE_NAME_PREFIX);

                        if (destinationName != null) {
                            messageHandlingTransaction.appendToName(" from ");
                            helper.addDestinationDetails(destination, destinationName, messageHandlingTransaction);
                            helper.addMessageDetails(message, messageHandlingTransaction);
                            helper.setMessageAge(message, messageHandlingTransaction);
                        }

                        messageHandlingTransaction.activate();
                    }
                }
            }
        }
    }

    public static class SetMessageListenerInstrumentation extends JakartaJmsMessageConsumerInstrumentation {

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("setMessageListener").and(takesArgument(0, named("jakarta.jms.MessageListener")));
        }

        @Override
        public String getAdviceClassName() {
            return "co.elastic.apm.agent.jms.JakartaJmsMessageConsumerInstrumentation$SetMessageListenerInstrumentation$ListenerWrappingAdvice";
        }

        public static class ListenerWrappingAdvice extends JakartaBaseAdvice {

            @Nullable
            @AssignTo.Argument(0)
            @Advice.OnMethodEnter(inline = false)
            public static MessageListener beforeSetListener(@Advice.Argument(0) @Nullable MessageListener original) {
                return helper.wrapLambda(original);
            }
        }
    }
}
