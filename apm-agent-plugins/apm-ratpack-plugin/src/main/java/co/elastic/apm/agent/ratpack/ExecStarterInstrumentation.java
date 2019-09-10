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
import ratpack.exec.ExecStarter;
import ratpack.exec.Execution;
import ratpack.func.Action;
import ratpack.registry.RegistrySpec;

import javax.annotation.Nullable;

import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

/**
 * Instrumentation for starting and ending a transaction.
 *
 * Ratpack's {@link ExecStarter} provides hooks for setting up a request to be handled by its execution model. These
 * hooks are good places to start and end a transaction.
 *
 * @see ExecStarter#register(Action)
 * @see ExecStarter#onStart(Action)
 * @see ExecStarter#onComplete(Action)
 * @see ExecStarter#onError(Action)
 * @see ratpack.handling.Context#error(Throwable)
 */
@SuppressWarnings("WeakerAccess")
public abstract class ExecStarterInstrumentation extends AbstractRatpackInstrumentation {

    @VisibleForAdvice
    @Nullable
    public static HelperClassManager<ExecStarterHelper<Action<? super RegistrySpec>, TransactionHolder, Action<? super Execution>>> helperManager;

    private final ElementMatcher<? super MethodDescription> methodMatcher;

    ExecStarterInstrumentation(final ElasticApmTracer tracer, final ElementMatcher<? super MethodDescription> methodMatcher) {
        this.methodMatcher = methodMatcher;
        if (helperManager == null) {
            helperManager = HelperClassManager.ForSingleClassLoader.of(tracer,
                "co.elastic.apm.agent.ratpack.ExecStarterHelperImpl",
                "co.elastic.apm.agent.ratpack.TransactionHolderImpl",
                "co.elastic.apm.agent.ratpack.ExecStarterHelperImpl$FillTransactionRequest",
                "co.elastic.apm.agent.ratpack.ExecStarterHelperImpl$FillTransactionResponse",
                "co.elastic.apm.agent.ratpack.ExecStarterHelperImpl$FillTransactionName",
                "co.elastic.apm.agent.ratpack.ExecStarterHelperImpl$RegisterTransactionAction",
                "co.elastic.apm.agent.ratpack.ExecStarterHelperImpl$StartTransactionAction",
                "co.elastic.apm.agent.ratpack.ExecStarterHelperImpl$EndTransactionAction");
        }
    }

    @Override
    public final ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return not(isInterface())
            .and(hasSuperType(named("ratpack.exec.ExecStarter")));
    }

    @Override
    public final ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return methodMatcher;
    }

    public interface ExecStarterHelper<AR, T, AE> {

        AR registerTransaction(AR action, Class<T> transactionToken);

        AE startTransaction(AE action, ElasticApmTracer tracer, Class<T> transactionToken, ClassLoader classLoader);

        AE endTransaction(AE action, ElasticApmTracer tracer, Class<T> transactionToken);
    }

    public static class RatpackRegisterTransactionInstrumentation extends ExecStarterInstrumentation {

        public RatpackRegisterTransactionInstrumentation(final ElasticApmTracer tracer) {
            super(tracer, named("register")
                .and(isPublic())
                .and(takesArgument(0, named("ratpack.func.Action")))
                .and(returns(named("ratpack.exec.ExecStarter"))));
        }

        @Override
        public Class<?> getAdviceClass() {
            return RatpackRegisterTransactionAdvice.class;
        }

        @VisibleForAdvice
        @IgnoreJRERequirement
        public static class RatpackRegisterTransactionAdvice {

            @Advice.OnMethodEnter(suppress = Throwable.class)
            public static void onBeforeRegister(
                @Advice.Argument(value = 0, readOnly = false) Action<? super RegistrySpec> action) {

                if (tracer == null || helperManager == null) {
                    return;
                }

                final ExecStarterHelper<Action<? super RegistrySpec>, TransactionHolder, Action<? super Execution>> helper = helperManager.getForClassLoaderOfClass(ExecStarter.class);

                if (helper != null) {
                    //noinspection UnusedAssignment
                    action = helper.registerTransaction(action, TransactionHolder.class);
                }
            }
        }
    }

    public static class RatpackStartTransactionInstrumentation extends ExecStarterInstrumentation {

        public RatpackStartTransactionInstrumentation(final ElasticApmTracer tracer) {
            super(tracer, named("onStart")
                .and(isPublic())
                .and(takesArgument(0, named("ratpack.func.Action")))
//                .and(returns(TypeDescription.VOID)));
                .and(returns(named("ratpack.exec.ExecStarter"))));
        }

        @Override
        public Class<?> getAdviceClass() {
            return RatpackStartTransactionAdvice.class;
        }

        @VisibleForAdvice
        public static class RatpackStartTransactionAdvice {

            @Advice.OnMethodEnter(suppress = Throwable.class)
            public static void onBeforerOnStart(
                @Advice.Argument(value = 0, readOnly = false) Action<? super Execution> action,
                @Advice.Origin final Class<?> origin
            ) {

                if (tracer == null || helperManager == null) {
                    return;
                }

                final ExecStarterHelper<Action<? super RegistrySpec>, TransactionHolder, Action<? super Execution>> helper = helperManager.getForClassLoaderOfClass(ExecStarter.class);

                if (helper != null) {
                    //noinspection UnusedAssignment
                    action = helper.startTransaction(action, tracer, TransactionHolder.class, origin.getClassLoader());
                }
            }
        }
    }

    public static class RatpackEndTransactionInstrumentation extends ExecStarterInstrumentation {

        public RatpackEndTransactionInstrumentation(final ElasticApmTracer tracer) {
            super(tracer, named("onComplete")
                .and(isPublic())
                .and(takesArgument(0, named("ratpack.func.Action")))
                .and(returns(named("ratpack.exec.ExecStarter"))));
        }

        @Override
        public Class<?> getAdviceClass() {
            return RatpackEndTransactionAdvice.class;
        }

        @VisibleForAdvice
        @IgnoreJRERequirement
        public static class RatpackEndTransactionAdvice {

            @SuppressWarnings("unused")
            @Advice.OnMethodEnter(suppress = Throwable.class)
            public static void onBeforeComplete(
                @Advice.Argument(value = 0, readOnly = false) Action<? super Execution> action) {

                if (tracer == null || helperManager == null) {
                    return;
                }

                final ExecStarterHelper<Action<? super RegistrySpec>, TransactionHolder, Action<? super Execution>> helper = helperManager.getForClassLoaderOfClass(ExecStarter.class);

                if (helper != null) {
                    //noinspection UnusedAssignment
                    action = helper.endTransaction(action, tracer, TransactionHolder.class);
                }
            }
        }
    }
}
