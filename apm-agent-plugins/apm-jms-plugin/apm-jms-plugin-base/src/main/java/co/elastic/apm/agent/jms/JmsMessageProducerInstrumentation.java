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

import co.elastic.apm.agent.impl.transaction.Span;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;

import javax.annotation.Nullable;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageProducer;

import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.nameContains;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

/**
 * In JMS 2 there is also JMSProducer, but likely its implementation is usually using a MessageProducer underneath,
 * unless the client does not support JMS 1 at all.
 * Currently tested using ActiveMQ Artemis, which is the successor of HornetQ, both of which are traced properly when
 * using JMS 2 API, buy this instrumentation of JMS 1 API.
 */
public abstract class JmsMessageProducerInstrumentation extends BaseJmsInstrumentation {

    @SuppressWarnings("WeakerAccess")
    public static final Logger logger = LoggerFactory.getLogger(JmsMessageProducerInstrumentation.class);

    @Override
    public ElementMatcher<? super NamedElement> getTypeMatcherPreFilter() {
        return nameContains("Message")
            .or(nameContains("Producer"))
            .or(nameContains("Sender"))
            .or(nameContains("Publisher"));
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return not(isInterface()).and(hasSuperType(named("javax.jms.MessageProducer")));
    }

    public static class JmsMessageProducerNoDestinationInstrumentation extends JmsMessageProducerInstrumentation {

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("send").and(takesArgument(0, named("javax.jms.Message"))).and(isPublic());
        }

        @Override
        public String getAdviceClassName() {
            return "co.elastic.apm.agent.jms.JmsMessageProducerInstrumentation$JmsMessageProducerNoDestinationInstrumentation$MessageProducerNoDestinationAdvice";
        }

        public static class MessageProducerNoDestinationAdvice extends BaseAdvice {
            @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
            @Nullable
            public static Object beforeSend(@Advice.Argument(0) final Message message,
                                            @Advice.This final MessageProducer producer) {

                try {
                    Destination destination = producer.getDestination();
                    return helper.startJmsSendSpan(destination, message);
                } catch (JMSException e) {
                    logger.warn("Failed to retrieve message's destination", e);
                }
                return null;
            }

            @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
            public static void afterSend(@Advice.Enter @Nullable final Object spanObj,
                                         @Advice.Thrown final Throwable throwable) {

                if (spanObj instanceof Span) {
                    Span span = (Span) spanObj;
                    span.captureException(throwable);
                    span.deactivate().end();
                }
            }
        }
    }

    public static class JmsMessageProducerWithDestinationInstrumentation extends JmsMessageProducerInstrumentation {

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("send")
                .and(takesArgument(0, named("javax.jms.Destination")))
                .and(takesArgument(1, named("javax.jms.Message")))
                .and(isPublic());
        }

        @Override
        public String getAdviceClassName() {
            return "co.elastic.apm.agent.jms.JmsMessageProducerInstrumentation$JmsMessageProducerWithDestinationInstrumentation$MessageProducerWithDestinationAdvice";
        }

        public static class MessageProducerWithDestinationAdvice extends BaseAdvice {

            @Nullable
            @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
            public static Object startSpan(@Advice.Argument(0) final Destination destination,
                                           @Advice.Argument(1) final Message message) {
                return helper.startJmsSendSpan(destination, message);
            }

            @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
            public static void endSpan(@Advice.Enter @Nullable final Object spanObj,
                                       @Advice.Thrown final Throwable throwable) {

                if (spanObj instanceof Span) {
                    Span span = (Span) spanObj;
                    span.captureException(throwable);
                    span.deactivate().end();
                }
            }
        }
    }
}
