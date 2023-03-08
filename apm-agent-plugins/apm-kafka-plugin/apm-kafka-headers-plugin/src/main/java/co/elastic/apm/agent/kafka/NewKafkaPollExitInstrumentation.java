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

import co.elastic.apm.agent.tracer.AbstractSpan;
import co.elastic.apm.agent.tracer.Span;
import co.elastic.apm.agent.kafka.helper.KafkaInstrumentationHeadersHelper;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.kafka.clients.consumer.ConsumerRecords;

import javax.annotation.Nullable;

import static co.elastic.apm.agent.bci.bytebuddy.CustomElementMatchers.classLoaderCanLoadClass;

/**
 * An instrumentation for {@link org.apache.kafka.clients.consumer.KafkaConsumer#poll} exit on new clients
 */
public class NewKafkaPollExitInstrumentation extends KafkaConsumerInstrumentation {
    @Override
    public ElementMatcher.Junction<ClassLoader> getClassLoaderMatcher() {
        return super.getClassLoaderMatcher().and(classLoaderCanLoadClass("org.apache.kafka.common.header.Headers"));
    }

    @Override
    public String getAdviceClassName() {
        return "co.elastic.apm.agent.kafka.NewKafkaPollExitInstrumentation$KafkaPollExitAdvice";
    }

    public static class KafkaPollExitAdvice {

        private static final KafkaInstrumentationHeadersHelper helper = KafkaInstrumentationHeadersHelper.get();

        @SuppressWarnings("unused")
        @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
        public static void pollEnd(@Advice.Thrown final Throwable throwable,
                                   @Advice.Return @Nullable ConsumerRecords<?, ?> records) {

            AbstractSpan<?> active = tracer.getActive();
            if (!(active instanceof Span<?>)) {
                return;
            }
            Span<?> span = (Span<?>) active;
            if ("kafka".equals(span.getSubtype()) && "poll".equals(span.getAction())) {
                helper.addSpanLinks(records, span);
                span.captureException(throwable)
                    .deactivate()
                    .end();
            }
        }
    }
}
