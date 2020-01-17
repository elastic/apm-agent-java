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
package co.elastic.apm.agent.concurrent;

import co.elastic.apm.agent.bci.ElasticApmInstrumentation;
import co.elastic.apm.agent.bci.VisibleForAdvice;
import co.elastic.apm.agent.impl.transaction.TraceContextHolder;
import co.elastic.apm.agent.util.DataStructures;
import com.blogspot.mydailyjava.weaklockfree.WeakConcurrentSet;
import net.bytebuddy.asm.Advice;
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
import java.util.concurrent.Future;

import static co.elastic.apm.agent.bci.bytebuddy.CustomElementMatchers.isProxy;
import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.nameContains;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

public abstract class ExecutorInstrumentation extends ElasticApmInstrumentation {

    @VisibleForAdvice
    public static final WeakConcurrentSet<Executor> excluded = DataStructures.createWeakConcurrentSetWithCleanerThread();
    @VisibleForAdvice
    public static final Set<String> excludedClasses = new HashSet<>();

    static {
        // this pool relies on the task to be an instance of org.glassfish.enterprise.concurrent.internal.ManagedFutureTask
        // the wrapping is done in org.glassfish.enterprise.concurrent.ManagedExecutorServiceImpl.execute
        // so this pool only works when called directly from ManagedExecutorServiceImpl
        // excluding this class from instrumentation does not work as it inherits the execute and submit methods
        excludedClasses.add("org.glassfish.enterprise.concurrent.internal.ManagedThreadPoolExecutor");

        // Used in Tomcat 7
        // Especially the wrapping of org.apache.tomcat.util.net.AprEndpoint$SocketProcessor is problematic
        // because that is the Runnable for the actual request processor thread.
        // Wrapping that leaks transactions and spans to other requests.
        excludedClasses.add("org.apache.tomcat.util.threads.ThreadPoolExecutor");

        // This pool relies on the task to be an instance of com.pilotfish.eip.server.ntm.transact.StageTransactionRunner
        // in its beforeExecute implementation.
        excludedClasses.add("com.pilotfish.eip.server.ntm.pool.NTMThreadPool");
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
            // hazelcast tries to serialize the Runnables/Callables to execute them on remote JVMs
            .and(not(nameStartsWith("com.hazelcast")))
            .and(not(isProxy()));
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Arrays.asList("concurrent", "executor");
    }

    @VisibleForAdvice
    public static boolean isExcluded(@Advice.This Executor executor) {
        return excluded.contains(executor) || excludedClasses.contains(executor.getClass().getName());
    }

    public static class ExecutorRunnableInstrumentation extends ExecutorInstrumentation {
        @SuppressWarnings("Duplicates")
        @Advice.OnMethodEnter(suppress = Throwable.class)
        public static void onExecute(@Advice.This Executor thiz,
                                     @Advice.Argument(value = 0, readOnly = false) @Nullable Runnable runnable,
                                     @Advice.Local("original") Runnable original) {
            final TraceContextHolder<?> active = ExecutorInstrumentation.getActive();
            if (active != null && runnable != null && !isExcluded(thiz) && tracer != null && tracer.isWrappingAllowedOnThread()) {
                //noinspection UnusedAssignment
                original = runnable;
                // Do no discard branches leading to async operations so not to break span references
                active.setDiscard(false);
                runnable = active.withActive(runnable);
                tracer.avoidWrappingOnThread();
            }
        }

        // This advice detects if the Executor can't cope with our wrappers
        // If so, it retries without the wrapper and adds it to a list of excluded Executor instances
        // which disables context propagation for those
        // There is a slight risk that retrying causes a side effect but the more likely scenario is that adding the task to the queue
        // fails and noting has been executed yet.
        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Exception.class, repeatOn = Advice.OnNonDefaultValue.class)
        public static boolean onError(@Advice.This Executor thiz,
                                      @Nullable @Advice.Thrown Exception exception,
                                      @Nullable @Advice.Argument(value = 0, readOnly = false) Runnable runnable,
                                      @Advice.Local("original") @Nullable Runnable original) {

            try {
                if (original != null && (exception instanceof ClassCastException || exception instanceof IllegalArgumentException)) {
                    // seems like this executor expects a specific subtype of Callable
                    runnable = original;
                    // repeat only if submitting a task fails for the first time
                    return excluded.add(thiz);
                } else {
                    // don't repeat on exceptions which don't seem to be caused by wrapping the runnable
                    return false;
                }
            } finally {
                if (tracer != null) {
                    tracer.allowWrappingOnThread();
                }
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
        @SuppressWarnings("Duplicates")
        @Advice.OnMethodEnter(suppress = Throwable.class)
        public static void onSubmit(@Advice.This Executor thiz,
                                    @Advice.Argument(value = 0, readOnly = false) @Nullable Callable<?> callable,
                                    @Advice.Local("original") Callable original) {
            final TraceContextHolder<?> active = ExecutorInstrumentation.getActive();
            if (active != null && callable != null && !isExcluded(thiz) && tracer != null && tracer.isWrappingAllowedOnThread()) {
                original = callable;
                // Do no discard branches leading to async operations so not to break span references
                active.setDiscard(false);
                callable = active.withActive(callable);
                tracer.avoidWrappingOnThread();
            }
        }

        // This advice detects if the Executor can't cope with our wrappers
        // If so, it retries without the wrapper and adds it to a list of excluded Executor instances
        // which disables context propagation for those
        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Exception.class, repeatOn = Advice.OnNonDefaultValue.class)
        public static boolean onError(@Advice.This Executor thiz,
                                      @Nullable @Advice.Thrown Exception exception,
                                      @Nullable @Advice.Argument(value = 0, readOnly = false) Callable callable,
                                      @Advice.Local("original") Callable original) {
            try {
                if (exception instanceof ClassCastException || exception instanceof IllegalArgumentException) {
                    // seems like this executor expects a specific subtype of Callable
                    callable = original;
                    // repeat only if submitting a task fails for the first time
                    return excluded.add(thiz);
                } else {
                    // don't repeat on exceptions which don't seem to be caused by wrapping the runnable
                    return false;
                }
            } finally {
                if (tracer != null) {
                    tracer.allowWrappingOnThread();
                }
            }
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("submit").and(returns(Future.class)).and(takesArguments(Callable.class));
        }

    }

}
