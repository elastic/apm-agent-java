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

import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;
import co.elastic.apm.agent.tracer.dispatch.HeaderRemover;
import co.elastic.apm.agent.tracer.dispatch.TextHeaderGetter;
import co.elastic.apm.agent.tracer.dispatch.TextHeaderSetter;
import co.elastic.apm.agent.tracer.util.HexUtils;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;

import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@SuppressWarnings("rawtypes")
public class KafkaRecordHeaderAccessor implements TextHeaderGetter<ConsumerRecord>, TextHeaderSetter<ProducerRecord>,
    HeaderRemover<ProducerRecord> {

    public static final Logger logger = LoggerFactory.getLogger(KafkaRecordHeaderAccessor.class);

    private static final KafkaRecordHeaderAccessor INSTANCE = new KafkaRecordHeaderAccessor();

    //TODO: can we somehow make this loom friendly?
    private static final ThreadLocal<Map<String, ElasticHeaderImpl>> threadLocalHeaderMap = new ThreadLocal<>();

    public static final String ELASTIC_TRACE_PARENT_TEXTUAL_HEADER_NAME = "elastic-apm-traceparent";
    public static final String LEGACY_BINARY_TRACEPARENT = "elasticapmtraceparent";

    public static KafkaRecordHeaderAccessor instance() {
        return INSTANCE;
    }

    @Nullable
    @Override
    public String getFirstHeader(String headerName, ConsumerRecord record) {
        if (headerName.equals(ELASTIC_TRACE_PARENT_TEXTUAL_HEADER_NAME)) {
            Header header = record.headers().lastHeader(LEGACY_BINARY_TRACEPARENT);
            if (header != null) {
                return convertLegacyBinaryTraceparentToTextHeader(header.value());
            }
        } else {
            Header header = record.headers().lastHeader(headerName);
            if (header != null) {
                return decodeUtf8(header.value());
            }
        }
        return null;
    }


    @Override
    public <S> void forEach(String headerName, ConsumerRecord carrier, S state, HeaderConsumer<String, S> consumer) {
        if (headerName.equals(ELASTIC_TRACE_PARENT_TEXTUAL_HEADER_NAME)) {
            for (Header header : carrier.headers().headers(LEGACY_BINARY_TRACEPARENT)) {
                String convertedHeader = convertLegacyBinaryTraceparentToTextHeader(header.value());
                if (convertedHeader != null) {
                    consumer.accept(convertedHeader, state);
                }
            }
        } else {
            for (Header header : carrier.headers().headers(headerName)) {
                consumer.accept(decodeUtf8(header.value()), state);
            }
        }
    }


    @Override
    public void setHeader(String headerName, String headerValue, ProducerRecord record) {
        remove(headerName, record);
        if (headerName.equals(ELASTIC_TRACE_PARENT_TEXTUAL_HEADER_NAME)) {
            record.headers().add(LEGACY_BINARY_TRACEPARENT, convertTextHeaderToLegacyBinaryTraceparent(headerValue));
        } else {
            record.headers().add(headerName, encodeUtf8(headerValue));
        }
    }

    @Override
    public void remove(String headerName, ProducerRecord carrier) {
        if (headerName.equals(ELASTIC_TRACE_PARENT_TEXTUAL_HEADER_NAME)) {
            carrier.headers().remove(LEGACY_BINARY_TRACEPARENT);
        } else {
            carrier.headers().remove(headerName);
        }
    }

    private byte[] encodeUtf8(String headerValue) {
        return headerValue.getBytes(StandardCharsets.UTF_8);
    }

    private String decodeUtf8(byte[] value) {
        return new String(value, StandardCharsets.UTF_8);
    }


    private byte[] convertTextHeaderToLegacyBinaryTraceparent(String headerValue) {
        //input is guaranteed to be a valid w3c header, no need to validate
        byte[] buffer = new byte[29];
        buffer[0] = 0; //version
        buffer[1] = 0; //trace-id field-id
        HexUtils.decode(headerValue, 3, 32, buffer, 2); //read 16 byte traceid
        buffer[18] = 1; //parent-id field-id
        HexUtils.decode(headerValue, 36, 16, buffer, 19); //read 16 byte parentid
        buffer[27] = 2; //flags field-id
        buffer[28] = HexUtils.getNextByte(headerValue, 53); //flags
        return buffer;
    }

    @Nullable
    private String convertLegacyBinaryTraceparentToTextHeader(byte[] value) {
        if (value.length < 29) {
            logger.warn("The elasticapmtraceparent header has to be at least 29 bytes long, but is not");
            return null;
        }
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("00-");
            HexUtils.writeBytesAsHex(value, 2, 16, sb);
            sb.append('-');
            HexUtils.writeBytesAsHex(value, 19, 8, sb);
            sb.append('-');
            HexUtils.writeByteAsHex(value[28], sb);
            return sb.toString();
        } catch (Exception e) {
            logger.warn("Failed to parse legacy elasticapmtraceparent header", e);
        }
        return null;
    }


}
