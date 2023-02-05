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
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;

import javax.annotation.Nullable;
import java.util.Iterator;

import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

/**
 * Instruments the {@link ConsumerRecords#iterator()} method
 */
public class ConsumerRecordsIteratorInstrumentation extends KafkaConsumerRecordsInstrumentation {

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("iterator")
            .and(isPublic())
            .and(takesArguments(0))
            .and(returns(Iterator.class));
    }

    @Override
    public String getAdviceClassName() {
        return getClass().getName() + "$ConsumerRecordsAdvice";
    }

    public static class ConsumerRecordsAdvice {

        private static final KafkaInstrumentationHeadersHelper helper = KafkaInstrumentationHeadersHelper.get();

        @Nullable
        @Advice.AssignReturned.ToReturned
        @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
        public static Iterator<ConsumerRecord<?, ?>> wrapIterator(@Advice.This ConsumerRecords<?, ?> thiz,
                                                                  @Advice.Return @Nullable final Iterator<ConsumerRecord<?, ?>> iterator) {
            if (helper.shouldWrapIterable(thiz, iterator)) {
                return helper.wrapConsumerRecordIterator(iterator);
            } else {
                return iterator;
            }
        }
    }
}
