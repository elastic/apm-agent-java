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
package co.elastic.apm.agent.kafka;

import co.elastic.apm.agent.configuration.MessagingConfiguration;
import co.elastic.apm.agent.impl.GlobalTracer;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.Transaction;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;

import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;

public class KafkaConsumerInstrumentation extends BaseKafkaInstrumentation {

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("org.apache.kafka.clients.consumer.KafkaConsumer");
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("poll").and(isPublic());
    }

    @Override
    public String getAdviceClassName() {
        return "co.elastic.apm.agent.kafka.KafkaConsumerInstrumentation$KafkaConsumerAdvice";
    }

    public static class KafkaConsumerAdvice {

        private static final MessagingConfiguration messagingConfiguration = GlobalTracer.requireTracerImpl().getConfig(MessagingConfiguration.class);

        @SuppressWarnings("unused")
        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        @Nullable
        public static Object pollStart() {

            final AbstractSpan<?> activeSpan = tracer.getActive();
            if (activeSpan == null) {
                return null;
            }

            if (messagingConfiguration.shouldEndMessagingTransactionOnPoll() && activeSpan instanceof Transaction) {
                Transaction transaction = (Transaction) activeSpan;
                if ("messaging".equals(transaction.getType())) {
                    transaction.deactivate().end();
                    return null;
                }
            }

            Span span = activeSpan.createExitSpan();
            if (span == null) {
                return null;
            }

            span.withType("messaging")
                .withSubtype("kafka")
                .withAction("poll")
                .withName("KafkaConsumer#poll", AbstractSpan.PRIO_HIGH_LEVEL_FRAMEWORK);

            span.getContext().getServiceTarget().withType("kafka");

            span.activate();
            return span;
        }

        @SuppressWarnings("unused")
        @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
        public static void pollEnd(@Advice.Enter @Nullable final Object spanObj,
                                   @Advice.Thrown final Throwable throwable) {

            Span span = (Span) spanObj;
            if (span != null) {
                span.captureException(throwable);
                span.deactivate().end();
            }
        }
    }
}
