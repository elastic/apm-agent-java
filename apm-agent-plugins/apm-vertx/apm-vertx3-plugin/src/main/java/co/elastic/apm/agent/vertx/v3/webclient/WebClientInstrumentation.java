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
package co.elastic.apm.agent.vertx.v3.webclient;

import co.elastic.apm.agent.tracer.AbstractSpan;
import co.elastic.apm.agent.vertx.AbstractVertxWebClientHelper;
import co.elastic.apm.agent.vertx.v3.Vertx3Instrumentation;
import io.vertx.core.Context;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.ext.web.client.HttpResponse;
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

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Arrays.asList("vertx", "vertx-webclient", "http-client", "experimental");
    }

    public abstract static class HttpContextInstrumentation extends WebClientInstrumentation {

        @Override
        public ElementMatcher<? super TypeDescription> getTypeMatcher() {
            return named("io.vertx.ext.web.client.impl.HttpContext");
        }

    }

    public static class AdviceBase {
        protected final static AbstractVertxWebClientHelper webClientHelper = new AbstractVertxWebClientHelper() {
            @Override
            protected String getMethod(HttpClientRequest request) {
                return request.method().name();
            }
        };
    }

    /**
     * Instruments {@link io.vertx.ext.web.client.impl.HttpContext#sendRequest(HttpClientRequest)}
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

        public static class HttpContextSendRequestAdvice extends AdviceBase {


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
     * Instruments {@link io.vertx.ext.web.client.impl.HttpContext#dispatchResponse(HttpResponse)}
     */
    public static class HttpContextDispatchResponseInstrumentation extends HttpContextInstrumentation {

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("dispatchResponse").and(takesArgument(0, named("io.vertx.ext.web.client.HttpResponse")));
        }

        @Override
        public String getAdviceClassName() {
            return "co.elastic.apm.agent.vertx.v3.webclient.WebClientInstrumentation$HttpContextDispatchResponseInstrumentation$HttpContextDispatchResponseAdvice";
        }

        public static class HttpContextDispatchResponseAdvice extends AdviceBase {


            @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
            public static void receiveResponse(@Advice.This HttpContext<?> httpContext,
                                               @Advice.Argument(value = 0) HttpResponse<?> response) {
                webClientHelper.endSpan(httpContext, response);
            }
        }
    }

    /**
     * Instruments {@link io.vertx.ext.web.client.impl.HttpContext#fail(Throwable)}
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

        public static class HttpContextFailAdvice extends AdviceBase {


            @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
            public static void fail(@Advice.This HttpContext<?> httpContext, @Advice.Argument(value = 0) Throwable thrown) {
                webClientHelper.failSpan(httpContext, thrown, null);
            }
        }
    }

    /**
     * Instruments {@code io.vertx.ext.web.client.impl.HttpContext#followRedirect()}, which is available only at late
     * 3.x versions.
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

        public static class HttpContextFollowRedirectAdvice extends AdviceBase {


            @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
            public static void followRedirect(@Advice.This HttpContext<?> httpContext,
                                              @Advice.FieldValue(value = "clientRequest") HttpClientRequest request) {
                webClientHelper.followRedirect(httpContext, request);
            }
        }
    }
}
