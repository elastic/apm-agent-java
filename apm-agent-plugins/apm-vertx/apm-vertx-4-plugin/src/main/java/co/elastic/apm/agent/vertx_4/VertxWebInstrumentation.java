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
package co.elastic.apm.agent.vertx_4;

import co.elastic.apm.agent.bci.TracerAwareInstrumentation;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.sdk.advice.AssignTo;
import co.elastic.apm.agent.vertx_4.tracer.NoopVertxTracer;
import io.vertx.core.Context;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.spi.tracing.VertxTracer;
import io.vertx.ext.web.RoutingContext;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collection;

import static co.elastic.apm.agent.bci.bytebuddy.CustomElementMatchers.classLoaderCanLoadClass;
import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesNoArguments;

@SuppressWarnings("JavadocReference")
public abstract class VertxWebInstrumentation extends TracerAwareInstrumentation {

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Arrays.asList("vertx", "vertx-web");
    }

    @Override
    public ElementMatcher.Junction<ClassLoader> getClassLoaderMatcher() {
        // ensure only Vertx versions >= 4.0 are instrumented
        return classLoaderCanLoadClass("io.vertx.core.http.impl.Http1xServerRequest");
    }

    /**
     * Instruments {@link io.vertx.core.impl.ContextImpl#tracer}} to return a noop tracer in case no tracer has been specified.
     */
    public static class ContextImplTracerInstrumentation extends VertxWebInstrumentation {
        @Override
        public ElementMatcher<? super TypeDescription> getTypeMatcher() {
            return named("io.vertx.core.impl.ContextImpl").and(not(isInterface()));
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("tracer").and(takesNoArguments());
        }

        @Override
        public String getAdviceClassName() {
            return "co.elastic.apm.agent.vertx_4.VertxWebInstrumentation$ContextImplTracerInstrumentation$ContextImplTracerAdvice";
        }

        public static class ContextImplTracerAdvice {

            @AssignTo.Return
            @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
            public static VertxTracer<?, ?> receiveRequest(@Advice.Return @Nullable VertxTracer<?, ?> currentTracer) {
                if (currentTracer == null) {
                    return NoopVertxTracer.INSTANCE;
                } else {
                    return currentTracer;
                }
            }
        }

    }

    /**
     * Instruments {@link io.vertx.core.spi.tracing.VertxTracer#receiveRequest}} to intercept tracer calls.
     */
    public static class VertxTracerReceiveRequestInstrumentation extends VertxWebInstrumentation {
        @Override
        public ElementMatcher<? super TypeDescription> getTypeMatcher() {
            return hasSuperType(named("io.vertx.core.spi.tracing.VertxTracer")).and(not(isInterface()));
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("receiveRequest").and(takesArgument(0, named("io.vertx.core.Context")));
        }

        @Override
        public String getAdviceClassName() {
            return "co.elastic.apm.agent.vertx_4.VertxWebInstrumentation$VertxTracerReceiveRequestInstrumentation$TracerReceiveRequestAdvice";
        }

        public static class TracerReceiveRequestAdvice {

            @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
            public static void receiveRequest(@Advice.Argument(value = 0) Context context, @Advice.Argument(value = 3) Object request) {
                if (request instanceof HttpServerRequest) {
                    HttpServerRequest httpServerRequest = (HttpServerRequest) request;
                    VertxWebHelper.getInstance().startOrGetTransaction(context, httpServerRequest);
                }
            }
        }
    }

    /**
     * Instruments {@link io.vertx.core.spi.tracing.VertxTracer#sendResponse}} to intercept tracer calls.
     */
    public static class VertxTracerSendResponseInstrumentation extends VertxWebInstrumentation {
        @Override
        public ElementMatcher<? super TypeDescription> getTypeMatcher() {
            return hasSuperType(named("io.vertx.core.spi.tracing.VertxTracer")).and(not(isInterface()));
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("sendResponse").and(takesArgument(0, named("io.vertx.core.Context")));
        }

        @Override
        public String getAdviceClassName() {
            return "co.elastic.apm.agent.vertx_4.VertxWebInstrumentation$VertxTracerSendResponseInstrumentation$TracerSendResponseAdvice";
        }

        public static class TracerSendResponseAdvice {

            @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
            public static void receiveRequest(@Advice.Argument(value = 0) Context context, @Advice.Argument(value = 1) Object response,
                                              @Nullable @Advice.Argument(value = 3) Throwable failure) {

                Object transactionObj = context.getLocal(VertxWebHelper.CONTEXT_TRANSACTION_KEY);
                if (transactionObj instanceof Transaction) {
                    Transaction transaction = (Transaction) transactionObj;
                    if (failure != null) {
                        transaction.captureException(failure);
                    }
                    if (response instanceof HttpServerResponse) {
                        VertxWebHelper.getInstance().finalizeTransaction((HttpServerResponse) response, transaction);
                    } else {
                        transaction.end();
                    }
                }
            }
        }
    }

    /**
     * Instruments {@link io.vertx.ext.web.Route#handler} to wrap router executions for better transaction naming based on routing information.
     */
    public static class RouteInstrumentation extends VertxWebInstrumentation {
        @Override
        public ElementMatcher<? super TypeDescription> getTypeMatcher() {
            return not(isInterface()).and(named("io.vertx.ext.web.impl.RouteState"));
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("handleContext").and(takesArgument(0, named("io.vertx.ext.web.impl.RoutingContextImplBase")));
        }

        @Override
        public String getAdviceClassName() {
            return "co.elastic.apm.agent.vertx_4.VertxWebInstrumentation$RouteInstrumentation$RouteImplAdvice";
        }

        public static class RouteImplAdvice {

            @Nullable
            @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
            public static Object nextEnter(@Advice.Argument(value = 0) RoutingContext routingContext) {
                Transaction transaction = VertxWebHelper.getInstance().setRouteBasedNameForCurrentTransaction(routingContext);

                if (transaction != null) {
                    transaction.activate();
                }

                return transaction;
            }

            @Advice.OnMethodExit(suppress = Throwable.class, inline = false, onThrowable = Throwable.class)
            public static void nextExit(@Advice.Argument(value = 0) RoutingContext routingContext,
                                        @Nullable @Advice.Enter Object transactionObj, @Nullable @Advice.Thrown Throwable thrown) {
                if (transactionObj instanceof Transaction) {
                    Transaction transaction = (Transaction) transactionObj;
                    transaction.captureException(thrown).deactivate();
                }
            }
        }


    }

    /**
     * Instruments
     * <ul>
     *     <li>{@link io.vertx.core.http.impl.Http1xServerRequest#onData}</li>
     *     <li>{@link io.vertx.core.http.impl.Http2ServerRequestImpl#handleData}</li>
     * </ul>
     * <p>
     * to enable request body capturing.
     */
    public static class RequestBufferInstrumentation extends VertxWebInstrumentation {
        @Override
        public ElementMatcher<? super TypeDescription> getTypeMatcher() {
            return not(isInterface()).and(hasSuperType(named("io.vertx.core.http.HttpServerRequest")));
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return namedOneOf("onData", "handleData").and(takesArgument(0, named("io.vertx.core.buffer.Buffer")));
        }

        @Override
        public String getAdviceClassName() {
            return "co.elastic.apm.agent.vertx_4.VertxWebInstrumentation$RequestBufferInstrumentation$HandleDataAdvice";
        }

        public static class HandleDataAdvice {

            @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
            public static void wrapHandler(@Advice.Argument(value = 0) Buffer requestDataBuffer, @Advice.FieldValue(value = "context") Context context) {
                Transaction transaction = context.getLocal(VertxWebHelper.CONTEXT_TRANSACTION_KEY);
                VertxWebHelper.getInstance().captureBody(transaction, requestDataBuffer);
            }
        }


    }

}
