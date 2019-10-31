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
import co.elastic.apm.agent.impl.transaction.TraceContext;
import co.elastic.apm.agent.impl.transaction.Transaction;
import net.bytebuddy.asm.Advice;
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

import static co.elastic.apm.agent.jms.JmsInstrumentationHelper.JMS_TRACE_PARENT_PROPERTY;
import static co.elastic.apm.agent.jms.JmsInstrumentationHelper.MESSAGING_TYPE;
import static co.elastic.apm.agent.jms.JmsInstrumentationHelper.RECEIVE_NAME_PREFIX;
import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

public class JmsMessageListenerInstrumentation extends BaseJmsInstrumentation {

    @VisibleForAdvice
    @SuppressWarnings("WeakerAccess")
    public static final Logger logger = LoggerFactory.getLogger(JmsMessageListenerInstrumentation.class);

    public JmsMessageListenerInstrumentation(ElasticApmTracer tracer) {
        super(tracer);
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return not(isInterface())
            .and(
                hasSuperType(named("javax.jms.MessageListener"))
                    .or(hasSuperType(named("org.springframework.jms.listener.SessionAwareMessageListener")))
            );
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("onMessage")
            .and(takesArgument(0, hasSuperType(named("javax.jms.Message")))).and(isPublic());
    }

    @Override
    public Class<?> getAdviceClass() {
        return MessageListenerAdvice.class;
    }

    public static class MessageListenerAdvice {

        @SuppressWarnings("unused")
        @Advice.OnMethodEnter(suppress = Throwable.class)
        @Nullable
        public static Transaction beforeOnMessage(@Advice.Argument(0) @Nullable final Message message,
                                                  @Advice.Origin Class<?> clazz) {

            if (message == null || tracer == null || tracer.currentTransaction() != null) {
                return null;
            }

            Destination destination = null;
            String destinationName = null;
            try {
                destination = message.getJMSDestination();
            } catch (JMSException e) {
                logger.warn("Failed to retrieve message's destination", e);
            }

            //noinspection ConstantConditions
            JmsInstrumentationHelper<Destination, Message, MessageListener> helper =
                jmsInstrHelperManager.getForClassLoaderOfClass(MessageListener.class);
            if (helper != null && destination != null) {
                destinationName = helper.extractDestinationName(message, destination);
                if (helper.ignoreDestination(destinationName)) {
                    return null;
                }
            }

            // Create a transaction - even if running on same JVM as the sender
            Transaction transaction = null;

            try {
                String traceParentProperty = message.getStringProperty(JMS_TRACE_PARENT_PROPERTY);
                if (traceParentProperty != null) {
                    transaction = tracer.startTransaction(TraceContext.fromTraceparentHeader(),
                        traceParentProperty, clazz.getClassLoader());
                }
            } catch (JMSException e) {
                logger.warn("Failed to retrieve trace context property from JMS message", e);
            }

            if (transaction == null) {
                transaction = tracer.startTransaction(TraceContext.asRoot(), null, clazz.getClassLoader());
            }

            transaction.withType(MESSAGING_TYPE).withName(RECEIVE_NAME_PREFIX);
            if (helper != null) {
                if (destinationName != null) {
                    helper.addDestinationDetails(message, destination, destinationName, transaction.appendToName(" from "));
                }
                helper.addMessageDetails(message, transaction);
            }
            transaction.activate();

            return transaction;
        }

        @SuppressWarnings("unused")
        @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
        public static void afterOnMessage(@Advice.Enter @Nullable final Transaction transaction,
                                          @Advice.Thrown final Throwable throwable) {

            if (transaction != null) {
                transaction.captureException(throwable);
                transaction.deactivate().end();
            }
        }
    }
}
