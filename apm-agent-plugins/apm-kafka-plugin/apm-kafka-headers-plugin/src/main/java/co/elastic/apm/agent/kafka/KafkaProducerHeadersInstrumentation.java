/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2021 Elastic and contributors
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

import co.elastic.apm.agent.bci.VisibleForAdvice;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.kafka.helper.KafkaInstrumentationHeadersHelper;
import co.elastic.apm.agent.kafka.helper.KafkaInstrumentationHelper;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.kafka.clients.ApiVersions;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.record.RecordBatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

public class KafkaProducerHeadersInstrumentation extends BaseKafkaHeadersInstrumentation {

    @VisibleForAdvice
    @SuppressWarnings("WeakerAccess")
    public static final Logger logger = LoggerFactory.getLogger(KafkaProducerInstrumentation.class);

    @VisibleForAdvice
    public static boolean headersSupported = true;

    public KafkaProducerHeadersInstrumentation(ElasticApmTracer tracer) {
        super(tracer);
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
    public String getAdviceClassName() {
        return "co.elastic.apm.agent.kafka.KafkaProducerHeadersInstrumentation$KafkaProducerHeadersAdvice";
    }

    @SuppressWarnings("rawtypes")
    public static class KafkaProducerHeadersAdvice {

        @SuppressWarnings({"unused", "DuplicatedCode", "ParameterCanBeLocal"})
        @Advice.OnMethodEnter(suppress = Throwable.class)
        @Nullable
        public static Span beforeSend(@Advice.FieldValue("apiVersions") final ApiVersions apiVersions,
                                      @Advice.Argument(0) final ProducerRecord record,
                                      @Nullable @Advice.Argument(value = 1, readOnly = false) Callback callback) {
            Span span = null;

            //noinspection ConstantConditions
            KafkaInstrumentationHelper<Callback, ProducerRecord, KafkaProducer> helper = kafkaInstrHelperManager.getForClassLoaderOfClass(KafkaProducer.class);

            if (helper != null) {
                span = helper.onSendStart(record);
            }
            if (span == null) {
                return null;
            }

            // Avoid adding headers to records sent to a version older than 0.11.0 - see specifications in
            // https://kafka.apache.org/0110/documentation.html#messageformat
            if (apiVersions.maxUsableProduceMagic() >= RecordBatch.MAGIC_VALUE_V2 && headersSupported) {
                try {
                    //noinspection ConstantConditions
                    KafkaInstrumentationHeadersHelper<ConsumerRecord, ProducerRecord> kafkaInstrumentationHelper =
                        kafkaInstrHeadersHelperManager.getForClassLoaderOfClass(KafkaProducer.class);
                    if (kafkaInstrumentationHelper != null) {
                        kafkaInstrumentationHelper.setOutgoingTraceContextHeaders(span, record);
                    }
                } catch (final IllegalStateException e) {
                    // headers are in a read-only state
                    logger.debug("Failed to add header to Kafka record {}, probably to headers' read-only state.", record);
                }
            }

            //noinspection UnusedAssignment
            callback = helper.wrapCallback(callback, span);
            return span;
        }

        @SuppressWarnings("unused")
        @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, repeatOn = Advice.OnNonDefaultValue.class)
        public static boolean afterSend(@Advice.Enter(readOnly = false) @Nullable Span span,
                                        @Advice.Argument(value = 0, readOnly = false) ProducerRecord record,
                                        @Advice.This final KafkaProducer thiz,
                                        @Advice.Thrown @Nullable final Throwable throwable) {

            if (throwable != null && throwable.getMessage().contains("Magic v1 does not support record headers")) {
                // Probably our fault - ignore span and retry. May happen when using a new client with an old (< 0.11.0)
                // broker. In such cases we DO check the version, but the first version check may be not yet up to date.
                if (span != null) {
                    logger.info("Adding header to Kafka record is not allowed with the used broker, attempting to resend record");
                    //noinspection unchecked
                    record = new ProducerRecord(record.topic(), record.partition(), record.timestamp(),
                        record.key(), record.value(), record.headers());
                    //noinspection ConstantConditions
                    KafkaInstrumentationHeadersHelper<ConsumerRecord, ProducerRecord> kafkaInstrumentationHelper =
                        kafkaInstrHeadersHelperManager.getForClassLoaderOfClass(KafkaProducer.class);
                    if (kafkaInstrumentationHelper != null) {
                        kafkaInstrumentationHelper.removeTraceContextHeader(record);
                    }
                    span.deactivate();
                    span = null;
                    headersSupported = false;
                    return true;
                }
            }
            //noinspection ConstantConditions
            KafkaInstrumentationHelper<Callback, ProducerRecord, KafkaProducer> helper = kafkaInstrHelperManager.getForClassLoaderOfClass(KafkaProducer.class);
            if (helper != null && span != null) {
                helper.onSendEnd(span, record, thiz, throwable);
            }
            return false;
        }
    }
}
