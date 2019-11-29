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
package co.elastic.apm.agent.process;

import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import static net.bytebuddy.matcher.ElementMatchers.named;

/**
 * Instruments:
 * <ul>
 *     <li>{@code ProcessImpl#waitFor()}</li>
 *     <li>{@code ProcessImpl#wwaitFor(long, java.util.concurrent.TimeUnit)}</li>
 *     <li>{@code UNIXProcess#waitFor()}</li>
 *     <li>{@code UNIXProcess#waitFor(long, java.util.concurrent.TimeUnit)}</li>
 * </ul>
 */
public class ProcessExitInstrumentation extends BaseProcessInstrumentation {

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        // on JDK 7-8
        // Windows : ProcessImpl extends Process
        // Unix/Linux : UNIXProcess extends Process, ProcessImpl does not
        // on JDK 9 and beyond
        // All platforms: ProcessImpl extends Process
        return named("java.lang.ProcessImpl")
            .or(named("java.lang.UNIXProcess"));
    }

    // TODO : ProcessHandle added in java9, not supported yet

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        // will match both variants : with and without timeout
        return named("waitFor");
    }

    @Override
    public Class<?> getAdviceClass() {
        return ProcessImplWaitForAdvice.class;
    }

    public static class ProcessImplWaitForAdvice {

        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
        private static void onExit(@Advice.This Process process) {

            if (tracer == null || tracer.getActive() == null) {
                return;
            }

            ProcessHelper.waitForEnd(process);
        }
    }

}
