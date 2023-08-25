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
import co.elastic.apm.agent.tracer.dispatch.UTF8ByteHeaderGetter;
import co.elastic.apm.agent.tracer.dispatch.UTF8ByteHeaderSetter;
import co.elastic.apm.agent.tracer.util.HexUtils;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;

import javax.annotation.Nullable;

@SuppressWarnings("rawtypes")
public class KafkaRecordHeaderAccessor implements UTF8ByteHeaderGetter<ConsumerRecord>, UTF8ByteHeaderSetter<ProducerRecord>,
    HeaderRemover<ProducerRecord> {

    public static final Logger logger = LoggerFactory.getLogger(KafkaRecordHeaderAccessor.class);

    private static final KafkaRecordHeaderAccessor INSTANCE = new KafkaRecordHeaderAccessor();
    public static final String ELASTIC_TRACE_PARENT_TEXTUAL_HEADER_NAME = "elastic-apm-traceparent";
    public static final String LEGACY_BINARY_TRACEPARENT = "elasticapmtraceparent";

    public static KafkaRecordHeaderAccessor instance() {
        return INSTANCE;
    }

    @Nullable
    @Override
    public byte[] getFirstHeader(String headerName, ConsumerRecord record) {
        if (headerName.equals(ELASTIC_TRACE_PARENT_TEXTUAL_HEADER_NAME)) {
            Header header = record.headers().lastHeader(LEGACY_BINARY_TRACEPARENT);
            if (header != null) {
                return convertLegacyBinaryTraceparentToTextHeader(header.value());
            }
        } else {
            Header header = record.headers().lastHeader(headerName);
            if (header != null) {
                return header.value();
            }
        }
        return null;
    }


    @Override
    public <S> void forEach(String headerName, ConsumerRecord carrier, S state, HeaderConsumer<byte[], S> consumer) {
        if (headerName.equals(ELASTIC_TRACE_PARENT_TEXTUAL_HEADER_NAME)) {
            for (Header header : carrier.headers().headers(LEGACY_BINARY_TRACEPARENT)) {
                byte[] convertedHeader = convertLegacyBinaryTraceparentToTextHeader(header.value());
                if (convertedHeader != null) {
                    consumer.accept(convertedHeader, state);
                }
            }
        } else {
            for (Header header : carrier.headers().headers(headerName)) {
                consumer.accept(header.value(), state);
            }
        }
    }


    @Override
    public void setHeader(String headerName, byte[] headerValue, ProducerRecord record) {
        // TODO: this currently allocates! Prior to the removal of binary propagation,
        // custom thread-local cached headers instances with cached byte arrays were used.
        // we can't use ThreadLocals due to loom, but we could use a bounded LRU cache instead
        remove(headerName, record);
        if (headerName.equals(ELASTIC_TRACE_PARENT_TEXTUAL_HEADER_NAME)) {
            record.headers().add(LEGACY_BINARY_TRACEPARENT, convertTextHeaderToLegacyBinaryTraceparent(headerValue));
        } else {
            record.headers().add(headerName, headerValue);
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


    private byte[] convertTextHeaderToLegacyBinaryTraceparent(byte[] asciiTextHeaderValue) {
        //input is guaranteed to be a valid w3c header, no need to validate
        byte[] buffer = new byte[29];
        buffer[0] = 0; //version
        buffer[1] = 0; //trace-id field-id
        HexUtils.decodeAscii(asciiTextHeaderValue, 3, 32, buffer, 2); //read 16 byte traceid
        buffer[18] = 1; //parent-id field-id
        HexUtils.decodeAscii(asciiTextHeaderValue, 36, 16, buffer, 19); //read 16 byte parentid
        buffer[27] = 2; //flags field-id
        buffer[28] = HexUtils.getNextByteAscii(asciiTextHeaderValue, 53); //flags
        return buffer;
    }

    @Nullable
    private byte[] convertLegacyBinaryTraceparentToTextHeader(byte[] binaryHeader) {
        if (binaryHeader.length < 29) {
            logger.warn("The elasticapmtraceparent header has to be at least 29 bytes long, but is not");
            return null;
        }
        try {
            byte[] asciiTextHeader = {
                '0', '0', '-',
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, //traceId
                '-',
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, //parentId
                '-',
                '0', '0' //flags
            };
            HexUtils.writeBytesAsHexAscii(binaryHeader, 2, 16, asciiTextHeader, 3);
            HexUtils.writeBytesAsHexAscii(binaryHeader, 19, 8, asciiTextHeader, 36);
            byte flags = binaryHeader[28];
            HexUtils.writeBytesAsHexAscii(flags, asciiTextHeader, 53);
            return asciiTextHeader;
        } catch (Exception e) {
            logger.warn("Failed to parse legacy elasticapmtraceparent header", e);
        }
        return null;
    }


}
