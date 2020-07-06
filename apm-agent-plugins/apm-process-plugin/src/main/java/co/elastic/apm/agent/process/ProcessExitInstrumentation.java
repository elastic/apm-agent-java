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

import co.elastic.apm.agent.impl.GlobalTracer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;

public abstract class ProcessExitInstrumentation extends BaseProcessInstrumentation {

    // ProcessHandle added in java9, not supported yet, see issue #966

    @Override
    public final ElementMatcher<? super TypeDescription> getTypeMatcher() {
        // on JDK 7-8
        // Windows : ProcessImpl extends Process
        // Unix/Linux : UNIXProcess extends Process, ProcessImpl does not
        // on JDK 9 and beyond
        // All platforms: ProcessImpl extends Process
        return named("java.lang.ProcessImpl")
            .or(named("java.lang.UNIXProcess"));
    }

    /**
     * Instruments
     * <ul>
     *     <li>{@code ProcessImpl#waitFor()}</li>
     *     <li>{@code ProcessImpl#waitFor(long, java.util.concurrent.TimeUnit)}</li>
     *     <li>{@code UNIXProcess#waitFor()}</li>
     *     <li>{@code UNIXProcess#waitFor(long, java.util.concurrent.TimeUnit)}</li>
     * </ul>
     */
    public static class WaitFor extends ProcessExitInstrumentation {

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            // will match both variants : with and without timeout
            return named("waitFor");
        }

        @Override
        public Class<?> getAdviceClass() {
            return WaitForAdvice.class;
        }

        public static class WaitForAdvice {

            @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
            public static void onExit(@Advice.This Process process) {

                if (tracer.getActive() == null) {
                    return;
                }

                // waitFor should poll process termination if interrupted
                ProcessHelper.endProcess(process, true);
            }
        }
    }

    /**
     * Instruments
     * <ul>
     *     <li>{@code ProcessImpl#destroy}</li>
     *     <li>{@code ProcessImpl#destroyForcibly}</li>
     *     <li>{@code UNIXProcess#destroy}</li>
     *     <li>{@code UNIXProcess#destroyForcibly}</li>
     * </ul>
     */
    public static class Destroy extends ProcessExitInstrumentation {

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return isPublic()
                .and(named("destroy")
                    .or(named("destroyForcibly")));
        }

        @Override
        public Class<?> getAdviceClass() {
            return DestroyAdvice.class;
        }

        public static class DestroyAdvice {

            @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
            public static void onExit(@Advice.This Process process) {

                if (tracer.getActive() == null) {
                    return;
                }

                // because destroy will not terminate process immediately, we need to skip checking process termination
                ProcessHelper.endProcess(process, false);
            }
        }
    }

}
