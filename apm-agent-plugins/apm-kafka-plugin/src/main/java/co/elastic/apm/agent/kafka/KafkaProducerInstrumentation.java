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

import co.elastic.apm.agent.bci.VisibleForAdvice;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.TraceContext;
import co.elastic.apm.agent.impl.transaction.TraceContextHolder;
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

public class KafkaProducerInstrumentation extends BaseKafkaInstrumentation {

    @VisibleForAdvice
    @SuppressWarnings("WeakerAccess")
    public static final Logger logger = LoggerFactory.getLogger(KafkaProducerInstrumentation.class);

    public KafkaProducerInstrumentation(ElasticApmTracer tracer) {
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
    public Class<?> getAdviceClass() {
        return KafkaProducerAdvice.class;
    }

    public static class KafkaProducerAdvice {
        @SuppressWarnings("unused")
        @Advice.OnMethodEnter(suppress = Throwable.class)
        @Nullable
        public static Span beforeSend(@Advice.FieldValue("apiVersions") final ApiVersions apiVersions,
                                      @SuppressWarnings("rawtypes") @Advice.Argument(0) final ProducerRecord record,
                                      @Nullable @Advice.Argument(value = 1, readOnly = false) Callback callback) {
            if (tracer == null) {
                return null;
            }

            String topic = record.topic();
            if (ignoreTopic(topic)) {
                return null;
            }

            final TraceContextHolder<?> activeSpan = tracer.getActive();
            if (activeSpan == null || !activeSpan.isSampled()) {
                return null;
            }

            if (activeSpan instanceof Span && "kafka".equals(((Span) activeSpan).getSubtype())) {
                return null;
            }

            Span span = activeSpan.createExitSpan();
            if (span == null) {
                return null;
            }

            //noinspection ConstantConditions,rawtypes
            KafkaInstrumentationHelper<Callback, ConsumerRecord> kafkaInstrumentationHelper =
                kafkaInstrHelperManager.getForClassLoaderOfClass(KafkaProducer.class);
            //noinspection ConstantConditions
            callback = kafkaInstrumentationHelper.wrapCallback(callback, span);

            span.withType("messaging").withSubtype("kafka").withAction("send");
            span.withName("KafkaProducer#send to ").appendToName(topic);
            span.getContext().getMessage().withQueue(topic);

            // Avoid adding headers to records sent to a version older than 0.11.0 - see specifications in
            // https://kafka.apache.org/0110/documentation.html#messageformat
            if (apiVersions.maxUsableProduceMagic() >= RecordBatch.MAGIC_VALUE_V2) {
                try {
                    record.headers().add(TraceContext.TRACE_PARENT_HEADER,
                        span.getTraceContext().getOutgoingTraceParentBinaryHeader());
                } catch (final IllegalStateException e) {
                    // headers are in a read-only state
                    logger.debug("Failed to add header to Kafka record {}, probably to headers' read-only state.", record);
                }
            }

            span.activate();
            return span;
        }

        @SuppressWarnings("unused")
        @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
        public static void afterSend(@Advice.Enter @Nullable final Span span,
                                     @Advice.Thrown final Throwable throwable) {

            if (span != null) {
                span.captureException(throwable);
                // Not ending here- ending in the callback
                span.deactivate();
            }
        }
    }
}
