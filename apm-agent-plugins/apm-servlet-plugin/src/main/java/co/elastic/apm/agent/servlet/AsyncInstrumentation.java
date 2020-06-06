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
package co.elastic.apm.agent.servlet;

import co.elastic.apm.agent.bci.VisibleForAdvice;
import co.elastic.apm.agent.bci.bytebuddy.postprocessor.AssignToArgument;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.servlet.helper.AsyncContextAdviceHelperImpl;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;
import javax.servlet.AsyncContext;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import java.util.Arrays;
import java.util.Collection;

import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.nameContains;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

/**
 * Only the methods annotated with {@link Advice.OnMethodEnter} and {@link Advice.OnMethodExit} may contain references to
 * {@code javax.servlet}, as these are inlined into the matching methods.
 * The agent itself does not have access to the Servlet API classes, as they are loaded by a child class loader.
 * See https://github.com/raphw/byte-buddy/issues/465 for more information.
 * However, the helper class {@link AsyncContextAdviceHelper} has access to the Servlet API,
 * as it is loaded by the child classloader of {@link AsyncContext}
 * (see {@link StartAsyncInstrumentation.StartAsyncAdvice#onExitStartAsync(AsyncContext)}
 * and {@link AsyncContextInstrumentation.AsyncContextStartAdvice#onEnterAsyncContextStart(Runnable)}).
 */
public abstract class AsyncInstrumentation extends AbstractServletInstrumentation {

    private static final String SERVLET_API_ASYNC_GROUP_NAME = "servlet-api-async";

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Arrays.asList(ServletInstrumentation.SERVLET_API, SERVLET_API_ASYNC_GROUP_NAME);
    }

    public interface AsyncContextAdviceHelper<T> {
        void onExitStartAsync(T asyncContext);
    }

    public static class StartAsyncInstrumentation extends AsyncInstrumentation {

        @Override
        public ElementMatcher<? super NamedElement> getTypeMatcherPreFilter() {
            return nameContains("Request");
        }

        @Override
        public ElementMatcher<? super TypeDescription> getTypeMatcher() {
            return not(isInterface())
                .and(hasSuperType(named("javax.servlet.ServletRequest")));
        }

        /**
         * Matches
         * <ul>
         * <li>{@link ServletRequest#startAsync()}</li>
         * <li>{@link ServletRequest#startAsync(ServletRequest, ServletResponse)}</li>
         * </ul>
         *
         * @return
         */
        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return isPublic()
                .and(named("startAsync"))
                .and(returns(named("javax.servlet.AsyncContext")))
                .and(takesArguments(0)
                    .or(
                        takesArgument(0, named("javax.servlet.ServletRequest"))
                            .and(takesArgument(1, named("javax.servlet.ServletResponse")))
                    )
                );
        }

        @Override
        public Class<?> getAdviceClass() {
            return StartAsyncAdvice.class;
        }

        @VisibleForAdvice
        public static class StartAsyncAdvice {
            @VisibleForAdvice
            public static AsyncContextAdviceHelper<AsyncContext> asyncHelper = new AsyncContextAdviceHelperImpl(tracer);

            @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
            public static void onExitStartAsync(@Advice.Return AsyncContext asyncContext) {
                asyncHelper.onExitStartAsync(asyncContext);
            }
        }
    }

    public static class AsyncContextInstrumentation extends AsyncInstrumentation {

        @Override
        public ElementMatcher<? super NamedElement> getTypeMatcherPreFilter() {
            return nameContains("AsyncContext");
        }

        @Override
        public ElementMatcher<? super TypeDescription> getTypeMatcher() {
            return not(isInterface())
                .and(hasSuperType(named("javax.servlet.AsyncContext")));
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return isPublic()
                .and(named("start"))
                .and(takesArguments(Runnable.class));
        }

        @Override
        public Class<?> getAdviceClass() {
            return AsyncContextStartAdvice.class;
        }

        @VisibleForAdvice
        public static class AsyncContextStartAdvice {

            @Nullable
            @AssignToArgument(0)
            @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
            public static Runnable onEnterAsyncContextStart(@Advice.Argument(0) @Nullable Runnable runnable) {
                if (tracer != null && runnable != null && tracer.isWrappingAllowedOnThread()) {
                    final Transaction transaction = tracer.currentTransaction();
                    if (transaction != null) {
                        runnable = transaction.withActive(runnable);
                        tracer.avoidWrappingOnThread();
                    }
                }
                return runnable;
            }

            @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Exception.class, inline = false)
            public static void onExitAsyncContextStart() {
                if (tracer != null) {
                    tracer.allowWrappingOnThread();
                }
            }
        }
    }
}
