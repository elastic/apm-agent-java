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
package co.elastic.apm.agent.springwebclient;

import co.elastic.apm.agent.tracer.dispatch.TextHeaderGetter;
import org.springframework.web.reactive.function.client.ClientRequest;

import javax.annotation.Nullable;
import java.util.List;

public class ClientRequestHeaderGetter implements TextHeaderGetter<ClientRequest> {

    public static final ClientRequestHeaderGetter INSTANCE = new ClientRequestHeaderGetter();

    @Nullable
    @Override
    public String getFirstHeader(String headerName, ClientRequest carrier) {
        return carrier.headers().getFirst(headerName);
    }

    @Override
    public <S> void forEach(String headerName, ClientRequest carrier, S state, HeaderConsumer<String, S> consumer) {
        List<String> headerValues = carrier.headers().get(headerName);
        if (headerValues == null) {
            return;
        }
        for (String value : headerValues) {
            consumer.accept(value, state);
        }
    }
}
