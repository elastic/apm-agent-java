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

import co.elastic.apm.agent.tracer.Span;
import net.bytebuddy.asm.Advice;

import javax.annotation.Nullable;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class HttpClientAdvice {
    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Object onBeforeExecute(@Advice.Argument(value = 0) HttpRequest httpRequest) {
        return HttpClientAdviceHelper.startSpan(httpRequest);
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
    public static void onAfterExecute(@Advice.Return @Nullable HttpResponse<?> response,
                                      @Advice.Enter @Nullable Object spanObj,
                                      @Advice.Thrown @Nullable Throwable t) {
        if (spanObj instanceof Span<?>) {
            final Span<?> span = (Span<?>) spanObj;
            if (response != null) {
                int statusCode = response.statusCode();
                span.getContext().getHttp().withStatusCode(statusCode);
            }
            span.captureException(t)
                .deactivate()
                .end();
        }
    }
}
