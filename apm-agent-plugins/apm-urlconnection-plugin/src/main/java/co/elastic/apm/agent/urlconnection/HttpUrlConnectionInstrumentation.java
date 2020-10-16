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

import co.elastic.apm.agent.bci.TracerAwareInstrumentation;
import co.elastic.apm.agent.http.client.HttpClientHelper;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.TraceContext;
import co.elastic.apm.agent.sdk.weakmap.WeakMapSupplier;
import com.blogspot.mydailyjava.weaklockfree.WeakConcurrentMap;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.is;
import static net.bytebuddy.matcher.ElementMatchers.nameContains;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

public abstract class HttpUrlConnectionInstrumentation extends TracerAwareInstrumentation {

    private static final WeakConcurrentMap<HttpURLConnection, Span> inFlightSpans = WeakMapSupplier.createMap();

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

        private static final Logger logger = LoggerFactory.getLogger(CreateSpanInstrumentation.class);

        @Nullable
        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static Object enter(@Advice.This HttpURLConnection thiz,
                                   @Advice.FieldValue("connected") boolean connected,
                                   @Advice.Origin String signature) {

            logger.debug("Enter advice signature = {} connected = {}", signature, connected);

            AbstractSpan<?> parent = tracer.getActive();
            if (parent == null) {
                logger.debug("Enter advice without parent for method {}#execute() {} {}", thiz.getClass().getName(), thiz.getRequestMethod(), thiz.getURL());
                return null;
            }
            Span span = inFlightSpans.get(thiz);
            if (span == null && !connected) {
                final URL url = thiz.getURL();
                span = HttpClientHelper.startHttpClientSpan(parent, thiz.getRequestMethod(), url.toString(), url.getProtocol(), url.getHost(), url.getPort());
                if (span != null) {
                    if (!TraceContext.containsTraceContextTextHeaders(thiz, UrlConnectionPropertyAccessor.instance())) {
                        span.propagateTraceContext(thiz, UrlConnectionPropertyAccessor.instance());
                    }
                }
            }
            if (span != null) {
                span.activate();
            }
            return span;
        }

        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
        public static void exit(@Advice.This HttpURLConnection thiz,
                                @Nullable @Advice.Thrown Throwable t,
                                @Advice.FieldValue("responseCode") int responseCode, // can we still do that without inlining ?
                                @Nullable @Advice.Enter Object spanObject,
                                @Advice.Origin String signature) {

            logger.debug("Exit advice signature = {} responseCode = {} span = {}", signature, responseCode, spanObject);

            Span span = (Span) spanObject;
            if (span == null) {
                return;
            }
            span.deactivate();

            // if we don't get the proper response code ?
            if (responseCode != -1) {
                logger.debug("response code != -1");
                inFlightSpans.remove(thiz);
                // if the response code is set, the connection has been established via getOutputStream
                // if the response code is unset even after getOutputStream has been called, there will be an exception
                span.getContext().getHttp().withStatusCode(responseCode);
                span.captureException(t).end();
            } else if (t != null) {
                logger.debug("thrown exception : {}", t.getMessage());
                inFlightSpans.remove(thiz);
                span.captureException(t).end();
            } else {
                logger.debug("keep in-flight");
                // if connect or getOutputStream has been called we can't end the span right away
                // we have to store associate it with thiz HttpURLConnection instance and end once getInputStream has been called
                // note that this could happen on another thread
                inFlightSpans.put(thiz, span);
            }

            if (logger.isDebugEnabled()) {
                logger.debug("in-flight spans count = {}", inFlightSpans.approximateSize());
                for (Map.Entry<HttpURLConnection, Span> entry : inFlightSpans) {
                    logger.debug("in-flight span = {}", entry.getValue());
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

        private static final Logger logger = LoggerFactory.getLogger(CreateSpanInstrumentation.class);

        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
        public static void afterDisconnect(@Advice.This HttpURLConnection thiz,
                                           @Nullable @Advice.Thrown Throwable t,
                                           @Advice.FieldValue("responseCode") int responseCode,
                                           @Advice.Origin String signature) {

            logger.debug("Exit advice signature = {} responseCode = {}", signature, responseCode);

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
