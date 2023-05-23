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
import co.elastic.apm.agent.tracer.GlobalTracer;
import co.elastic.apm.agent.tracer.AbstractSpan;
import co.elastic.apm.agent.tracer.Span;
import co.elastic.apm.agent.tracer.Transaction;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import static co.elastic.apm.agent.bci.bytebuddy.CustomElementMatchers.classLoaderCanLoadClass;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;

/**
 * Instruments the public {@link org.apache.kafka.clients.consumer.KafkaConsumer#poll} methods.
 * The entry advice is identical for new and old clients, however the exit advice is not - in non-legacy clients, which already support
 * record headers, we want to add span links. Therefore, we have two exit advices.
 */
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
        return "co.elastic.apm.agent.kafka.KafkaConsumerInstrumentation$KafkaPollEntryAdvice";
    }

    public static class KafkaPollEntryAdvice {

        private static final MessagingConfiguration messagingConfiguration = GlobalTracer.get().getConfig(MessagingConfiguration.class);

        @SuppressWarnings("unused")
        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static void pollStart() {

            final AbstractSpan<?> activeSpan = tracer.getActive();
            if (activeSpan == null) {
                return;
            }

            if (messagingConfiguration.shouldEndMessagingTransactionOnPoll() && activeSpan instanceof Transaction) {
                Transaction<?> transaction = (Transaction<?>) activeSpan;
                if ("messaging".equals(transaction.getType())) {
                    transaction.deactivate().end();
                    return;
                }
            }

            Span<?> span = activeSpan.createExitSpan();
            if (span == null) {
                return;
            }

            span.withType("messaging")
                .withSubtype("kafka")
                .withAction("poll")
                .withName("KafkaConsumer#poll", AbstractSpan.PRIORITY_HIGH_LEVEL_FRAMEWORK);

            span.getContext().getServiceTarget().withType("kafka");

            span.activate();
        }
    }

    /**
     * An instrumentation for {@link org.apache.kafka.clients.consumer.KafkaConsumer#poll} exit on legacy clients
     */
    public static class LegacyKafkaPollExitInstrumentation extends KafkaConsumerInstrumentation {
        @Override
        public ElementMatcher.Junction<ClassLoader> getClassLoaderMatcher() {
            return super.getClassLoaderMatcher().and(not(classLoaderCanLoadClass("org.apache.kafka.common.header.Headers")));
        }

        @Override
        public String getAdviceClassName() {
            return "co.elastic.apm.agent.kafka.KafkaConsumerInstrumentation$LegacyKafkaPollExitInstrumentation$KafkaPollExitAdvice";
        }

        public static class KafkaPollExitAdvice {
            @SuppressWarnings("unused")
            @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
            public static void pollEnd(@Advice.Thrown final Throwable throwable) {

                AbstractSpan<?> active = tracer.getActive();
                if (!(active instanceof Span<?>)) {
                    return;
                }
                Span<?> span = (Span<?>) active;
                if ("kafka".equals(span.getSubtype()) && "poll".equals(span.getAction())) {
                    span.captureException(throwable);
                    span.deactivate().end();
                }
            }
        }
    }
}
