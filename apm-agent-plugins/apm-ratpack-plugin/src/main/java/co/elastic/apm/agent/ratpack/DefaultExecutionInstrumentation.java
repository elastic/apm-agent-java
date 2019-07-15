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
import org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement;
import ratpack.exec.Execution;
import ratpack.exec.internal.DefaultExecution;

import javax.annotation.Nullable;

import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

/**
 * This instrumentation tracks how a request is being serviced in Ratpack's execution environment. The environment
 * is asynchronous, so the request is activated and deactivated and can switch threads.
 *
 * Ratpack tries to hide the complexity of managing this, and as a result, this is one of the few Ratpack "internal"
 * classes that needs instrumentation.
 *
 * @see DefaultExecution#drain()
 * @see DefaultExecution#unbindFromThread()
 * @see DefaultExecution#bindToThread()
 */
@SuppressWarnings("WeakerAccess")
public abstract class DefaultExecutionInstrumentation extends AbstractRatpackInstrumentation {

    @VisibleForAdvice
    @Nullable
    public static HelperClassManager<DefaultExecutionHelper<Execution, TransactionHolder>> helperManager;

    private final ElementMatcher<? super MethodDescription> methodMatcher;

    DefaultExecutionInstrumentation(final ElasticApmTracer tracer, final ElementMatcher<? super MethodDescription> methodMatcher) {
        this.methodMatcher = methodMatcher;
        if (helperManager == null) {
            helperManager = HelperClassManager.ForSingleClassLoader.of(tracer,
                "co.elastic.apm.agent.ratpack.DefaultExecutionHelperImpl");
        }
    }

    @Override
    public final ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("ratpack.exec.internal.DefaultExecution");
    }

    @Override
    public final ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return methodMatcher;
    }

    public interface DefaultExecutionHelper<E, T> {

        void activateWhenBound(E execution, ElasticApmTracer tracer, Class<T> transactionToken);

        void deactivateWhenUnbound(Execution execution, ElasticApmTracer tracer, Class<T> transactionToken);
    }

    /**
     * Tracks when the request is bound to a thread so that the transaction can be activated.
     *
     * @see DefaultExecution#bindToThread()
     */
    public static class RatpackActivateTransactionInstrumentation extends DefaultExecutionInstrumentation {
        public RatpackActivateTransactionInstrumentation(final ElasticApmTracer tracer) {
            super(tracer, named("bindToThread")
                .and(isPublic())
                .and(takesArguments(0))
                .and(returns(TypeDescription.VOID)));
        }

        @Override
        public Class<?> getAdviceClass() {
            return RatpackActivateTransactionAdvice.class;
        }

        @VisibleForAdvice
        @IgnoreJRERequirement
        public static class RatpackActivateTransactionAdvice {

            @SuppressWarnings("unused")
            @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
            public static void onAfterBind(@Advice.This final Execution execution) {

                if (tracer == null || helperManager == null) {
                    return;
                }

                final DefaultExecutionHelper<Execution, TransactionHolder> helper = helperManager.getForClassLoaderOfClass(Execution.class);

                if (helper != null) {

                    helper.activateWhenBound(execution, tracer, TransactionHolder.class);
                }
            }
        }
    }

    /**
     * Tracks when the request is removed from a thread so that the transaction can be de-activated.
     *
     * @see DefaultExecution#unbindFromThread()
     */
    public static class RatpackDeactivateTransactionInstrumentation extends DefaultExecutionInstrumentation {
        public RatpackDeactivateTransactionInstrumentation(final ElasticApmTracer tracer) {
            super(tracer, named("unbindFromThread")
                .and(isPublic())
                .and(takesArguments(0))
                .and(returns(TypeDescription.VOID)));
        }

        @Override
        public Class<?> getAdviceClass() {
            return RatpackDeactivateAdvice.class;
        }

        @VisibleForAdvice
        @IgnoreJRERequirement
        public static class RatpackDeactivateAdvice {

            @SuppressWarnings("unused")
            @Advice.OnMethodEnter(suppress = Throwable.class)
            public static void onBeforeUnbind(@Advice.This final Execution execution) {

                if (tracer == null || helperManager == null) {
                    return;
                }

                final DefaultExecutionHelper<Execution, TransactionHolder> helper = helperManager.getForClassLoaderOfClass(Execution.class);

                if (helper != null) {

                    helper.deactivateWhenUnbound(execution, tracer, TransactionHolder.class);
                }
            }
        }
    }
}
