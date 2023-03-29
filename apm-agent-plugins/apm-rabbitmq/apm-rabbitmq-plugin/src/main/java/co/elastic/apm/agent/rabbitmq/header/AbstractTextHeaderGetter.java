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
package co.elastic.apm.agent.rabbitmq.header;

import co.elastic.apm.agent.tracer.dispatch.TextHeaderGetter;

import javax.annotation.Nullable;
import java.util.Map;

public abstract class AbstractTextHeaderGetter<T> implements TextHeaderGetter<T> {

    @Nullable
    @Override
    public String getFirstHeader(String headerName, T carrier) {
        Map<String, Object> headers = getHeaders(carrier);
        if (headers == null || headers.isEmpty()) {
            return null;
        }
        Object headerValue = headers.get(headerName);
        if (headerValue != null) {
            // com.rabbitmq.client.impl.LongStringHelper.ByteArrayLongString
            return headerValue.toString();
        }
        return null;
    }

    @Override
    public <S> void forEach(String headerName, T carrier, S state, HeaderConsumer<String, S> consumer) {
        String header = getFirstHeader(headerName, carrier);
        if (header != null) {
            consumer.accept(header, state);
        }
    }

    @Nullable
    protected abstract Map<String, Object> getHeaders(T carrier);

}
