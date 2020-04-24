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
package co.elastic.apm.agent.scala.concurrent;

import co.elastic.apm.agent.bci.ElasticApmInstrumentation;
import co.elastic.apm.agent.bci.VisibleForAdvice;
import co.elastic.apm.agent.impl.transaction.TraceContextHolder;
import com.blogspot.mydailyjava.weaklockfree.WeakConcurrentMap;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import scala.concurrent.Promise;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collection;

import static net.bytebuddy.matcher.ElementMatchers.*;

public abstract class FutureInstrumentation extends ElasticApmInstrumentation {

    @VisibleForAdvice
    @SuppressWarnings("WeakerAccess")
    public static final WeakConcurrentMap<Promise<?>, TraceContextHolder<?>> promisesToContext =
        new WeakConcurrentMap.WithInlinedExpunction<>();

    @Nonnull
    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Arrays.asList("concurrent", "future");
    }

    public static class ExecutionContextInstrumentation extends FutureInstrumentation {

        @Override
        public ElementMatcher<? super TypeDescription> getTypeMatcher() {
            return hasSuperType(named("scala.concurrent.ExecutionContext"));
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher(){
            return named("execute").and(returns(void.class)).and(takesArguments(Runnable.class));
        }

        @Advice.OnMethodEnter(suppress = Throwable.class)
        public static void onExecute(@Advice.Argument(value = 0, readOnly = false) @Nullable Runnable runnable) {
            final TraceContextHolder<?> active = getActive();
            if (active != null && runnable != null && tracer != null) {
                // Do no discard branches leading to async operations so not to break span references
                active.setDiscard(false);
                runnable = active.withActive(runnable);
            }
        }

    }

    public static class BatchedExecutionContextInstrumentation extends FutureInstrumentation {

        @Override
        public ElementMatcher<? super TypeDescription> getTypeMatcher() {
            return hasSuperType(named("scala.concurrent.BatchingExecutor"));
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher(){
            return named("submitForExecution").and(returns(void.class)).and(takesArguments(Runnable.class))
                .or(named("submitAsyncBatched").and(returns(void.class)).and(takesArguments(Runnable.class)))
                .or(named("submitSyncBatched").and(returns(void.class)).and(takesArguments(Runnable.class)));
        }

        @Advice.OnMethodEnter(suppress = Throwable.class)
        public static void onExecute(@Advice.Argument(value = 0, readOnly = false) @Nullable Runnable runnable) {
            final TraceContextHolder<?> active = getActive();
            if (active != null && runnable != null && tracer != null) {
                // Do no discard branches leading to async operations so not to break span references
                active.setDiscard(false);
                runnable = active.withActive(runnable);
            }
        }

    }

    public static class ConstructorInstrumentation extends FutureInstrumentation {

        @Override
        public ElementMatcher<? super TypeDescription> getTypeMatcher() {
            return hasSuperType(named("scala.concurrent.impl.Promise$Transformation"));
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return isConstructor();
        }

        @Advice.OnMethodExit(suppress = Throwable.class)
        public static void onExit(@Advice.This Promise<?> thiz) {
            final TraceContextHolder<?> active = getActive();
            if (active != null) {
                promisesToContext.put(thiz, active);
            }
        }

    }

    public static class RunInstrumentation extends FutureInstrumentation {

        @Override
        public ElementMatcher<? super TypeDescription> getTypeMatcher() {
            return hasSuperType(named("scala.concurrent.impl.Promise$Transformation"));
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("run").and(returns(void.class));
        }

        @VisibleForAdvice
        @Advice.OnMethodEnter(suppress = Throwable.class)
        public static void onEnter(@Advice.This Promise<?> thiz) {
            final TraceContextHolder<?> context = promisesToContext.getIfPresent(thiz);
            if (tracer != null && context != null) {
                tracer.activate(context);
            }
        }

        @Advice.OnMethodExit(suppress = Throwable.class)
        public static void onExit(@Advice.This Promise<?> thiz) {
            promisesToContext.remove(thiz);
        }

    }

}
