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
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.kafka.helper.KafkaInstrumentationHelper;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;

import javax.annotation.Nullable;

import static co.elastic.apm.agent.bci.bytebuddy.CustomElementMatchers.classLoaderCanLoadClass;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

public class KafkaProducerInstrumentation extends BaseKafkaInstrumentation {

    public KafkaProducerInstrumentation(ElasticApmTracer tracer) {
        super(tracer);
    }

    @Override
    public ElementMatcher.Junction<ClassLoader> getClassLoaderMatcher() {
        return super.getClassLoaderMatcher().and(not(classLoaderCanLoadClass("org.apache.kafka.common.header.Headers")));
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("org.apache.kafka.clients.producer.KafkaProducer");
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("doSend").and(takesArgument(0, named("org.apache.kafka.clients.producer.ProducerRecord")));
    }

    @Override
    public Class<?> getAdviceClass() {
        return KafkaProducerAdvice.class;
    }

    @SuppressWarnings("rawtypes")
    public static class KafkaProducerAdvice {
        @SuppressWarnings({"unused", "DuplicatedCode", "ParameterCanBeLocal"})
        @Advice.OnMethodEnter(suppress = Throwable.class)
        @Nullable
        public static Span beforeSend(@Advice.Argument(0) final ProducerRecord record,
                                      @Advice.Argument(value = 1, readOnly = false) @Nullable Callback callback,
                                      @Advice.Local("helper") @Nullable KafkaInstrumentationHelper<Callback, ProducerRecord, KafkaProducer> helper) {
            if (tracer == null) {
                return null;
            }
            Span span = null;

            //noinspection ConstantConditions
            helper = kafkaInstrHelperManager.getForClassLoaderOfClass(KafkaProducer.class);

            if (helper != null) {
                span = helper.onSendStart(record);
            }
            if (span == null) {
                return null;
            }

            //noinspection UnusedAssignment
            callback = helper.wrapCallback(callback, span);
            return span;
        }

        @SuppressWarnings("unused")
        @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
        public static void afterSend(@Advice.Enter @Nullable final Span span,
                                     @Advice.Argument(0) final ProducerRecord record,
                                     @Advice.This final KafkaProducer thiz,
                                     @Advice.Local("helper") @Nullable KafkaInstrumentationHelper<Callback, ProducerRecord, KafkaProducer> helper,
                                     @Advice.Thrown final Throwable throwable) {

            if (helper != null && span != null) {
                helper.onSendEnd(span, record, thiz, throwable);
            }
        }
    }
}
