/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2019 Elastic and contributors
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
package co.elastic.apm.agent.http.client;

import co.elastic.apm.agent.bci.VisibleForAdvice;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.TraceContextHolder;

import javax.annotation.Nullable;
import java.net.URI;

public class HttpClientHelper {

    public static final String EXTERNAL_TYPE = "external";
    public static final String HTTP_SUBTYPE = "http";

    @Nullable
    @VisibleForAdvice
    public static Span startHttpClientSpan(TraceContextHolder<?> parent, String method, @Nullable URI uri, String hostName) {
        return startHttpClientSpan(parent, method, uri != null ? uri.toString() : null, hostName);
    }

    @Nullable
    @VisibleForAdvice
    public static Span startHttpClientSpan(TraceContextHolder<?> parent, String method, @Nullable String uri, String hostName) {
        Span span = parent.createExitSpan();
        if (span != null) {
            span.withType(EXTERNAL_TYPE)
                .withSubtype(HTTP_SUBTYPE)
                .appendToName(method).appendToName(" ").appendToName(hostName);

            if (uri != null) {
                span.getContext().getHttp().withUrl(uri);
            }
        }
        return span;
    }
}
