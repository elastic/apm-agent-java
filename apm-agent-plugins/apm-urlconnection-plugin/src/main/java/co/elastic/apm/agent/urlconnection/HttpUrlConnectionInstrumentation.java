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
package co.elastic.apm.agent.urlconnection;

import co.elastic.apm.agent.bci.ElasticApmInstrumentation;
import co.elastic.apm.agent.http.client.HttpClientHelper;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.TraceContext;
import co.elastic.apm.agent.impl.transaction.TraceContextHolder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.Collection;

import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.is;
import static net.bytebuddy.matcher.ElementMatchers.nameContains;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

public abstract class HttpUrlConnectionInstrumentation extends ElasticApmInstrumentation {

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Arrays.asList("http-client", "urlconnection");
    }

    @Override
    public ElementMatcher<? super NamedElement> getTypeMatcherPreFilter() {
        return nameContains("URLConnection").or(nameContains("UrlConnection"));
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return hasSuperType(is(HttpURLConnection.class));
    }

    public static class CreateSpanInstrumentation extends HttpUrlConnectionInstrumentation {

        @Advice.OnMethodEnter(suppress = Throwable.class)
        public static void enter(@Advice.This HttpURLConnection thiz,
                                 @Advice.FieldValue("connected") boolean connected) {
            if (tracer == null || tracer.getActive() == null || connected) {
                return;
            }
            final URL url = thiz.getURL();
            final Span span = HttpClientHelper.startHttpClientSpan(tracer.getActive(), thiz.getRequestMethod(), url.toString(), url.getHost());
            if (span != null) {
                span.setOriginator(thiz);
                if (thiz.getRequestProperty(TraceContext.TRACE_PARENT_HEADER) == null) {
                    thiz.addRequestProperty(TraceContext.TRACE_PARENT_HEADER, span.getTraceContext().getOutgoingTraceParentHeader().toString());
                }
            }
        }

        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
        public static void exit(@Advice.This HttpURLConnection thiz,
                                @Nullable @Advice.Thrown Throwable t,
                                @Advice.FieldValue("responseCode") int responseCode) {
            if (tracer == null) {
                return;
            }
            // we can't use local variables as a span might be started in HttpUrlConnection#connect
            // but it is always ended in HttpUrlConnection#getInputStream
            final TraceContextHolder<?> active = tracer.getActive();
            if (active instanceof Span) {
                final Span span = (Span) active;
                // makes sure this is actually the span for this HttpUrlConnection and not some random parent span
                if (span.isOriginatedBy(thiz)) {
                    if (responseCode != -1) {
                        // if the response code is set, the connection has been established via getOutputStream
                        // if the response code is unset even after getOutputStream has been called, there will be an exception
                        span.getContext().getHttp().withStatusCode(responseCode);
                        span.captureException(t).deactivate().end();
                    } else if (t != null) {
                        span.captureException(t).deactivate().end();
                    }
                }
            }
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("connect").and(takesArguments(0))
                .or(named("getOutputStream").and(takesArguments(0)))
                .or(named("getInputStream").and(takesArguments(0)));
        }
    }

    /**
     * Should users forget to call {@link HttpURLConnection#getInputStream()} but call {@link HttpURLConnection#disconnect()},
     * this makes sure to end the span.
     */
    public static class DisconnectInstrumentation extends HttpUrlConnectionInstrumentation {

        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
        public static void afterDisconnect(@Advice.This HttpURLConnection thiz,
                                           @Nullable @Advice.Thrown Throwable t,
                                           @Advice.FieldValue("responseCode") int responseCode) {
            if (tracer == null) {
                return;
            }
            final TraceContextHolder<?> active = tracer.getActive();
            if (active instanceof Span) {
                final Span span = (Span) active;
                if (span.isOriginatedBy(thiz)) {
                    span.captureException(t).deactivate().end();
                }
            }
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("disconnect").and(takesArguments(0));
        }

    }

}
