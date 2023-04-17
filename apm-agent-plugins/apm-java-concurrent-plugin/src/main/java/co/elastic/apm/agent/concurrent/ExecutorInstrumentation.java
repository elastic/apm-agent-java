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
import co.elastic.apm.agent.sdk.state.GlobalVariables;
import co.elastic.apm.agent.util.ExecutorUtils;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned.ToArguments.ToArgument;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static co.elastic.apm.agent.bci.bytebuddy.CustomElementMatchers.isProxy;
import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.is;
import static net.bytebuddy.matcher.ElementMatchers.isOverriddenFrom;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.nameContains;
import static net.bytebuddy.matcher.ElementMatchers.nameEndsWith;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

public abstract class ExecutorInstrumentation extends TracerAwareInstrumentation {

    static final Set<String> excludedClasses = GlobalVariables.get(ExecutorInstrumentation.class, "excludedClasses", new HashSet<String>());

    static {
        // Used in Tomcat 7
        // Especially the wrapping of org.apache.tomcat.util.net.AprEndpoint$SocketProcessor is problematic
        // because that is the Runnable for the actual request processor thread.
        // Wrapping that leaks transactions and spans to other requests.
        excludedClasses.add("org.apache.tomcat.util.threads.ThreadPoolExecutor");
    }


    @Override
    public ElementMatcher<? super NamedElement> getTypeMatcherPreFilter() {
        return nameContains("Execut")
            .or(nameContains("Loop"))
            .or(nameContains("Pool"))
            .or(nameContains("Dispatch"));
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return hasSuperType(named("java.util.concurrent.Executor"))
            // executes on same thread, no need to wrap to activate again
            .and(not(named("org.apache.felix.resolver.ResolverImpl$DumbExecutor")))
            .and(not(nameContains("jetty")))
            .and(not(nameContains("tomcat")))
            .and(not(nameContains("undertow")))
            .and(not(nameContains("netty")))
            .and(not(nameContains("vertx")))
            // hazelcast tries to serialize the Runnables/Callables to execute them on remote JVMs
            .and(not(nameStartsWith("com.hazelcast")))
            .and(not(isProxy()));
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Arrays.asList("concurrent", "executor");
    }

    private static boolean isExcluded(@Advice.This Executor executor) {
        return excludedClasses.contains(executor.getClass().getName()) ||
            ExecutorUtils.isAgentExecutor(executor);
    }

    public static class ExecutorRunnableInstrumentation extends ExecutorInstrumentation {

        public static class AdviceClass {
            @Nullable
            @Advice.AssignReturned.ToArguments(@ToArgument(0))
            @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
            public static Runnable onExecute(@Advice.This Executor thiz,
                                             @Advice.Argument(0) @Nullable Runnable runnable) {
                if (ExecutorInstrumentation.isExcluded(thiz)) {
                    return runnable;
                }
                return JavaConcurrent.withContext(runnable, tracer);
            }

            @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
            public static void onExit(@Nullable @Advice.Thrown Throwable thrown,
                                      @Advice.Argument(value = 0) @Nullable Runnable runnable) {
                JavaConcurrent.doFinally(thrown, runnable);
            }
        }

        /**
         * <ul>
         *     <li>{@link ExecutorService#execute(Runnable)}</li>
         *     <li>{@link ExecutorService#submit(Runnable)}</li>
         *     <li>{@link ExecutorService#submit(Runnable, Object)}</li>
         *     <li>{@link ScheduledExecutorService#schedule(Runnable, long, TimeUnit)}</li>
         * </ul>
         */
        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("execute").and(returns(void.class)).and(takesArguments(Runnable.class))
                .or(named("submit").and(returns(hasSuperType(is(Future.class)))).and(takesArguments(Runnable.class)))
                .or(named("submit").and(returns(hasSuperType(is(Future.class)))).and(takesArguments(Runnable.class, Object.class)))
                .or(named("schedule").and(returns(hasSuperType(is(ScheduledFuture.class)))).and(takesArguments(Runnable.class, long.class, TimeUnit.class)));
        }
    }

    public static class ExecutorCallableInstrumentation extends ExecutorInstrumentation {

        public static class AdviceClass {
            @Nullable
            @Advice.AssignReturned.ToArguments(@ToArgument(0))
            @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
            public static Callable<?> onSubmit(@Advice.This Executor thiz,
                                               @Advice.Argument(0) @Nullable Callable<?> callable) {
                if (ExecutorInstrumentation.isExcluded(thiz)) {
                    return callable;
                }
                return JavaConcurrent.withContext(callable, tracer);
            }

            @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
            public static void onExit(@Nullable @Advice.Thrown Throwable thrown,
                                      @Advice.Argument(0) @Nullable Callable<?> callable) {
                JavaConcurrent.doFinally(thrown, callable);
            }
        }

        /**
         * <ul>
         *     <li>{@link ExecutorService#submit(Callable)}</li>
         *     <li>{@link ScheduledExecutorService#schedule(Callable, long, TimeUnit)}</li>
         * </ul>
         */
        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("submit").and(returns(hasSuperType(is(Future.class)))).and(takesArguments(Callable.class))
                .or(named("schedule").and(returns(hasSuperType(is(ScheduledFuture.class)))).and(takesArguments(Callable.class, long.class, TimeUnit.class)));
        }

    }

    public static class ExecutorInvokeAnyAllInstrumentation extends ExecutorInstrumentation {

        /**
         * <ul>
         *     <li>{@link ExecutorService#invokeAll}</li>
         *     <li>{@link ExecutorService#invokeAny}</li>
         * </ul>
         */
        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return nameStartsWith("invoke")
                .and(nameEndsWith("Any").or(nameEndsWith("All")))
                .and(isPublic())
                .and(takesArgument(0, Collection.class))
                .and(isOverriddenFrom(ExecutorService.class));
        }

        public static class AdviceClass {
            @Nullable
            @Advice.AssignReturned.ToArguments(@ToArgument(0))
            @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
            public static <T> Collection<? extends Callable<T>> onEnter(@Advice.This Executor thiz,
                                                                        @Nullable @Advice.Argument(0) Collection<? extends Callable<T>> callables) {
                if (ExecutorInstrumentation.isExcluded(thiz)) {
                    return callables;
                }
                return JavaConcurrent.withContext(callables, tracer);
            }

            @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
            public static void onExit(@Nullable @Advice.Thrown Throwable thrown,
                                      @Nullable @Advice.Argument(0) Collection<? extends Callable<?>> callables) {
                JavaConcurrent.doFinally(thrown, callables);
            }
        }

        @Override
        public Collection<String> getInstrumentationGroupNames() {
            return Arrays.asList("concurrent", "executor", "executor-collection");
        }
    }

    public static class ForkJoinPoolInstrumentation extends ExecutorInstrumentation {

        @Override
        public ElementMatcher<? super TypeDescription> getTypeMatcher() {
            return hasSuperType(is(ForkJoinPool.class)).and(super.getTypeMatcher());
        }

        /**
         * <ul>
         *     <li>{@link ForkJoinPool#execute(ForkJoinTask)}</li>
         *     <li>{@link ForkJoinPool#submit(ForkJoinTask)}</li>
         *     <li>{@link ForkJoinPool#invoke(ForkJoinTask)}</li>
         * </ul>
         */
        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("execute").and(returns(void.class)).and(takesArguments(ForkJoinTask.class))
                .or(named("submit").and(returns(hasSuperType(is(ForkJoinTask.class)))).and(takesArguments(ForkJoinTask.class)))
                .or(named("invoke").and(returns(Object.class)).and(takesArguments(ForkJoinTask.class)));
        }

        public static class AdviceClass {
            @Nullable
            @Advice.AssignReturned.ToArguments(@ToArgument(0))
            @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
            public static ForkJoinTask<?> onExecute(@Advice.This Executor thiz,
                                                    @Advice.Argument(0) @Nullable ForkJoinTask<?> task) {
                if (ExecutorInstrumentation.isExcluded(thiz)) {
                    return task;
                }
                return JavaConcurrent.withContext(task, tracer);
            }

            @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
            public static void onExit(@Nullable @Advice.Thrown Throwable thrown,
                                      @Advice.Argument(value = 0) @Nullable ForkJoinTask<?> task) {
                JavaConcurrent.doFinally(thrown, task);
            }
        }

        @Override
        public Collection<String> getInstrumentationGroupNames() {
            return Arrays.asList("concurrent", "fork-join");
        }
    }

}
