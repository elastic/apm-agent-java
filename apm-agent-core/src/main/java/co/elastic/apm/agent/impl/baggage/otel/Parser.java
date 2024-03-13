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
/**
 * This class has been copied from OTel release v1.27.0 and adjusted to use our internal baggage builder instead.
 */
/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package co.elastic.apm.agent.impl.baggage.otel;

import co.elastic.apm.agent.impl.baggage.BaggageImpl;

import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;

/**
 * Implements single-pass Baggage parsing in accordance with https://w3c.github.io/baggage/ Key /
 * value are restricted in accordance with https://www.ietf.org/rfc/rfc2616.txt
 *
 * <p>Note: following aspects are not specified in RFC: - some invalid elements (key or value) -
 * parser will include valid ones, disregard invalid - empty "value" is regarded as invalid - meta -
 * anything besides element terminator (comma)
 */
public class Parser {

    private enum State {
        KEY,
        VALUE,
        META
    }

    private final String baggageHeader;

    private final Element key = Element.createKeyElement();
    private final Element value = Element.createValueElement();
    private String meta;

    private State state;
    private int metaStart;

    private boolean skipToNext;

    public Parser(String baggageHeader) {
        this.baggageHeader = baggageHeader;
        reset(0);
    }

    public void parseInto(BaggageImpl.Builder baggageBuilder) {
        for (int i = 0, n = baggageHeader.length(); i < n; i++) {
            char current = baggageHeader.charAt(i);

            if (skipToNext) {
                if (current == ',') {
                    reset(i + 1);
                }
                continue;
            }

            switch (current) {
                case '=': {
                    if (state == State.KEY) {
                        if (key.tryTerminating(i, baggageHeader)) {
                            setState(State.VALUE, i + 1);
                        } else {
                            skipToNext = true;
                        }
                    } else if (state == State.VALUE) {
                        skipToNext = !value.tryNextChar(current, i);
                    }
                    break;
                }
                case ';': {
                    if (state == State.VALUE) {
                        skipToNext = !value.tryTerminating(i, baggageHeader);
                        setState(State.META, i + 1);
                    }
                    break;
                }
                case ',': {
                    switch (state) {
                        case VALUE:
                            value.tryTerminating(i, baggageHeader);
                            break;
                        case META:
                            meta = baggageHeader.substring(metaStart, i).trim();
                            break;
                        case KEY: // none
                    }
                    putBaggage(baggageBuilder, key.getValue(), value.getValue(), meta);
                    reset(i + 1);
                    break;
                }
                default: {
                    switch (state) {
                        case KEY:
                            skipToNext = !key.tryNextChar(current, i);
                            break;
                        case VALUE:
                            skipToNext = !value.tryNextChar(current, i);
                            break;
                        case META: // none
                    }
                }
            }
        }
        // need to finish parsing if there was no list element termination comma
        switch (state) {
            case KEY:
                break;
            case META: {
                String rest = baggageHeader.substring(metaStart).trim();
                putBaggage(baggageBuilder, key.getValue(), value.getValue(), rest);
                break;
            }
            case VALUE: {
                if (!skipToNext) {
                    value.tryTerminating(baggageHeader.length(), baggageHeader);
                    putBaggage(baggageBuilder, key.getValue(), value.getValue(), null);
                    break;
                }
            }
        }
    }

    private static void putBaggage(
        BaggageImpl.Builder baggage,
        @Nullable String key,
        @Nullable String value,
        @Nullable String metadataValue) {
        String decodedValue = decodeValue(value);
        if (key != null && decodedValue != null) {
            baggage.put(key, decodedValue, metadataValue);
        }
    }

    @Nullable
    private static String decodeValue(@Nullable String value) {
        if (value == null) {
            return null;
        }
        return BaggageCodec.decode(value, StandardCharsets.UTF_8);
    }

    /**
     * Resets parsing state, preparing to start a new list element (see spec).
     *
     * @param index index where parser should start new element scan
     */
    private void reset(int index) {
        this.skipToNext = false;
        this.state = State.KEY;
        this.key.reset(index);
        this.value.reset(index);
        this.meta = "";
        this.metaStart = 0;
    }

    /**
     * Switches parser state (element of a list member).
     */
    private void setState(State state, int start) {
        this.state = state;
        this.metaStart = start;
    }
}
