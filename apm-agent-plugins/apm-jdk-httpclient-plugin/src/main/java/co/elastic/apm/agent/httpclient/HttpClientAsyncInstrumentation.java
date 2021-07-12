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

import co.elastic.apm.agent.impl.transaction.Span;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.nameContains;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

public class HttpClientAsyncInstrumentation extends AbstractHttpClientInstrumentation {

    @Override
    public ElementMatcher<? super NamedElement> getTypeMatcherPreFilter() {
        return nameContains("HttpClient");
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return hasSuperType(named("java.net.http.HttpClient"));
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("sendAsync")
            .and(returns(hasSuperType(named("java.util.concurrent.CompletableFuture"))))
            .and(takesArguments(3));
    }

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Object onBeforeExecute(@Advice.Argument(value = 0) HttpRequest httpRequest) {
        return startSpan(httpRequest);
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
    public static void onAfterExecute(@Advice.Return @Nullable CompletableFuture<HttpResponse<?>> completableFuture,
                                      @Advice.Enter @Nullable Object spanObj,
                                      @Advice.Thrown @Nullable Throwable t) {
        if (spanObj instanceof Span) {
            final Span span = (Span) spanObj;
            span.deactivate();
            if (completableFuture == null) {
                span.captureException(t)
                    .end();

                return;
            }

            completableFuture.whenComplete((response, throwable) -> {
                if (response != null) {
                    int statusCode = response.statusCode();
                    span.getContext().getHttp().withStatusCode(statusCode);
                }
                span.captureException(throwable)
                    .end();
            });
        }
    }
}
