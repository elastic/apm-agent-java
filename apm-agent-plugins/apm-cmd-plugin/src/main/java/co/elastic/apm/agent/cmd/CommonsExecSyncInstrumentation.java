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
package co.elastic.apm.agent.cmd;

import java.util.Map;

import javax.annotation.Nullable;

import co.elastic.apm.agent.impl.transaction.Span;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.commons.exec.CommandLine;

import static co.elastic.apm.agent.bci.bytebuddy.CustomElementMatchers.classLoaderCanLoadClass;
import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isBootstrapClassLoader;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

// TODO : add links to the instrumented classes
// TODO : async part is not instrumented (yet)
public class CommonsExecSyncInstrumentation extends BinaryExecutionInstrumentation {

    @Override
    public ElementMatcher.Junction<ClassLoader> getClassLoaderMatcher() {
        return not(isBootstrapClassLoader())
            .and(classLoaderCanLoadClass("org.apache.commons.exec.Executor"));
        // java.lang is loaded by bootstrap classloader, thus excluded here
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return not(isInterface())
            .and(hasSuperType(named("org.apache.commons.exec.Executor")));
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        ElementMatcher.Junction<TypeDescription> cmdLine = named("org.apache.commons.exec.CommandLine");
        return named("execute").and(
            takesArguments(1).and(takesArgument(0, cmdLine))
                .or(takesArguments(2).and(takesArgument(0, cmdLine).and(takesArgument(1, Map.class))))
        );
    }

    // TODO : add getTypeMatcherPreFilter to filter out on class name/package otherwise
    // filtering on classes that implement an interface becomes costly

    @Override
    public Class<?> getAdviceClass() {
        return CommonsExecAdvice.class;
    }

    public static class CommonsExecAdvice {

        @Advice.OnMethodEnter(suppress = Throwable.class)
        private static void onBeforeExecute(@Advice.Argument(0) CommandLine commandLine,
                                            @Advice.Local("span") Span span) {

            // might be a bit better to not get arguments all the time as it allocates extra memory
            // it's not necessary when outside a transaction/span

            span = ExecuteHelper.createAndActivateSpan(tracer, commandLine.getExecutable(), commandLine.getArguments(), "commons-exec");
        }

        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
        public static void onAfterExecute(@Advice.Return int exitValue,
                                          @Advice.Local("span") @Nullable Span span,
                                          @Advice.Thrown Throwable t) {
            ExecuteHelper.endAndDeactivateSpan(span, t, exitValue);
        }
    }
}
