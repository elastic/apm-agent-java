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
package co.elastic.apm.agent.javalin;

import co.elastic.apm.agent.bci.TracerAwareInstrumentation;
import co.elastic.apm.agent.impl.GlobalTracer;
import co.elastic.apm.agent.impl.context.web.WebConfiguration;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.util.TransactionNameUtils;
import co.elastic.apm.agent.util.VersionUtils;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.javalin.http.HandlerType;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;

import javax.annotation.Nullable;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;

import static co.elastic.apm.agent.bci.bytebuddy.CustomElementMatchers.classLoaderCanLoadClass;
import static co.elastic.apm.agent.impl.transaction.AbstractSpan.PRIO_HIGH_LEVEL_FRAMEWORK;
import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

public class JavalinInstrumentation extends TracerAwareInstrumentation {

    private static final Logger logger = LoggerFactory.getLogger(JavalinInstrumentation.class);

    private static final String FRAMEWORK_NAME = "Javalin";

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return hasSuperType(named("io.javalin.http.Handler")).and(not(isInterface()));
    }

    @Override
    public ElementMatcher.Junction<ClassLoader> getClassLoaderMatcher() {
        return classLoaderCanLoadClass("io.javalin.http.Handler");
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("handle").and(takesArgument(0, named("io.javalin.http.Context")));
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Collections.singleton("javalin");
    }

    @Override
    public String getAdviceClassName() {
        return "co.elastic.apm.agent.javalin.JavalinInstrumentation$HandlerAdapterAdvice";
    }

    public static class HandlerAdapterAdvice {

        private static final WebConfiguration webConfig = GlobalTracer.requireTracerImpl().getConfig(WebConfiguration.class);

        // never invoked, only used to cache the fact that the io.javalin.http.Context#handlerType() method is unavailable in this Javalin version
        private static final MethodHandle NOOP = MethodHandles.constant(String.class, "Non-supported Javalin version");

        @Nullable
        private static MethodHandle handlerTypeMethodHandle = null;

        @Nullable
        private static HandlerType getHandlerType(Context context) {
            if (handlerTypeMethodHandle == null) {
                synchronized (HandlerAdapterAdvice.class) {
                    if (handlerTypeMethodHandle == null) {
                        try {
                            handlerTypeMethodHandle = MethodHandles.lookup().findVirtual(
                                context.getClass(), "handlerType", MethodType.methodType(HandlerType.class)
                            );
                            logger.debug("This Javalin version is supported");
                        } catch (NoSuchMethodException e) {
                            logger.info("The current Javalin version is not supported, only 3.13.8+ versions are supported");
                            handlerTypeMethodHandle = NOOP;
                        } catch (Throwable throwable) {
                            logger.error("Cannot get a method handle for io.javalin.http.Context#handlerType(), Javalin will not be traced", throwable);
                            handlerTypeMethodHandle = NOOP;
                        }
                    }
                }
            }
            HandlerType handlerType = null;
            if (handlerTypeMethodHandle != NOOP) {
                try {
                    //noinspection ConstantConditions
                    handlerType = (HandlerType) handlerTypeMethodHandle.invoke(context);
                } catch (Throwable throwable) {
                    logger.error("Cannot determine Javalin HandlerType. Javalin cannot be traced");
                }
            }
            return handlerType;
        }

        @Nullable
        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static Object setSpanAndTransactionName(@Advice.This Handler handler,
                                                       @Advice.Argument(0) Context ctx) {

            HandlerType handlerType = getHandlerType(ctx);

            // exit early if instrumentation cannot work because of missing Context.handlerType() method due to older version
            if (handlerType == null) {
                return null;
            }

            final Transaction transaction = tracer.currentTransaction();
            if (transaction == null) {
                return null;
            }

            final String handlerClassName = handler.getClass().getName();

            // do not create an own span for JavalinServlet.addHandler, as this not added by the users code and leads to confusion
            if (handlerClassName.startsWith("io.javalin.http.JavalinServlet")) {
                return null;
            }

            // transaction name gets only set if we are dealing with a HTTP method processing, not before/after handlers
            if (handlerType.isHttpMethod()) {
                final StringBuilder name = transaction.getAndOverrideName(PRIO_HIGH_LEVEL_FRAMEWORK, false);
                if (name != null) {
                    transaction.setFrameworkName(FRAMEWORK_NAME);
                    transaction.setFrameworkVersion(VersionUtils.getVersion(Handler.class, "io.javalin", "javalin"));
                    transaction.withType("request");
                    TransactionNameUtils.setNameFromHttpRequestPath(
                        handlerType.name(),
                        ctx.endpointHandlerPath(),
                        name,
                        webConfig.getUrlGroups()
                    );
                }
            }

            // create own span for all handlers including after/before
            final AbstractSpan<?> parent = tracer.getActive();
            if (parent == null) {
                return null;
            }

            Span span = parent.createSpan().activate();
            span.withType("app").withSubtype("handler");
            span.appendToName(handlerType.name()).appendToName(" ").appendToName(ctx.matchedPath());
            return span;
        }

        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
        public static void onAfterExecute(@Advice.Enter @Nullable Object spanObj,
                                          @Advice.Argument(0) Context ctx,
                                          @Advice.Thrown @Nullable Throwable t) {
            if (spanObj != null) {
                final Span span = (Span) spanObj;
                span.deactivate();

                final CompletableFuture<?> responseFuture = ctx.resultFuture();
                if (responseFuture == null) {
                    // sync request
                    span.captureException(t).end();
                } else {
                    // future was set in the handler, so we need to end the span only on future completion
                    responseFuture.whenComplete((o, futureThrowable) -> span.captureException(futureThrowable).end());
                }
            }
        }
    }
}
