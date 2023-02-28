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
package co.elastic.apm.agent.finaglehttpclient.helper;

import co.elastic.apm.agent.tracer.dispatch.TextHeaderGetter;
import co.elastic.apm.agent.tracer.dispatch.TextHeaderSetter;
import com.twitter.finagle.http.Request;
import scala.Option;
import scala.collection.Iterator;
import scala.collection.immutable.Seq;

import javax.annotation.Nullable;

@SuppressWarnings("unused")
public class RequestHeaderAccessor implements TextHeaderGetter<Request>, TextHeaderSetter<Request> {

    public static final RequestHeaderAccessor INSTANCE = new RequestHeaderAccessor();

    @Override
    public void setHeader(String headerName, String headerValue, Request request) {
        request.headerMap().set(headerName, headerValue);
    }

    @Nullable
    @Override
    public String getFirstHeader(String headerName, Request request) {
        Option<String> headerValue = request.headerMap().get(headerName);
        if (headerValue.nonEmpty()) {
            return headerValue.get();
        }
        return null;
    }

    @Override
    public <S> void forEach(String headerName, Request carrier, S state, HeaderConsumer<String, S> consumer) {
        Seq<String> headers = carrier.headerMap().getAll(headerName);
        Iterator<String> headerIterator = headers.iterator();
        while (headerIterator.hasNext()) {
            consumer.accept(headerIterator.next(), state);
        }
    }
}
