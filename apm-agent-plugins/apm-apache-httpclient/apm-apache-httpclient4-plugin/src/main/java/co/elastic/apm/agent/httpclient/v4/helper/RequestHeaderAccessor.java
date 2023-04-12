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
package co.elastic.apm.agent.httpclient.v4.helper;

import co.elastic.apm.agent.tracer.dispatch.TextHeaderGetter;
import co.elastic.apm.agent.tracer.dispatch.TextHeaderSetter;
import org.apache.http.Header;
import org.apache.http.HttpRequest;

import javax.annotation.Nullable;

@SuppressWarnings("unused")
public class RequestHeaderAccessor implements TextHeaderGetter<HttpRequest>, TextHeaderSetter<HttpRequest> {

    public static final RequestHeaderAccessor INSTANCE = new RequestHeaderAccessor();

    @Override
    public void setHeader(String headerName, String headerValue, HttpRequest request) {
        request.setHeader(headerName, headerValue);
    }

    @Nullable
    @Override
    public String getFirstHeader(String headerName, HttpRequest request) {
        Header header = request.getFirstHeader(headerName);
        if (header != null) {
            return header.getValue();
        }
        return null;
    }

    @Override
    public <S> void forEach(String headerName, HttpRequest carrier, S state, HeaderConsumer<String, S> consumer) {
        Header[] headers = carrier.getHeaders(headerName);
        if (headers != null) {
            for (Header header : headers) {
                consumer.accept(header.getValue(), state);
            }
        }
    }
}
