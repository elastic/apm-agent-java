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

import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.ForkJoinTask;

import static net.bytebuddy.matcher.ElementMatchers.is;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;

/**
 * Instruments {@link ForkJoinTask#fork()} to support parallel streams.
 */
public class ForkJoinTaskInstrumentation extends AbstractJavaConcurrentInstrumentation {

    static {
        if (Boolean.parseBoolean(System.getProperty("intellij.debug.agent"))) {
            // InteliJ debugger also instrument some java.util.concurrent classes and changes the class structure.
            // However, the changes are not re-applied when re-transforming already loaded classes, which makes our
            // agent unable to see those structural changes and try to load classes with their original bytecode
            //
            // Go to the following to enable/disable: File | Settings | Build, Execution, Deployment | Debugger | Async Stack Traces
            throw new IllegalStateException("InteliJ debug agent detected, disable it to prevent unexpected instrumentation errors. See https://github.com/elastic/apm-agent-java/issues/1673");
        }
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return is(ForkJoinTask.class);
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("fork").and(returns(ForkJoinTask.class));
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Arrays.asList("concurrent", "fork-join");
    }

    public static class AdviceClass {
        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static void onExecute(@Advice.This ForkJoinTask<?> thiz) {
            JavaConcurrent.withContext(thiz, tracer);
        }

        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
        public static void onExit(@Nullable @Advice.Thrown Throwable thrown,
                                  @Advice.This ForkJoinTask<?> thiz) {
            JavaConcurrent.doFinally(thrown, thiz);
        }
    }
}
