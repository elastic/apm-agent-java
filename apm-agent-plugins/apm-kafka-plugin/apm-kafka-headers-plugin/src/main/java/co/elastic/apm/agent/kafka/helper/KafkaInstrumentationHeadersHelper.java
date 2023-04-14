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
package co.elastic.apm.agent.kafka.helper;

import co.elastic.apm.agent.tracer.AbstractSpan;
import co.elastic.apm.agent.tracer.GlobalTracer;
import co.elastic.apm.agent.tracer.Span;
import co.elastic.apm.agent.tracer.Tracer;
import co.elastic.apm.agent.tracer.dispatch.HeaderUtils;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.ProducerRecord;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public class KafkaInstrumentationHeadersHelper {

    private static final Logger logger = LoggerFactory.getLogger(KafkaInstrumentationHeadersHelper.class);
    private static final KafkaInstrumentationHeadersHelper INSTANCE = new KafkaInstrumentationHeadersHelper(GlobalTracer.get());

    private static final ThreadLocal<Boolean> wrappingDisabled = new ThreadLocal<Boolean>() {
        @Override
        protected Boolean initialValue() {
            return Boolean.FALSE;
        }
    };

    private final Tracer tracer;
    private final Set<String> binaryTraceHeaders = new HashSet<>();
    private final Map<String, String> translatedTraceHeaders = new HashMap<>();

    public static KafkaInstrumentationHeadersHelper get() {
        return INSTANCE;
    }

    public KafkaInstrumentationHeadersHelper(Tracer tracer) {
        this.tracer = tracer;
        Pattern pattern = Pattern.compile("[^a-zA-Z0-9]");
        Set<String> traceHeaders = tracer.getTraceHeaderNames();
        for (String traceHeader : traceHeaders) {
            String binaryTraceHeader = pattern.matcher(traceHeader).replaceAll("");
            if (!binaryTraceHeaders.add(binaryTraceHeader)) {
                throw new IllegalStateException("Ambiguous translation of trace headers into binary format: " + traceHeaders);
            }
            translatedTraceHeaders.put(traceHeader, binaryTraceHeader);
        }
    }

    public String resolvePossibleTraceHeader(String header) {
        String translation = translatedTraceHeaders.get(header);
        return translation == null ? header : translation;
    }

    public Iterator<ConsumerRecord<?, ?>> wrapConsumerRecordIterator(Iterator<ConsumerRecord<?, ?>> consumerRecordIterator) {
        try {
            return new ConsumerRecordsIteratorWrapper(consumerRecordIterator, tracer, binaryTraceHeaders);
        } catch (Throwable throwable) {
            logger.debug("Failed to wrap Kafka ConsumerRecords iterator", throwable);
            return consumerRecordIterator;
        }
    }

    public Iterable<ConsumerRecord<?, ?>> wrapConsumerRecordIterable(Iterable<ConsumerRecord<?, ?>> consumerRecordIterable) {
        try {
            return new ConsumerRecordsIterableWrapper(consumerRecordIterable, tracer, binaryTraceHeaders);
        } catch (Throwable throwable) {
            logger.debug("Failed to wrap Kafka ConsumerRecords", throwable);
            return consumerRecordIterable;
        }
    }

    public List<ConsumerRecord<?, ?>> wrapConsumerRecordList(List<ConsumerRecord<?, ?>> consumerRecordList) {
        try {
            return new ConsumerRecordsListWrapper(consumerRecordList, tracer, binaryTraceHeaders);
        } catch (Throwable throwable) {
            logger.debug("Failed to wrap Kafka ConsumerRecords list", throwable);
            return consumerRecordList;
        }
    }

    /**
     * Checks whether the provided iterable should be wrapped.
     * If there is an active span when this method is invoked, span links are added to it based non the provided {@link ConsumerRecords}
     * and this method returns {@code false}.
     * @param consumerRecords the {@link ConsumerRecords} object from which this method is invoked when trying to obtain an iterable
     * @param iterable the original iterable object returned by the instrumented method
     * @return {@code true} if the provided iterable object should be wrapped, {@code false} otherwise
     */
    public boolean shouldWrapIterable(ConsumerRecords<?, ?> consumerRecords, @Nullable Object iterable) {
        if (wrappingDisabled.get() || !tracer.isRunning() || iterable == null) {
            return false;
        }
        AbstractSpan<?> activeSpan = tracer.getActive();
        if (activeSpan != null) {
            addSpanLinks(consumerRecords, activeSpan);
            return false;
        }
        return true;
    }

    public void addSpanLinks(@Nullable ConsumerRecords<?, ?> records, AbstractSpan<?> span) {
        if (records != null && !records.isEmpty()) {
            // Avoid stack overflow by trying to re-wrap and avoid adding span links for this iteration
            wrappingDisabled.set(Boolean.TRUE);
            try {
                for (ConsumerRecord<?, ?> record : records) {
                    span.addLink(KafkaRecordHeaderAccessor.instance(), record);
                }
            } finally {
                wrappingDisabled.set(false);
            }
        }
    }

    public void setOutgoingTraceContextHeaders(Span<?> span, ProducerRecord<?, ?> producerRecord) {
        span.propagateTraceContext(producerRecord, KafkaRecordHeaderAccessor.instance());
    }

    public void removeTraceContextHeader(ProducerRecord<?, ?> producerRecord) {
        HeaderUtils.remove(binaryTraceHeaders, producerRecord, KafkaRecordHeaderAccessor.instance());
    }
}
