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
package co.elastic.apm.agent.urlconnection;

import co.elastic.apm.agent.bci.ElasticApmInstrumentation;
import co.elastic.apm.agent.bci.VisibleForAdvice;
import co.elastic.apm.agent.http.client.HttpClientHelper;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.TraceContext;
import co.elastic.apm.agent.util.DataStructures;
import com.blogspot.mydailyjava.weaklockfree.WeakConcurrentMap;
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

    @VisibleForAdvice
    public static final WeakConcurrentMap<HttpURLConnection, Span> inFlightSpans = DataStructures.createWeakConcurrentMapWithCleanerThread();

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
                                 @Advice.FieldValue("connected") boolean connected,
                                 @Advice.Local("span") Span span,
                                 @Advice.Origin String signature) {
            if (tracer == null || tracer.getActive() == null) {
                return;
            }
            span = inFlightSpans.get(thiz);
            if (span == null && !connected) {
                final URL url = thiz.getURL();
                span = HttpClientHelper.startHttpClientSpan(tracer.getActive(), thiz.getRequestMethod(), url.toString(), url.getProtocol(), url.getHost(), url.getPort());
                if (span != null) {
                    if (!TraceContext.containsTraceContextTextHeaders(thiz, UrlConnectionPropertyAccessor.instance())) {
                        span.propagateTraceContext(thiz, UrlConnectionPropertyAccessor.instance());
                    }
                }
            }
            if (span != null) {
                span.activate();
            }
        }

        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
        public static void exit(@Advice.This HttpURLConnection thiz,
                                @Nullable @Advice.Thrown Throwable t,
                                @Advice.FieldValue("responseCode") int responseCode,
                                @Nullable @Advice.Local("span") Span span,
                                @Advice.Origin String signature) {
            if (span == null) {
                return;
            }
            span.deactivate();
            if (responseCode != -1) {
                inFlightSpans.remove(thiz);
                // if the response code is set, the connection has been established via getOutputStream
                // if the response code is unset even after getOutputStream has been called, there will be an exception
                span.getContext().getHttp().withStatusCode(responseCode);
                span.captureException(t).end();
            } else if (t != null) {
                inFlightSpans.remove(thiz);
                span.captureException(t).end();
            } else {
                // if connect or getOutputStream has been called we can't end the span right away
                // we have to store associate it with thiz HttpURLConnection instance and end once getInputStream has been called
                // note that this could happen on another thread
                inFlightSpans.put(thiz, span);
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
            Span span = inFlightSpans.remove(thiz);
            if (span != null) {
                span.captureException(t).end();
            }
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("disconnect").and(takesArguments(0));
        }

    }

}
