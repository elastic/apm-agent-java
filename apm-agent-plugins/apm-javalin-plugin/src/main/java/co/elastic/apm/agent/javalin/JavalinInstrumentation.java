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
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Collections;

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

        private static Field handlerTypeField = null;

        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static Object setSpanAndTransactionName(@Advice.This Handler handler,
                                                       @Advice.Argument(0) Object ctxObject) throws Exception {
            final Transaction transaction = tracer.currentTransaction();
            if (transaction == null) {
                return null;
            }
            Context ctx = ((Context) ctxObject);

            // TODO FIXME I don't think this is the right way, I am sure there is a fancy bytecode trick that I am not aware off
            if (handlerTypeField == null) {
                Field field = ctx.getClass().getDeclaredField("handlerType");
                field.setAccessible(true);
                handlerTypeField = field;
            }

            final HandlerType handlerType = (HandlerType) handlerTypeField.get(ctx);
            final String handlerClassName = handler.getClass().getName();

            // transaction name like the HTTP path
            if (handlerType.isHttpMethod()) {
                transaction.setFrameworkName(FRAMEWORK_NAME);
                transaction.setFrameworkVersion(VersionUtils.getVersion(Handler.class, "io.javalin", "javalin"));
                // no need for anonymous handler class names in the transaction
                if (handlerClassName.startsWith("co.elastic.apm.agent.javalin.JavalinHandlerLambdaInstrumentation$WrappingHandler")
                    || handlerClassName.startsWith("io.javalin.http.JavalinServlet")) {
                    setName(transaction, handlerType.name() + " " + ctx.endpointHandlerPath());
                } else {
                    setName(transaction, handlerType.name() + " " + ctx.endpointHandlerPath() + " " + handlerClassName);
                }
            }

            // do not create an own span for JavalinServlet.addHandler, as this not added by the users code and leads to confusion
            if (handlerClassName.startsWith("io.javalin.http.JavalinServlet")) {
                return null;
            }

            // create own span for all handlers including after/before
            final Span span = startSpan(handlerType.name(), ctx.matchedPath(), ctx.method(), ctx.url());
            return span;
        }

        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
        public static void onAfterExecute(@Advice.Enter @Nullable Object spanObj,
                                          @Advice.Thrown @Nullable Throwable t) {
            if (spanObj instanceof Span) {
                final Span span = (Span) spanObj;
                span.deactivate();
                if (t != null) {
                    span.captureException(t);
                }
                span.end();
            }
        }

        private static Span startSpan(String handlerTypeName, String matchedPath, String method, String url) {
            final AbstractSpan<?> parent = tracer.getActive();
            if (parent == null) {
                return null;
            }

            Span span = parent.createSpan().activate();
            // TODO figure out correct type and subtype or leave out?
            span.withType("web")
                .withSubtype("javalin")
                .appendToName(handlerTypeName).appendToName(" ").appendToName(matchedPath);

            span.getContext().getHttp().withUrl(url).withMethod(method);

            return span;
        }

        private static void setName(Transaction transaction, String transactionName) {
            final StringBuilder name = transaction.getAndOverrideName(PRIO_HIGH_LEVEL_FRAMEWORK);
            if (name != null) {
                name.append(transactionName);
            }
        }
    }
}
