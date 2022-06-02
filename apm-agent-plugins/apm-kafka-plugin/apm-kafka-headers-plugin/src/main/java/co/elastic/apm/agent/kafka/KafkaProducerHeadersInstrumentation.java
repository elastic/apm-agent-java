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

import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.kafka.helper.KafkaInstrumentationHeadersHelper;
import co.elastic.apm.agent.kafka.helper.KafkaInstrumentationHelper;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned.ToArguments.ToArgument;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.kafka.clients.ApiVersions;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.record.RecordBatch;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;

import javax.annotation.Nullable;

import static net.bytebuddy.implementation.bytecode.assign.Assigner.Typing.DYNAMIC;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

public class KafkaProducerHeadersInstrumentation extends BaseKafkaHeadersInstrumentation {

    private static final Logger logger = LoggerFactory.getLogger(KafkaProducerInstrumentation.class);

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
        return getClass().getName() + "$KafkaProducerHeadersAdvice";
    }

    public static class KafkaProducerHeadersAdvice {
        private static final KafkaInstrumentationHelper helper = KafkaInstrumentationHelper.get();
        private static final KafkaInstrumentationHeadersHelper headersHelper = KafkaInstrumentationHeadersHelper.get();
        private static boolean headersSupported = true;

        @Nullable
        @Advice.AssignReturned.ToArguments(@ToArgument(value = 1, index = 1, typing = DYNAMIC))
        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static Object[] beforeSend(@Advice.FieldValue("apiVersions") final ApiVersions apiVersions,
                                          @Advice.Argument(0) final ProducerRecord<?, ?> record,
                                          @Nullable @Advice.Argument(value = 1) Callback callback) {
            Span span = helper.onSendStart(record);
            if (span == null) {
                return null;
            }

            // Avoid adding headers to records sent to a version older than 0.11.0 - see specifications in
            // https://kafka.apache.org/0110/documentation.html#messageformat
            if (apiVersions.maxUsableProduceMagic() >= RecordBatch.MAGIC_VALUE_V2 && headersSupported) {
                try {
                    headersHelper.setOutgoingTraceContextHeaders(span, record);
                } catch (final IllegalStateException e) {
                    // headers are in a read-only state
                    logger.debug("Failed to add header to Kafka record {}, probably to headers' read-only state.", record);
                }
            }

            return new Object[]{span, helper.wrapCallback(callback, span)};
        }

        /*
         * Implementation notes on the return value:
         * Returning Throwable doesn't work because we can only remove the exception with Object[]{null}.
         * When changing the return type to Throwable and returning null, it would still not change the exception if there's any.
         * That is to be consistent with the @AssignTo.* annotations that don't allow assigning something to null.
         * The reason is that if an advice method throws an exception, the return value will always be null.
         * We want to avoid assigning that null value as that will have unintended consequences.
         */
        @Nullable
        @Advice.AssignReturned.ToThrown(index = 0, typing = DYNAMIC)
        @SuppressWarnings({"unchecked", "rawtypes"})
        @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
        public static Object[] afterSend(@Advice.Enter @Nullable Object[] enter,
                                          @Advice.Argument(value = 0) ProducerRecord<?, ?> record,
                                          @Advice.Argument(value = 1) Callback callback,
                                          @Advice.This final KafkaProducer<?, ?> thiz,
                                          @Advice.Thrown @Nullable final Throwable throwable) {

            Span span = enter != null ? (Span) enter[0] : null;
            if (span == null) {
                return null;
            }
            Object[] overrideThrowable = null;
            if (throwable != null && throwable.getMessage().contains("Magic v1 does not support record headers")) {
                // Probably our fault - ignore span and retry. May happen when using a new client with an old (< 0.11.0)
                // broker. In such cases we DO check the version, but the first version check may be not yet up to date.
                logger.info("Adding header to Kafka record is not allowed with the used broker, attempting to resend record");
                ProducerRecord copy = new ProducerRecord<>(record.topic(), record.partition(), record.timestamp(),
                    record.key(), record.value(), record.headers());
                headersHelper.removeTraceContextHeader(copy);
                headersSupported = false;
                thiz.send(copy, callback);
                overrideThrowable = new Object[]{null};
            }
            helper.onSendEnd(span, record, thiz, throwable);
            return overrideThrowable;
        }
    }

}
