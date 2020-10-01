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
package co.elastic.apm.agent.impl.transaction;

import javax.annotation.Nullable;
import java.util.List;

class TextTracestateAppender {

    private static final TextTracestateAppender INSTANCE = new TextTracestateAppender();
    private final ThreadLocal<StringBuilder> tracestateBuffer = new ThreadLocal<StringBuilder>();

    static TextTracestateAppender instance() {
        return INSTANCE;
    }

    TextTracestateAppender() {
    }

    @Nullable
    public String join(List<? extends CharSequence> tracestate, int tracestateSizeLimit) {
        StringBuilder buffer = getTracestateBuffer();
        //noinspection ForLoopReplaceableByForEach
        for (int i = 0, size = tracestate.size(); i < size; i++) {
            CharSequence value = tracestate.get(i);
            if (value != null) { // ignore null entries to allow removing entries without resizing collection
                appendTracestateHeaderValue(value, buffer, tracestateSizeLimit);
            }
        }
        return buffer.length() == 0 ? null : buffer.toString();
    }

    void appendTracestateHeaderValue(CharSequence headerValue, StringBuilder tracestateBuffer, int tracestateSizeLimit) {
        int requiredLength = headerValue.length();
        boolean needsComma = tracestateBuffer.length() > 0;
        if (needsComma) {
            requiredLength++;
        }

        if (tracestateBuffer.length() + requiredLength <= tracestateSizeLimit) {
            // header fits completely
            if (needsComma) {
                tracestateBuffer.append(',');
            }
            tracestateBuffer.append(headerValue);
        } else {
            // only part of header might be included
            //
            // When trimming due to size limit, we must include complete entries
            int endIndex = 0;
            for (int i = headerValue.length() - 1; i >= 0; i--) {
                if (headerValue.charAt(i) == ',' && tracestateBuffer.length() + i < tracestateSizeLimit) {
                    endIndex = i;
                    break;
                }
            }
            if (endIndex > 0) {
                if (tracestateBuffer.length() > 0) {
                    tracestateBuffer.append(',');
                }
                tracestateBuffer.append(headerValue, 0, endIndex);
            }
        }

    }

    private StringBuilder getTracestateBuffer() {
        StringBuilder buffer = tracestateBuffer.get();
        if (buffer == null) {
            buffer = new StringBuilder();
            tracestateBuffer.set(buffer);
        } else {
            buffer.setLength(0);
        }
        return buffer;
    }
}
