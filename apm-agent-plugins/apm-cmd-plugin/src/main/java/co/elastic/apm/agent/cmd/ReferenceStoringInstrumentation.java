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

import co.elastic.apm.agent.bci.VisibleForAdvice;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.TraceContextHolder;
import co.elastic.apm.agent.util.DataStructures;
import com.blogspot.mydailyjava.weaklockfree.WeakConcurrentMap;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecuteResultHandler;
import org.apache.commons.exec.ExecuteResultHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static co.elastic.apm.agent.bci.bytebuddy.CustomElementMatchers.classLoaderCanLoadClass;
import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isBootstrapClassLoader;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

public abstract class ReferenceStoringInstrumentation extends BinaryExecutionInstrumentation {

    @VisibleForAdvice
    public static final WeakConcurrentMap<Process, Span> inFlightSpans = DataStructures.createWeakConcurrentMapWithCleanerThread();

    @VisibleForAdvice
    public static final WeakConcurrentMap<ExecuteResultHandler, Span> inFlightSpansCommonsExec = DataStructures.createWeakConcurrentMapWithCleanerThread();

    public static class CommonsExecAsyncInstrumentation extends ReferenceStoringInstrumentation {

        private static final String EXECUTOR_CLASS_NAME = "org.apache.commons.exec.Executor";

        private static final String COMMAND_LINE_CLASS_NAME = "org.apache.commons.exec.CommandLine";

        private static final String EXECUTE_RESULT_HANDLER_CLASS_NAME = "org.apache.commons.exec.ExecuteResultHandler";

        @Advice.OnMethodEnter(suppress = Throwable.class)
        private static void onBeforeExecute(@Advice.AllArguments Object[] methodArguments,
                                            @Advice.Local("span") Span span) {
            if (methodArguments == null || methodArguments.length == 0) {
                return;
            }

            // CommandLine is always the first argument of the method
            final CommandLine commandLine = (CommandLine) methodArguments[0];
            // ExecuteResultHandler is always the last argument of the method
            final ExecuteResultHandler executeResultHandler = (ExecuteResultHandler) methodArguments[methodArguments.length - 1];

            span = ExecuteHelper.createAndActivateSpan(tracer, commandLine.getExecutable(), commandLine.getArguments(), "commons-exec");

            if (span != null) {
                inFlightSpansCommonsExec.put(executeResultHandler, span);
            }
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("execute")
                .and(
                    takesArguments(2)
                        .and(takesArgument(0, named(COMMAND_LINE_CLASS_NAME))
                            .and(takesArgument(1, named(EXECUTE_RESULT_HANDLER_CLASS_NAME)))
                        )
                        .or(
                            takesArguments(3)
                                .and(
                                    takesArgument(0, named(COMMAND_LINE_CLASS_NAME))
                                        .and(takesArgument(1, Map.class))
                                        .and(takesArgument(2, named(EXECUTE_RESULT_HANDLER_CLASS_NAME))))
                        )
                );
        }

        @Override
        public ElementMatcher.Junction<ClassLoader> getClassLoaderMatcher() {
            return not(isBootstrapClassLoader())
                .and(classLoaderCanLoadClass(EXECUTOR_CLASS_NAME));
        }

        @Override
        public ElementMatcher<? super TypeDescription> getTypeMatcher() {
            return not(isInterface())
                .and(hasSuperType(named(EXECUTOR_CLASS_NAME)));
        }
    }

    public static class EndCommonsExecAsyncInstrumentation extends ReferenceStoringInstrumentation {

        private static final String EXECUTE_RESULT_HANDLER_CLASS_NAME = "org.apache.commons.exec.ExecuteResultHandler";

        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
        public static void afterProcessEnds(@Advice.This ExecuteResultHandler executeResultHandler,
                                            @Nullable @Advice.Thrown Throwable t) {
            final Span span = inFlightSpansCommonsExec.remove(executeResultHandler);
            if (span != null) {
                Integer exitValue = null;
                if (executeResultHandler instanceof DefaultExecuteResultHandler) {
                    exitValue = ((DefaultExecuteResultHandler) executeResultHandler).getExitValue();
                }
                ExecuteHelper.endAndDeactivateSpan(span, t, exitValue);
            }
        }

        @Override
        public ElementMatcher.Junction<ClassLoader> getClassLoaderMatcher() {
            return not(isBootstrapClassLoader())
                .and(classLoaderCanLoadClass(EXECUTE_RESULT_HANDLER_CLASS_NAME));
        }

        @Override
        public ElementMatcher<? super TypeDescription> getTypeMatcher() {
            return not(isInterface())
                .and(hasSuperType(named(EXECUTE_RESULT_HANDLER_CLASS_NAME)));
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("waitFor");
        }
    }

    public static class JavaProcessBuilderApiInstrumentation extends ReferenceStoringInstrumentation {

        @VisibleForAdvice
        public static final Logger logger = LoggerFactory.getLogger(JavaProcessBuilderApiInstrumentation.class);

        private static final String PROCESS_BUILDER_CLASS_NAME = "java.lang.ProcessBuilder";

        @Override
        public ElementMatcher<? super TypeDescription> getTypeMatcher() {
            return not(isInterface())
                .and(named(PROCESS_BUILDER_CLASS_NAME));
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("start").and(takesArguments(0));
        }

        @Advice.OnMethodEnter(suppress = Throwable.class)
        private static void onBeforeExecute(@Advice.This ProcessBuilder processBuilder,
                                            @Advice.Return Process process,
                                            @Advice.Local("span") Span span,
                                            @Advice.Origin("#m") String methodName) {
            logger.info("Java Processbuilder api start");
            if (tracer != null) {
                TraceContextHolder<?> active = tracer.getActive();
                if (active == null) {
                    return;
                }

                // The first command is the binary. All other commands are program arguments
                final String[] processBuilderCommand = processBuilder.command().toArray(new String[0]);
                final String binaryName = processBuilderCommand[0];
                final String[] binaryArguments = new String[processBuilderCommand.length - 1];
                for (int i = 1; i < processBuilderCommand.length; i++) {
                    binaryArguments[i - 1] = processBuilderCommand[i];
                }
                span = ExecuteHelper.createAndActivateSpan(tracer, binaryName, binaryArguments, "java processbuilder api");

                if (span != null) {
                    inFlightSpans.put(process, span);
                }
            }
        }
    }

    public static class JavaRuntimeExecInstrumentation extends ReferenceStoringInstrumentation {

        @Override
        public ElementMatcher<? super TypeDescription> getTypeMatcher() {
            return named("java.lang.Runtime");
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("exec");
        }

        @Advice.OnMethodEnter(suppress = Throwable.class)
        private static void onBeforeExecute(@Advice.Return Process process,
                                            @Advice.Local("span") Span span,
                                            @Advice.Argument(value = 0) String command,
                                            @Advice.Origin("#m") String methodName) {
            if (command != null && !command.isEmpty()) {
                // The first element in the list should be the binary, all others are binary arguments
                final String[] commandList = command.split(" ");
                final String binaryName = commandList[0];
                final String[] binaryArguments = new String[commandList.length - 1];
                for (int i = 1; i < commandList.length; i++) {
                    binaryArguments[i - 1] = commandList[i];
                }

                span = ExecuteHelper.createAndActivateSpan(tracer, binaryName, binaryArguments, "java runtime api");

                if (span != null) {
                    inFlightSpans.put(process, span);
                }
            }
        }
    }

    public static class EndProcessInstrumentation extends ReferenceStoringInstrumentation {

        @VisibleForAdvice
        public static final Logger logger = LoggerFactory.getLogger(EndProcessInstrumentation.class);

        private static final String PROCESS_CLASS_NAME = "java.lang.Process";

        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
        public static void afterProcessEnds(@Advice.This Process process,
                                            @Advice.Return int exitValue,
                                            @Nullable @Advice.Thrown Throwable t) {
            logger.info("Process wait for end");
            final Span span = inFlightSpans.remove(process);
            if (span != null) {
                ExecuteHelper.endAndDeactivateSpan(span, t, exitValue);
            }
        }

        @Override
        public ElementMatcher.Junction<ClassLoader> getClassLoaderMatcher() {
            return not(isBootstrapClassLoader())
                .and(classLoaderCanLoadClass(PROCESS_CLASS_NAME));
        }

        @Override
        public ElementMatcher<? super TypeDescription> getTypeMatcher() {
            return not(isInterface())
                .and(hasSuperType(named(PROCESS_CLASS_NAME)));
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("waitFor");
        }
    }
}
