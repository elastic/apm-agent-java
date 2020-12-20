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
package co.elastic.apm.agent.servlet.helper;

import co.elastic.apm.agent.impl.transaction.TextHeaderGetter;
import jakarta.servlet.http.HttpServletRequest;

import javax.annotation.Nullable;
import java.util.Enumeration;

class JakartaEEServletRequestHeaderGetter implements TextHeaderGetter<HttpServletRequest> {

    private static final JakartaEEServletRequestHeaderGetter INSTANCE = new JakartaEEServletRequestHeaderGetter();

    static JakartaEEServletRequestHeaderGetter getInstance() {
        return INSTANCE;
    }

    @Nullable
    @Override
    public String getFirstHeader(String headerName, HttpServletRequest request) {
        return request.getHeader(headerName);
    }

    @Override
    public <S> void forEach(String headerName, HttpServletRequest carrier, S state, HeaderConsumer<String, S> consumer) {
        Enumeration<String> headers = carrier.getHeaders(headerName);
        while (headers.hasMoreElements()) {
            consumer.accept(headers.nextElement(), state);
        }
    }

}
