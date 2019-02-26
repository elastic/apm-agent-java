/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2019 Elastic and contributors
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package co.elastic.apm.agent.http.client;

import co.elastic.apm.agent.bci.VisibleForAdvice;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.TraceContextHolder;

import javax.annotation.Nullable;
import java.net.URI;

public class HttpClientHelper {
    private static final String EXTERNAL_TYPE = "external";
    private static final String HTTP_SUBTYPE = "http";

    @Nullable
    @VisibleForAdvice
    public static Span startHttpClientSpan(TraceContextHolder<?> parent, String method, @Nullable URI uri, String hostName) {
        return startHttpClientSpan(parent, method, uri != null ? uri.toString() : null, hostName);
    }

    @Nullable
    @VisibleForAdvice
    public static Span startHttpClientSpan(TraceContextHolder<?> parent, String method, @Nullable String uri, String hostName) {
        Span span = null;
        if (!isAlreadyMonitored(parent)) {
            span = parent
                .createSpan()
                .withType(EXTERNAL_TYPE)
                .withSubtype(HTTP_SUBTYPE)
                .appendToName(method).appendToName(" ").appendToName(hostName);

            if (uri != null) {
                span.getContext().getHttp().withUrl(uri);
            }
        }
        return span;
    }

    /*
     * typically, more than one ClientExecChain implementation is invoked during an HTTP request
     */
    private static boolean isAlreadyMonitored(TraceContextHolder<?> parent) {
        if (!(parent instanceof Span)) {
            return false;
        }
        Span parentSpan = (Span) parent;
        // a http client span can't be the child of another http client span
        // this means the span has already been created for this db call
        return parentSpan.getType() != null && parentSpan.getType().equals(EXTERNAL_TYPE) &&
            parentSpan.getSubtype() != null && parentSpan.getSubtype().equals(HTTP_SUBTYPE);
    }
}
