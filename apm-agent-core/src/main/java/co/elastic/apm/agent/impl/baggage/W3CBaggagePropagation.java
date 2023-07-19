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
package co.elastic.apm.agent.impl.baggage;

import co.elastic.apm.agent.impl.baggage.otel.Parser;
import co.elastic.apm.agent.impl.baggage.otel.PercentEscaper;
import co.elastic.apm.agent.tracer.dispatch.HeaderGetter;
import co.elastic.apm.agent.tracer.dispatch.TextHeaderGetter;
import co.elastic.apm.agent.tracer.dispatch.TextHeaderSetter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

public class W3CBaggagePropagation {

    private static final Logger logger = LoggerFactory.getLogger(W3CBaggagePropagation.class);

    private static final PercentEscaper VALUE_ESCAPER = PercentEscaper.create();

    public static final String BAGGAGE_HEADER_NAME = "baggage";

    private static final HeaderGetter.HeaderConsumer<String, Baggage.Builder> PARSING_CONSUMER = new HeaderGetter.HeaderConsumer<String, Baggage.Builder>() {
        @Override
        public void accept(@Nullable String headerValue, Baggage.Builder state) {
            if (headerValue != null) {
                try {
                    new Parser(headerValue).parseInto(state);
                } catch (Exception e) {
                    logger.error("Failed to parse baggage header: {}", headerValue, e);
                }
            }
        }
    };

    public static <C> void parse(C carrier, TextHeaderGetter<C> headerGetter, Baggage.Builder into) {
        headerGetter.forEach(BAGGAGE_HEADER_NAME, carrier, into, PARSING_CONSUMER);
    }

    public static <C> void propagate(Baggage baggage, C carrier, TextHeaderSetter<C> setter) {
        if (baggage.isEmpty()) {
            return;
        }
        String header = baggage.getCachedSerializedW3CHeader();
        if (header == null) {
            header = encodeToHeader(baggage);
            baggage.setCachedSerializedW3CHeader(header);
        }
        if (!header.isEmpty()) {
            setter.setHeader(BAGGAGE_HEADER_NAME, encodeToHeader(baggage), carrier);
        }
    }


    private static String encodeToHeader(Baggage baggage) {
        StringBuilder header = new StringBuilder();
        for (String key : baggage.keys()) {
            String value = baggage.get(key);
            String metadata = baggage.getMetadata(key);
            if (isEncodeableBaggage(key, value)) {
                if (header.length() > 0) {
                    header.append(",");
                }
                header.append(key).append('=').append(VALUE_ESCAPER.escape(value));
                if (metadata != null && !metadata.isEmpty()) {
                    header.append(';').append(metadata);
                }
            } else {
                logger.debug("Skipping encoding of baggage {} because it is not encodeable!", key);
            }
        }
        return header.toString();
    }


    private static boolean isEncodeableBaggage(String key, String value) {
        if (key == null || key.isEmpty()) {
            return false;
        }
        if (value == null) {
            return false;
        }
        for (int i = 0; i < key.length(); i++) {
            char ch = key.charAt(i);
            if (!isAllowedKeyChar(ch)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isAllowedKeyChar(char ch) {
        //Check for RFC tchar: https://datatracker.ietf.org/doc/html/rfc7230#section-3.2.6
        switch (ch) {
            case '!':
            case '#':
            case '$':
            case '%':
            case '&':
            case '\'':
            case '*':
            case '+':
            case '-':
            case '.':
            case '^':
            case '_':
            case '`':
            case '|':
            case '~':
                return true;
            default:
                return (ch >= '0' && ch <= '9') || (ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z');
        }
    }

}
