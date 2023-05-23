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
package co.elastic.apm.agent.urlconnection;

import co.elastic.apm.agent.bci.TracerAwareInstrumentation;
import co.elastic.apm.agent.collections.WeakConcurrentProviderImpl;
import co.elastic.apm.agent.httpclient.HttpClientHelper;
import co.elastic.apm.agent.tracer.AbstractSpan;
import co.elastic.apm.agent.tracer.Outcome;
import co.elastic.apm.agent.tracer.Span;
import co.elastic.apm.agent.sdk.state.CallDepth;
import co.elastic.apm.agent.sdk.state.GlobalState;
import co.elastic.apm.agent.sdk.weakconcurrent.WeakMap;
import co.elastic.apm.agent.tracer.dispatch.HeaderUtils;
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
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

@GlobalState
public abstract class HttpUrlConnectionInstrumentation extends TracerAwareInstrumentation {

    public static final WeakMap<HttpURLConnection, Span> inFlightSpans = WeakConcurrentProviderImpl.createWeakSpanMap();
    public static final CallDepth callDepth = CallDepth.get(HttpUrlConnectionInstrumentation.class);

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
        return hasSuperType(is(HttpURLConnection.class)).and(not(named("sun.net.www.protocol.https.HttpsURLConnectionImpl")));
    }

    public static class CreateSpanInstrumentation extends HttpUrlConnectionInstrumentation {

        public static class AdviceClass {
            @Nullable
            @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
            public static Object enter(@Advice.This HttpURLConnection thiz,
                                       @Advice.FieldValue("connected") boolean connected,
                                       @Advice.Origin String signature) {

                boolean isNestedCall = callDepth.isNestedCallAndIncrement();
                AbstractSpan<?> parent = tracer.getActive();
                if (parent == null) {
                    return null;
                }
                Span<?> span = inFlightSpans.get(thiz);
                if (span == null && !connected) {
                    final URL url = thiz.getURL();
                    span = HttpClientHelper.startHttpClientSpan(parent, thiz.getRequestMethod(), url.toString(), url.getProtocol(), url.getHost(), url.getPort());
                    if (!HeaderUtils.containsAny(tracer.getTraceHeaderNames(), thiz, UrlConnectionPropertyAccessor.instance())) {
                        if (span != null) {
                            span.propagateTraceContext(thiz, UrlConnectionPropertyAccessor.instance());
                        } else {
                            parent.propagateTraceContext(thiz, UrlConnectionPropertyAccessor.instance());
                        }
                    }
                }
                if (!isNestedCall && span != null) {
                    span.activate();
                    return span;
                }
                return null;
            }

            @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
            public static void exit(@Advice.This HttpURLConnection thiz,
                                    @Advice.Thrown @Nullable Throwable t,
                                    @Advice.FieldValue("responseCode") int responseCode,
                                    @Advice.Enter @Nullable Object spanObject,
                                    @Advice.Origin String signature) {

                callDepth.decrement();
                Span<?> span = (Span<?>) spanObject;
                if (span == null) {
                    return;
                }
                try {
                    if (responseCode != -1) {
                        inFlightSpans.remove(thiz);
                        // if the response code is set, the connection has been established via getOutputStream
                        // if the response code is unset even after getOutputStream has been called, there will be an exception
                        // checking if "finished" to avoid multiple endings on nested calls
                        if (!span.isFinished()) {
                            span.getContext().getHttp().withStatusCode(responseCode);
                            span.captureException(t).end();
                        }
                    } else if (t != null) {
                        inFlightSpans.remove(thiz);

                        // an exception here is synonym of failure, for example with circular redirects
                        // checking if "finished" to avoid multiple endings on nested calls
                        if (!span.isFinished()) {
                            span.captureException(t)
                                .withOutcome(Outcome.FAILURE)
                                .end();
                        }
                    } else {
                        // if connect or getOutputStream has been called we can't end the span right away
                        // we have to store associate it with thiz HttpURLConnection instance and end once getInputStream has been called
                        // note that this could happen on another thread
                        inFlightSpans.put(thiz, span);
                    }
                } finally {
                    span.deactivate();
                }
            }
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("connect").and(takesArguments(0))
                .or(named("getOutputStream").and(takesArguments(0)))
                .or(named("getInputStream").and(takesArguments(0)))
                .or(named("getResponseCode").and(takesArguments(0)));
        }
    }

    /**
     * Should users forget to call {@link HttpURLConnection#getInputStream()} but call {@link HttpURLConnection#disconnect()},
     * this makes sure to end the span.
     */
    public static class DisconnectInstrumentation extends HttpUrlConnectionInstrumentation {

        public static class AdviceClass {
            @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
            public static void afterDisconnect(@Advice.This HttpURLConnection thiz,
                                               @Advice.Thrown @Nullable Throwable t,
                                               @Advice.FieldValue("responseCode") int responseCode) {
                Span<?> span = inFlightSpans.remove(thiz);
                if (span != null) {
                    span.captureException(t)
                        .withOutcome(t != null ? Outcome.FAILURE : Outcome.SUCCESS)
                        .end();
                }
            }
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("disconnect").and(takesArguments(0));
        }

    }

}
