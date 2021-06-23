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
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.util.VersionUtils;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.javalin.http.HandlerType;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;
import java.security.ProtectionDomain;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;

import static co.elastic.apm.agent.bci.bytebuddy.CustomElementMatchers.classLoaderCanLoadClass;
import static co.elastic.apm.agent.bci.bytebuddy.CustomElementMatchers.implementationVersionLte;
import static co.elastic.apm.agent.impl.transaction.AbstractSpan.PRIO_HIGH_LEVEL_FRAMEWORK;
import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

public class JavalinInstrumentation extends TracerAwareInstrumentation {

    private static final String FRAMEWORK_NAME = "Javalin";

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return hasSuperType(named("io.javalin.http.Handler")).and(not(isInterface()));
    }

    @Override
    public ElementMatcher.Junction<ClassLoader> getClassLoaderMatcher() {
        return classLoaderCanLoadClass("io.javalin.http.Handler");
    }

    // ctx.handlerType() was introduced in Javalin 3.13.8 and above, so instrument only in that case
    @Override
    public ElementMatcher.Junction<ProtectionDomain> getProtectionDomainPostFilter() {
        return implementationVersionLte("3.13.7");
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

        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static Object setSpanAndTransactionName(@Advice.This Handler handler,
                                                       @Advice.Argument(0) Object ctxObject) throws Exception {
            final Transaction transaction = tracer.currentTransaction();
            if (transaction == null) {
                return null;
            }
            Context ctx = ((Context) ctxObject);

            final String handlerClassName = handler.getClass().getName();
            final boolean isLambdaHandler = handlerClassName.equals("co.elastic.apm.agent.javalin.JavalinHandlerLambdaInstrumentation$WrappingHandler");

            // transaction name gets only set if we are dealing with a HTTP method processing, not before/after handlers
            if (ctx.handlerType().isHttpMethod()) {
                final StringBuilder name = transaction.getAndOverrideName(PRIO_HIGH_LEVEL_FRAMEWORK);
                if (name != null) {
                    transaction.setFrameworkName(FRAMEWORK_NAME);
                    transaction.setFrameworkVersion(VersionUtils.getVersion(Handler.class, "io.javalin", "javalin"));
                    transaction.withType("request");

                    name.append(ctx.handlerType().name()).append(" ").append(ctx.endpointHandlerPath());

                    // no need for anonymous handler class names in the transaction
                    if (!isLambdaHandler) {
                        name.append(" ").append(handlerClassName);
                    }
                }
            }

            // do not create an own span for JavalinServlet.addHandler, as this not added by the users code and leads to confusion
            if (handlerClassName.startsWith("io.javalin.http.JavalinServlet")) {
                return null;
            }

            // create own span for all handlers including after/before
            return startSpan(handlerClassName, ctx.handlerType(), isLambdaHandler, ctx.matchedPath(), ctx.method(), ctx.url());
        }

        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
        public static void onAfterExecute(@Advice.Enter @Nullable Object spanObj,
                                          @Advice.Argument(0) Object ctxObject,
                                          @Advice.Thrown @Nullable Throwable t) {
            if (spanObj != null) {
                Context ctx = ((Context) ctxObject);
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

        private static Span startSpan(String handlerClassName, HandlerType handlerType, boolean isLambdaHandler, String matchedPath, String method, String url) {
            final AbstractSpan<?> parent = tracer.getActive();
            if (parent == null) {
                return null;
            }

            Span span = parent.createSpan().activate();
            span.appendToName(handlerType.name()).appendToName(" ").appendToName(matchedPath).appendToName(" ");
            if (isLambdaHandler) {
                span.appendToName("<Lambda>");
            } else {
                span.appendToName(handlerClassName);
            }

            span.getContext().getHttp().withUrl(url).withMethod(method);

            return span;
        }
    }
}
