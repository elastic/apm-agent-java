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
package co.elastic.apm.agent.concurrent;

import co.elastic.apm.agent.bci.TracerAwareInstrumentation;
import co.elastic.apm.agent.tracer.AbstractSpan;
import co.elastic.apm.agent.sdk.DynamicTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.Callable;
import java.util.concurrent.ForkJoinTask;

import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.is;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

/**
 * Used only within {@link JavaConcurrent#withContext} to
 * {@linkplain DynamicTransformer#ensureInstrumented(Class, Collection) ensure}
 * that particular {@link Callable}, {@link Runnable} and {@link ForkJoinTask} classes are instrumented.
 */
public class RunnableCallableForkJoinTaskInstrumentation extends TracerAwareInstrumentation {

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return hasSuperType(
            is(Runnable.class)
                .or(is(Callable.class))
                .or(is(ForkJoinTask.class))
        );
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("run").and(takesArguments(0))
            .or(named("call").and(takesArguments(0)))
            .or(named("exec").and(takesArguments(0).and(returns(boolean.class))));
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Arrays.asList("concurrent", "executor");
    }

    public static class AdviceClass {
        @Nullable
        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static Object onEnter(@Advice.This Object thiz) {
            return JavaConcurrent.restoreContext(thiz, tracer);
        }

        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
        public static void onExit(@Advice.Thrown Throwable thrown,
                                  @Nullable @Advice.Enter Object context) {
            if (context instanceof AbstractSpan) {
                ((AbstractSpan<?>) context).deactivate();
            }
        }
    }
}
