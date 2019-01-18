/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2019 Elastic and contributors
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package co.elastic.apm.agent.concurrent;

import co.elastic.apm.agent.bci.ElasticApmInstrumentation;
import co.elastic.apm.agent.impl.transaction.TraceContextHolder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.nameContains;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

public abstract class ExecutorInstrumentation extends ElasticApmInstrumentation {

    @Override
    public ElementMatcher<? super NamedElement> getTypeMatcherPreFilter() {
        return nameContains("Execut")
            .or(nameContains("Loop"))
            .or(nameContains("Pool"))
            .or(nameContains("Dispatch"));
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return hasSuperType(named("java.util.concurrent.Executor"));
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Arrays.asList("concurrent", "executor");
    }

    public static class ExecutorRunnableInstrumentation extends ExecutorInstrumentation {
        @Advice.OnMethodEnter(suppress = Throwable.class)
        public static void onExecute(@Advice.This Executor thiz, @Advice.Argument(value = 0, readOnly = false) @Nullable Runnable runnable) {
            final TraceContextHolder<?> active = ExecutorInstrumentation.getActive();
            if (active != null && runnable != null) {
                // this could be a problem when the executor casts to a specific Runnable
                runnable = active.withActiveContext(runnable);
            }
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("execute").and(returns(void.class)).and(takesArguments(Runnable.class))
                .or(named("submit").and(returns(Future.class)).and(takesArguments(Runnable.class)))
                .or(named("submit").and(returns(Future.class)).and(takesArguments(Runnable.class, Object.class)));
        }
    }

    public static class ExecutorCallableInstrumentation extends ExecutorInstrumentation {
        @Advice.OnMethodEnter(suppress = Throwable.class)
        public static void onSubmit(@Advice.This ExecutorService thiz, @Advice.Argument(value = 0, readOnly = false) @Nullable Callable<?> callable) {
            final TraceContextHolder<?> active = ExecutorInstrumentation.getActive();
            if (active != null && callable != null) {
                // this could be a problem when the executor casts to a specific Callable
                callable = active.withActiveContext(callable);
            }
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("submit").and(returns(Future.class)).and(takesArguments(Callable.class));
        }

    }

}
