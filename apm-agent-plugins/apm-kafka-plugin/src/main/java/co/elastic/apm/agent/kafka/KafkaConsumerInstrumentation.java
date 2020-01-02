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
package co.elastic.apm.agent.kafka;

import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.TraceContextHolder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;

import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;

public class KafkaConsumerInstrumentation extends BaseKafkaInstrumentation {

    public KafkaConsumerInstrumentation(ElasticApmTracer tracer) {
        super(tracer);
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("org.apache.kafka.clients.consumer.KafkaConsumer");
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("poll").and(isPublic());
    }

    @Override
    public Class<?> getAdviceClass() {
        return KafkaConsumerAdvice.class;
    }

    public static class KafkaConsumerAdvice {
        @SuppressWarnings("unused")
        @Advice.OnMethodEnter(suppress = Throwable.class)
        @Nullable
        public static Span pollStart() {
            if (tracer == null) {
                return null;
            }

            final TraceContextHolder<?> activeSpan = tracer.getActive();
            if (activeSpan == null || !activeSpan.isSampled()) {
                return null;
            }

            Span span = activeSpan.createExitSpan();
            if (span == null) {
                return null;
            }

            span.withType("messaging").withSubtype("kafka").withAction("poll")
                .withName("KafkaConsumer#poll", AbstractSpan.PRIO_HIGH_LEVEL_FRAMEWORK)
                .activate();
            return span;
        }

        @SuppressWarnings("unused")
        @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
        public static void pollEnd(@Advice.Enter @Nullable final Span span,
                                   @Advice.Thrown final Throwable throwable) {

            if (span != null) {
                span.captureException(throwable);
                span.deactivate().end();
            }
        }
    }
}
