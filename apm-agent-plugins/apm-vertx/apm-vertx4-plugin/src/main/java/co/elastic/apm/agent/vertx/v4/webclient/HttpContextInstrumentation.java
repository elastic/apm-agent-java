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
package co.elastic.apm.agent.vertx.v4.webclient;

import co.elastic.apm.agent.tracer.AbstractSpan;
import co.elastic.apm.agent.vertx.AbstractVertxWebClientHelper;
import co.elastic.apm.agent.vertx.AbstractVertxWebHelper;
import co.elastic.apm.agent.vertx.v4.Vertx4Instrumentation;
import io.vertx.core.Context;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.ext.web.client.HttpRequest;
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

public abstract class HttpContextInstrumentation extends Vertx4Instrumentation {

    protected static final String WEB_CLIENT_PARENT_SPAN_KEY = AbstractVertxWebClientHelper.class.getName() + ".parent";

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Arrays.asList("vertx", "vertx-webclient", "http-client", "experimental");
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("io.vertx.ext.web.client.impl.HttpContext");
    }

    public static class AdviceBase {
        protected final static AbstractVertxWebClientHelper webClientHelper = new AbstractVertxWebClientHelper() {
            @Override
            protected String getMethod(HttpClientRequest request) {
                return request.getMethod().name();
            }
        };
    }

    /**
     * Instruments {@link io.vertx.ext.web.client.impl.HttpContext#prepareRequest(HttpRequest, String, Object)}
     */
    public static class HttpContextPrepareRequestInstrumentation extends HttpContextInstrumentation {

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("prepareRequest");
        }

        @Override
        public String getAdviceClassName() {
            return "co.elastic.apm.agent.vertx.v4.webclient.HttpContextInstrumentation$HttpContextPrepareRequestInstrumentation$HttpContextPrepareRequestAdvice";
        }

        public static class HttpContextPrepareRequestAdvice extends AdviceBase {


            @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
            public static void prepareRequest(@Advice.This HttpContext<?> httpContext) {
                AbstractSpan<?> activeSpan = tracer.getActive();
                if (null != activeSpan) {
                    activeSpan.incrementReferences();
                    httpContext.set(WEB_CLIENT_PARENT_SPAN_KEY, activeSpan);
                }
            }
        }
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
            return "co.elastic.apm.agent.vertx.v4.webclient.HttpContextInstrumentation$HttpContextSendRequestInstrumentation$HttpContextSendRequestAdvice";
        }

        public static class HttpContextSendRequestAdvice extends AdviceBase {


            @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
            public static void sendRequest(@Advice.This HttpContext<?> httpContext, @Advice.Argument(value = 0) HttpClientRequest request, @Advice.FieldValue(value = "context") Context vertxContext) {
                Object parentSpan = httpContext.get(WEB_CLIENT_PARENT_SPAN_KEY);

                if (parentSpan != null) {
                    // Setting to null removes from the context attributes map
                    httpContext.set(WEB_CLIENT_PARENT_SPAN_KEY, null);
                    ((AbstractSpan<?>) parentSpan).decrementReferences();
                } else {
                    parentSpan = vertxContext.getLocal(AbstractVertxWebHelper.CONTEXT_TRANSACTION_KEY);
                }

                if (parentSpan != null) {
                    AbstractSpan<?> parent = (AbstractSpan<?>) parentSpan;
                    webClientHelper.startSpan(parent, httpContext, request);
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
            return "co.elastic.apm.agent.vertx.v4.webclient.HttpContextInstrumentation$HttpContextDispatchResponseInstrumentation$HttpContextDispatchResponseAdvice";
        }

        public static class HttpContextDispatchResponseAdvice extends AdviceBase {


            @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
            public static void receiveResponse(@Advice.This HttpContext<?> httpContext, @Advice.Argument(value = 0) HttpResponse<?> response) {
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
            return "co.elastic.apm.agent.vertx.v4.webclient.HttpContextInstrumentation$HttpContextFailInstrumentation$HttpContextFailAdvice";
        }

        public static class HttpContextFailAdvice extends AdviceBase {


            @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
            public static void fail(@Advice.This HttpContext<?> httpContext, @Advice.Argument(value = 0) Throwable thrown) {

                AbstractSpan<?> parent = null;
                Object parentFromContext = httpContext.get(WEB_CLIENT_PARENT_SPAN_KEY);
                if (parentFromContext != null) {
                    parent = (AbstractSpan<?>) parentFromContext;

                    // Setting to null removes from the context attributes map
                    httpContext.set(WEB_CLIENT_PARENT_SPAN_KEY, null);
                    parent.decrementReferences();
                }
                webClientHelper.failSpan(httpContext, thrown, parent);
            }
        }
    }
}
