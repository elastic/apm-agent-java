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

import co.elastic.apm.agent.bci.bytebuddy.postprocessor.AssignToReturn;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.kafka.helper.KafkaInstrumentationHeadersHelper;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;

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
    public ConsumerRecordsIteratorInstrumentation(ElasticApmTracer tracer) {
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

        @Nullable
        @AssignToReturn
        @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
        public static Iterator<ConsumerRecord> wrapIterator(@Nullable @Advice.Return Iterator<ConsumerRecord> iterator) {
            if (tracer == null || !tracer.isRunning() || tracer.currentTransaction() != null) {
                return iterator;
            }

            //noinspection ConstantConditions,rawtypes
            KafkaInstrumentationHeadersHelper<ConsumerRecord, ProducerRecord> kafkaInstrumentationHelper =
                kafkaInstrHeadersHelperManager.getForClassLoaderOfClass(KafkaProducer.class);
            if (iterator != null && kafkaInstrumentationHelper != null) {
                iterator = kafkaInstrumentationHelper.wrapConsumerRecordIterator(iterator);
            }
            return iterator;
        }
    }
}
