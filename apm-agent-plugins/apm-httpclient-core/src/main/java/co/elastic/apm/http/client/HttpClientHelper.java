/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 Elastic and contributors
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
package co.elastic.apm.http.client;

import co.elastic.apm.bci.VisibleForAdvice;
import co.elastic.apm.impl.transaction.AbstractSpan;
import co.elastic.apm.impl.transaction.Span;

import javax.annotation.Nullable;

public class HttpClientHelper {

    public static final String HTTP_CLIENT_SPAN_TYPE_PREFIX = "ext.http.";

    @Nullable
    @VisibleForAdvice
    public static Span startHttpClientSpan(AbstractSpan<?> parent, String method, String url, String hostName, String spanType) {
        Span span = null;
        if (!isAlreadyMonitored(parent)) {
            span = parent
                .createSpan()
                .withType(spanType)
                .appendToName(method).appendToName(" ").appendToName(hostName)
                .activate();
            span.getContext().getHttp()
                .withUrl(url);
        }
        return span;
    }

    /*
     * typically, more than one ClientExecChain implementation is invoked during an HTTP request
     */
    private static boolean isAlreadyMonitored(AbstractSpan<?> parent) {
        if (!(parent instanceof Span)) {
            return false;
        }
        Span parentSpan = (Span) parent;
        // a http client span can't be the child of another http client span
        // this means the span has already been created for this db call
        return parentSpan.getType() != null && parentSpan.getType().startsWith(HTTP_CLIENT_SPAN_TYPE_PREFIX);
    }
}
