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

import co.elastic.apm.agent.tracer.AbstractSpan;
import co.elastic.apm.agent.tracer.GlobalTracer;
import co.elastic.apm.agent.tracer.Span;
import co.elastic.apm.agent.tracer.Tracer;
import net.bytebuddy.asm.Advice;

import javax.annotation.Nullable;
import java.net.http.HttpHeaders;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class HttpRequestHeadersAdvice {

    private static final Tracer tracer = GlobalTracer.get();

    @Nullable
    @Advice.AssignReturned.ToReturned
    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
    public static HttpHeaders onAfterExecute(@Advice.Return @Nullable final HttpHeaders httpHeaders) {
        AbstractSpan<?> active = tracer.getActive();
        if (!(active instanceof Span<?>) || httpHeaders == null) { // in case of thrown exception return value might be null
            return httpHeaders;
        }
        Map<String, List<String>> headersMap = new LinkedHashMap<>(httpHeaders.map());
        active.propagateTraceContext(headersMap, HttpClientRequestPropertyAccessor.instance());
        return HttpHeaders.of(headersMap, (x, y) -> true);
    }
}
