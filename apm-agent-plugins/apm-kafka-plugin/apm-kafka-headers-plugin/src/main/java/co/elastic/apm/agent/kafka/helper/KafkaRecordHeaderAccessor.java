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

import co.elastic.apm.agent.impl.transaction.BinaryHeaderGetter;
import co.elastic.apm.agent.impl.transaction.BinaryHeaderSetter;
import co.elastic.apm.agent.impl.transaction.HeaderRemover;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("rawtypes")
class KafkaRecordHeaderAccessor implements BinaryHeaderGetter<ConsumerRecord>, BinaryHeaderSetter<ProducerRecord>,
    HeaderRemover<ProducerRecord> {

    public static final Logger logger = LoggerFactory.getLogger(KafkaRecordHeaderAccessor.class);

    private static final KafkaRecordHeaderAccessor INSTANCE = new KafkaRecordHeaderAccessor();

    private static final ThreadLocal<Map<String, ElasticHeaderImpl>> threadLocalHeaderMap = new ThreadLocal<>();

    public static KafkaRecordHeaderAccessor instance() {
        return INSTANCE;
    }

    @Nullable
    @Override
    public byte[] getFirstHeader(String headerName, ConsumerRecord record) {
        Header traceParentHeader = record.headers().lastHeader(headerName);
        if (traceParentHeader != null) {
            return traceParentHeader.value();
        }
        return null;
    }

    @Override
    public <S> void forEach(String headerName, ConsumerRecord carrier, S state, HeaderConsumer<byte[], S> consumer) {
        for (Header header : carrier.headers().headers(headerName)) {
            consumer.accept(header.value(), state);
        }
    }

    @Override
    @Nullable
    public byte[] getFixedLengthByteArray(String headerName, int length) {
        Map<String, ElasticHeaderImpl> headerMap = threadLocalHeaderMap.get();
        if (headerMap == null) {
            headerMap = new HashMap<>();
            threadLocalHeaderMap.set(headerMap);
        }
        ElasticHeaderImpl header = headerMap.get(headerName);
        if (header == null) {
            header = new ElasticHeaderImpl(headerName, length);
            headerMap.put(headerName, header);
        }
        return header.valueForSetting();
    }

    @Override
    public void setHeader(String headerName, byte[] headerValue, ProducerRecord record) {
        ElasticHeaderImpl header = null;
        Map<String, ElasticHeaderImpl> headerMap = threadLocalHeaderMap.get();
        if (headerMap != null) {
            header = headerMap.get(headerName);
        }
        // Not accessing the value through the method, as it checks the thread
        if (header == null || header.value == null) {
            logger.warn("No header cached for {}, allocating byte array for each record", headerName);
            record.headers().add(headerName, headerValue);
        } else {
            record.headers().add(header);
        }
    }

    @Override
    public void remove(String headerName, ProducerRecord carrier) {
        carrier.headers().remove(headerName);
    }

}
