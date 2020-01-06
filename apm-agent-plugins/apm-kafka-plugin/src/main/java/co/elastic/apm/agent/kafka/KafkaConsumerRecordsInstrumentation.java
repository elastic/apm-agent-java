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
import co.elastic.apm.agent.kafka.helper.KafkaInstrumentationHelper;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.common.TopicPartition;

import javax.annotation.Nullable;

import java.util.Iterator;
import java.util.List;

import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

public abstract class KafkaConsumerRecordsInstrumentation extends BaseKafkaInstrumentation {

    public KafkaConsumerRecordsInstrumentation(ElasticApmTracer tracer) {
        super(tracer);
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("org.apache.kafka.clients.consumer.ConsumerRecords");
    }

    public static class IteratorInstrumentation extends KafkaConsumerRecordsInstrumentation {
        public IteratorInstrumentation(ElasticApmTracer tracer) {
            super(tracer);
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("iterator")
                .and(isPublic())
                .and(takesArguments(0))
                .and(returns(Iterator.class));
        }

        @Override
        public Class<?> getAdviceClass() {
            return ConsumerRecordsAdvice.class;
        }

        @SuppressWarnings("rawtypes")
        public static class ConsumerRecordsAdvice {

            @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
            public static void wrapIterator(@Nullable @Advice.Return(readOnly = false) Iterator<ConsumerRecord> iterator) {
                if (tracer == null || tracer.currentTransaction() != null) {
                    return;
                }

                //noinspection ConstantConditions,rawtypes
                KafkaInstrumentationHelper<Callback, ConsumerRecord> kafkaInstrumentationHelper =
                    kafkaInstrHelperManager.getForClassLoaderOfClass(KafkaProducer.class);
                if (iterator != null && kafkaInstrumentationHelper != null) {
                    iterator = kafkaInstrumentationHelper.wrapConsumerRecordIterator(iterator);
                }
            }
        }
    }

    public static class RecordsInstrumentation extends KafkaConsumerRecordsInstrumentation {
        public RecordsInstrumentation(ElasticApmTracer tracer) {
            super(tracer);
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("records")
                .and(isPublic())
                .and(takesArgument(0, String.class))
                .and(returns(Iterable.class));
        }

        @Override
        public Class<?> getAdviceClass() {
            return ConsumerRecordsAdvice.class;
        }

        @SuppressWarnings("rawtypes")
        public static class ConsumerRecordsAdvice {

            @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
            public static void wrapIterable(@Nullable @Advice.Return(readOnly = false) Iterable<ConsumerRecord> iterable) {
                if (tracer == null || tracer.currentTransaction() != null) {
                    return;
                }

                //noinspection ConstantConditions,rawtypes
                KafkaInstrumentationHelper<Callback, ConsumerRecord> kafkaInstrumentationHelper =
                    kafkaInstrHelperManager.getForClassLoaderOfClass(KafkaProducer.class);
                if (iterable != null && kafkaInstrumentationHelper != null) {
                    iterable = kafkaInstrumentationHelper.wrapConsumerRecordIterable(iterable);
                }
            }
        }
    }

    public static class RecordsListInstrumentation extends KafkaConsumerRecordsInstrumentation {
        public RecordsListInstrumentation(ElasticApmTracer tracer) {
            super(tracer);
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("records")
                .and(isPublic())
                .and(takesArgument(0, TopicPartition.class))
                .and(returns(List.class));
        }

        @Override
        public Class<?> getAdviceClass() {
            return ConsumerRecordsAdvice.class;
        }

        @SuppressWarnings("rawtypes")
        public static class ConsumerRecordsAdvice {

            @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
            public static void wrapRecordList(@Nullable @Advice.Return(readOnly = false) List<ConsumerRecord> list) {
                if (tracer == null || tracer.currentTransaction() != null) {
                    return;
                }

                //noinspection ConstantConditions,rawtypes
                KafkaInstrumentationHelper<Callback, ConsumerRecord> kafkaInstrumentationHelper =
                    kafkaInstrHelperManager.getForClassLoaderOfClass(KafkaProducer.class);
                if (list != null && kafkaInstrumentationHelper != null) {
                    list = kafkaInstrumentationHelper.wrapConsumerRecordList(list);
                }
            }
        }
    }
}
