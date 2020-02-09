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

class TextTracestateAppender implements HeaderGetter.HeaderConsumer<String, TraceContext> {

    private static TextTracestateAppender INSTANCE = new TextTracestateAppender();

    static TextTracestateAppender instance() {
        return INSTANCE;
    }

    TextTracestateAppender() {
    }

    @Override
    public void accept(@Nullable String headerValue, TraceContext traceContext) {
        if (headerValue == null) {
            return;
        }
        // This means that the tracestate buffer will be allocated from pool only if tracestate headers exist
        StringBuilder tracestateBuffer = traceContext.getTracestateBuffer();
        int tracestateSizeLimit = traceContext.coreConfiguration.getTracestateSizeLimit();
        appendTracestateHeaderValue(headerValue, tracestateBuffer, tracestateSizeLimit);
    }

    void appendTracestateHeaderValue(String headerValue, StringBuilder tracestateBuffer, int tracestateSizeLimit) {
        int endIndex = headerValue.length();
        // Check if adding comma and the entire header value will break size limit
        if (tracestateBuffer.length() + endIndex + 1 > tracestateSizeLimit) {
            // When trimming due to size limit, we must include complete entries
            endIndex = 0;
            for (int i = headerValue.length() - 1; i >= 0; i--) {
                if (headerValue.charAt(i) == ',' && tracestateBuffer.length() + i < tracestateSizeLimit) {
                    endIndex = i;
                    break;
                }
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
