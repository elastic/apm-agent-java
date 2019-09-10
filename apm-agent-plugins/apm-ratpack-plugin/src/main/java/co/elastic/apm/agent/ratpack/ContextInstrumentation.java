/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2019 Elastic and contributors
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
package co.elastic.apm.agent.ratpack;

import co.elastic.apm.agent.bci.HelperClassManager;
import co.elastic.apm.agent.bci.VisibleForAdvice;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import ratpack.handling.Context;

import javax.annotation.Nullable;

import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

@SuppressWarnings("WeakerAccess")
public abstract class ContextInstrumentation extends AbstractRatpackInstrumentation {

    @VisibleForAdvice
    @Nullable
    public static HelperClassManager<ContextInstrumentation.ContextHelper<Context, TransactionHolder>> helperManager;

    private final ElementMatcher<? super MethodDescription> methodMatcher;

    public ContextInstrumentation(final ElasticApmTracer tracer, final ElementMatcher<? super MethodDescription> methodMatcher) {
        this.methodMatcher = methodMatcher;
        if (helperManager == null) {
            helperManager = HelperClassManager.ForSingleClassLoader.of(tracer,
                "co.elastic.apm.agent.ratpack.ContextHelperImpl");
        }
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return not(isInterface())
            .and(hasSuperType(named("ratpack.handling.Context")));
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return methodMatcher;
    }

    public interface ContextHelper<C, T> {

        void captureException(C context, Throwable throwable, ElasticApmTracer tracer, Class<T> transactionToken);
    }

    /**
     * Instrumentation that captures the recording of an error that is collected during the processing of a Ratpack
     * request. See ratpack.handling.Context#error(java.lang.Throwable)
     */
    public static class RatpackCaptureExceptionInstrumentation extends ContextInstrumentation {

        public RatpackCaptureExceptionInstrumentation(final ElasticApmTracer tracer) {
            super(tracer, named("error")
                .and(isPublic())
                .and(takesArgument(0, Throwable.class))
                .and(returns(TypeDescription.VOID))
            );
        }

        @Override
        public Class<?> getAdviceClass() {
            return RatpackCaptureErrorAdvice.class;
        }

        @VisibleForAdvice
        public static class RatpackCaptureErrorAdvice {

            @Advice.OnMethodEnter(suppress = Throwable.class)
            public static void onBeforeError(
                @Advice.Argument(0) final Throwable throwable,
                @Advice.This Context context
            ) {

                if (tracer == null || helperManager == null) {
                    return;
                }

                final ContextHelper<Context, TransactionHolder> helper = helperManager.getForClassLoaderOfClass(Context.class);

                if (helper != null) {

                    helper.captureException(context, throwable, tracer, TransactionHolder.class);
                }
            }
        }
    }
}
