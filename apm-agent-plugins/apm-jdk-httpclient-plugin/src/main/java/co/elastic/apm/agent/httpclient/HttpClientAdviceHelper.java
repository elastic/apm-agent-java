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
package co.elastic.apm.agent.httpclient;

import co.elastic.apm.agent.impl.GlobalTracer;
import co.elastic.apm.agent.impl.Tracer;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Span;

import javax.annotation.Nullable;
import java.net.URI;
import java.net.http.HttpRequest;

public class HttpClientAdviceHelper {

    private static final Tracer tracer = GlobalTracer.get();

    @Nullable
    public static Span startSpan(HttpRequest httpRequest) {
        final AbstractSpan<?> parent = tracer.getActive();
        if (parent == null) {
            return null;
        }

        URI uri = httpRequest.uri();
        Span span = HttpClientHelper.startHttpClientSpan(parent, httpRequest.method(), uri, uri.getHost());
        if (span != null) {
            span.activate();
        }
        return span;
    }
}
