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

import co.elastic.apm.agent.kafka.helper.KafkaInstrumentationHeadersHelper;
import co.elastic.apm.agent.sdk.advice.AssignTo;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;

import javax.annotation.Nullable;

import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

/**
 * Instruments the {@link ConsumerRecords#records(String)} method
 */
public class ConsumerRecordsRecordsInstrumentation extends KafkaConsumerRecordsInstrumentation {

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("records")
            .and(isPublic())
            .and(takesArgument(0, String.class))
            .and(returns(Iterable.class));
    }

    @Override
    public String getAdviceClassName() {
        return getClass().getName() + "$ConsumerRecordsAdvice";
    }

    public static class ConsumerRecordsAdvice {

        private static final KafkaInstrumentationHeadersHelper helper = KafkaInstrumentationHeadersHelper.get();

        @Nullable
        @AssignTo.Return
        @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
        public static Iterable<ConsumerRecord<?, ?>> wrapIterable(@Advice.Return @Nullable final Iterable<ConsumerRecord<?, ?>> iterable) {
            if (!tracer.isRunning() || tracer.currentTransaction() != null || iterable == null) {
                return iterable;
            }

            return helper.wrapConsumerRecordIterable(iterable);
        }
    }
}
