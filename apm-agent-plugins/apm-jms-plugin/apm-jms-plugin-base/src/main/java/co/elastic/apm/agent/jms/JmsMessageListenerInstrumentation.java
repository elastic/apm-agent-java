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

import co.elastic.apm.agent.impl.transaction.Transaction;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;

import javax.annotation.Nullable;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;

import static co.elastic.apm.agent.jms.JmsInstrumentationHelper.MESSAGING_TYPE;
import static co.elastic.apm.agent.jms.JmsInstrumentationHelper.RECEIVE_NAME_PREFIX;
import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

public class JmsMessageListenerInstrumentation extends BaseJmsInstrumentation {

    @SuppressWarnings("WeakerAccess")
    public static final Logger logger = LoggerFactory.getLogger(JmsMessageListenerInstrumentation.class);

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return not(isInterface()).and(hasSuperType(named("javax.jms.MessageListener")));
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("onMessage")
            .and(takesArgument(0, hasSuperType(named("javax.jms.Message")))).and(isPublic());
    }

    @Override
    public String getAdviceClassName() {
        return "co.elastic.apm.agent.jms.JmsMessageListenerInstrumentation$MessageListenerAdvice";
    }

    public static class MessageListenerAdvice extends BaseAdvice {

        @SuppressWarnings("unused")
        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        @Nullable
        public static Object beforeOnMessage(@Advice.Argument(0) @Nullable final Message message,
                                             @Advice.Origin Class<?> clazz) {

            if (message == null || tracer.currentTransaction() != null) {
                return null;
            }

            Destination destination = null;
            String destinationName = null;
            long timestamp = 0;
            try {
                destination = message.getJMSDestination();
                timestamp = message.getJMSTimestamp();
            } catch (JMSException e) {
                logger.warn("Failed to retrieve message's destination", e);
            }

            if (destination != null) {
                destinationName = helper.extractDestinationName(message, destination);
                if (helper.ignoreDestination(destinationName)) {
                    return null;
                }
            }

            // Create a transaction - even if running on same JVM as the sender
            Transaction transaction = helper.startJmsTransaction(message, clazz);
            if (transaction != null) {
                transaction.withType(MESSAGING_TYPE)
                    .withName(RECEIVE_NAME_PREFIX);

                if (destinationName != null) {
                    helper.addDestinationDetails(destination, destinationName, transaction.appendToName(" from "));
                }
                helper.addMessageDetails(message, transaction);
                helper.setMessageAge(message, transaction);
                transaction.activate();
            }

            return transaction;
        }

        @SuppressWarnings("unused")
        @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
        public static void afterOnMessage(@Advice.Enter @Nullable final Object transactionObj,
                                          @Advice.Thrown final Throwable throwable) {
            if (transactionObj instanceof Transaction) {
                Transaction transaction = (Transaction) transactionObj;
                transaction.captureException(throwable);
                transaction.deactivate().end();
            }
        }
    }
}
