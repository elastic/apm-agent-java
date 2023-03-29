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
package co.elastic.apm.agent.impl;

import co.elastic.apm.agent.impl.transaction.TraceContext;
import co.elastic.apm.agent.tracer.dispatch.BinaryHeaderGetter;
import co.elastic.apm.agent.tracer.dispatch.BinaryHeaderSetter;
import co.elastic.apm.agent.tracer.dispatch.HeaderRemover;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

public class BinaryHeaderMapAccessor implements BinaryHeaderGetter<Map<String, byte[]>>,
    BinaryHeaderSetter<Map<String, byte[]>>, HeaderRemover<Map<String, byte[]>> {

    public static final BinaryHeaderMapAccessor INSTANCE = new BinaryHeaderMapAccessor();

    private final Map<String, byte[]> headerCache = new HashMap<>();

    private BinaryHeaderMapAccessor() {
    }

    @Nullable
    @Override
    public byte[] getFirstHeader(String headerName, Map<String, byte[]> headerMap) {
        return headerMap.get(headerName);
    }

    @Override
    public <S> void forEach(String headerName, Map<String, byte[]> carrier, S state, HeaderConsumer<byte[], S> consumer) {
        byte[] headerValue = carrier.get(headerName);
        if (headerValue != null) {
            consumer.accept(headerValue, state);
        }
    }

    @Nullable
    @Override
    public byte[] getFixedLengthByteArray(String headerName, int length) {
        headerCache.computeIfAbsent(headerName, k -> new byte[TraceContext.BINARY_FORMAT_EXPECTED_LENGTH]);
        return headerCache.get(headerName);
    }

    @Override
    public void setHeader(String headerName, byte[] headerValue, Map<String, byte[]> headerMap) {
        headerMap.put(headerName, headerValue);
    }

    @Override
    public void remove(String headerName, Map<String, byte[]> headerMap) {
        headerMap.remove(headerName);
    }
}
