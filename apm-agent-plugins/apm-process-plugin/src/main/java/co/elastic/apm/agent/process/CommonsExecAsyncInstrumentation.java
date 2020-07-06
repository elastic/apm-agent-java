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
package co.elastic.apm.agent.process;

import co.elastic.apm.agent.bci.TracerAwareInstrumentation;
import co.elastic.apm.agent.bci.bytebuddy.postprocessor.AssignTo;
import co.elastic.apm.agent.concurrent.JavaConcurrent;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collection;

import static net.bytebuddy.matcher.ElementMatchers.hasSuperClass;
import static net.bytebuddy.matcher.ElementMatchers.nameContains;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

/**
 * Provides context propagation for apache commons-exec library that delegates to a background thread for
 * asynchronous process execution. Synchronous execution is already covered with {@link Process} instrumentation.
 * <p>
 * Instruments {@code org.apache.commons.exec.DefaultExecutor#createThread(Runnable, String)} and any direct subclass
 * that overrides it.
 */
public class CommonsExecAsyncInstrumentation extends TracerAwareInstrumentation {

    private static final String DEFAULT_EXECUTOR_CLASS = "org.apache.commons.exec.DefaultExecutor";
    // only known subclass of default implementation
    private static final String DAEMON_EXECUTOR_CLASS = "org.apache.commons.exec.DaemonExecutor";

    @Override
    public ElementMatcher<? super NamedElement> getTypeMatcherPreFilter() {
        // Most implementations are likely to have 'Executor' in their name, which will work most of the time
        // while not perfect this allows to avoid the expensive 'hasSuperClass' in most cases
        return nameContains("Executor");
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        // instrument default implementation and direct subclasses
        return named(DEFAULT_EXECUTOR_CLASS)
            .or(named(DAEMON_EXECUTOR_CLASS))
            // this super class check is expensive
            .or(hasSuperClass(named(DEFAULT_EXECUTOR_CLASS)));
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("createThread")
            .and(takesArgument(0, Runnable.class));
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        // part of 'process' group, as usage is not relevant without it, relies on generic Process instrumentation
        return Arrays.asList("apache-commons-exec", "process");
    }

    @Override
    public Class<?> getAdviceClass() {
        return CommonsExecAdvice.class;
    }

    @Override
    public boolean indyPlugin() {
        return true;
    }

    public static final class CommonsExecAdvice {

        @Nullable
        @AssignTo.Argument(0)
        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static Runnable onEnter(Runnable runnable) {
            return JavaConcurrent.withContext(runnable, tracer);
        }

        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
        public static void onExit(@Advice.Thrown Throwable thrown,
                                  @Advice.Argument(value = 0) Runnable runnable) {
            JavaConcurrent.doFinally(thrown, runnable);
        }
    }
}
