/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2021 Elastic and contributors
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
package co.elastic.apm.agent.vertx.v3.webclient;

import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.vertx.AbstractVertxWebClientHelper;
import co.elastic.apm.agent.vertx.v3.Vertx3Instrumentation;
import io.vertx.core.Context;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.ext.web.client.impl.HttpContext;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import java.util.Arrays;
import java.util.Collection;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

public abstract class WebClientInstrumentation extends Vertx3Instrumentation {

    private final static AbstractVertxWebClientHelper webClientHelper = new AbstractVertxWebClientHelper() {
        @Override
        protected String getMethod(HttpClientRequest request) {
            return request.method().name();
        }
    };

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Arrays.asList("vertx", "vertx-webclient", "http-client", "experimental");
    }

    /**
     * Instruments TODO
     */
    public abstract static class HttpContextInstrumentation extends WebClientInstrumentation {

        @Override
        public ElementMatcher<? super TypeDescription> getTypeMatcher() {
            return named("io.vertx.ext.web.client.impl.HttpContext");
        }

    }

    /**
     * Instruments TODO
     */
    public static class HttpContextSendRequestInstrumentation extends HttpContextInstrumentation {

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("sendRequest").and(takesArgument(0, named("io.vertx.core.http.HttpClientRequest")));
        }

        @Override
        public String getAdviceClassName() {
            return "co.elastic.apm.agent.vertx.v3.webclient.WebClientInstrumentation$HttpContextSendRequestInstrumentation$HttpContextSendRequestAdvice";
        }

        public static class HttpContextSendRequestAdvice {


            @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
            public static void sendRequest(@Advice.This HttpContext<?> httpContext,
                                           @Advice.Argument(value = 0) HttpClientRequest request,
                                           @Advice.FieldValue(value = "context") Context vertxContext) {
                AbstractSpan<?> parent = tracer.getActive();

                if (null != parent) {
                    webClientHelper.startSpan(parent, httpContext, request);
                } else {
                    webClientHelper.followRedirect(httpContext, request);
                }
            }
        }
    }

    /**
     * Instruments TODO
     */
    public static class HttpContextReceiveResponseInstrumentation extends HttpContextInstrumentation {

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("receiveResponse").and(takesArgument(0, named("io.vertx.core.http.HttpClientResponse")));
        }

        @Override
        public String getAdviceClassName() {
            return "co.elastic.apm.agent.vertx.v3.webclient.WebClientInstrumentation$HttpContextReceiveResponseInstrumentation$HttpContextReceiveResponseAdvice";
        }

        public static class HttpContextReceiveResponseAdvice {


            @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
            public static void receiveResponse(@Advice.This HttpContext<?> httpContext,
                                               @Advice.Argument(value = 0) HttpClientResponse response) {
                webClientHelper.endSpan(httpContext, response);
            }
        }
    }

    /**
     * Instruments TODO
     */
    public static class HttpContextFailInstrumentation extends HttpContextInstrumentation {

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("fail").and(takesArgument(0, Throwable.class));
        }

        @Override
        public String getAdviceClassName() {
            return "co.elastic.apm.agent.vertx.v3.webclient.WebClientInstrumentation$HttpContextFailInstrumentation$HttpContextFailAdvice";
        }

        public static class HttpContextFailAdvice {


            @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
            public static void fail(@Advice.This HttpContext<?> httpContext, @Advice.Argument(value = 0) Throwable thrown) {
                webClientHelper.failSpan(httpContext, thrown);
            }
        }
    }

    /**
     * Instruments TODO
     */
    public static class HttpContextFollowRedirectInstrumentation extends HttpContextInstrumentation {

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("followRedirect");
        }

        @Override
        public String getAdviceClassName() {
            return "co.elastic.apm.agent.vertx.v3.webclient.WebClientInstrumentation$HttpContextFollowRedirectInstrumentation$HttpContextFollowRedirectAdvice";
        }

        public static class HttpContextFollowRedirectAdvice {


            @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
            public static void followRedirect(@Advice.This HttpContext<?> httpContext,
                                              @Advice.FieldValue(value = "clientRequest") HttpClientRequest request) {
                webClientHelper.followRedirect(httpContext, request);
            }
        }
    }
}
