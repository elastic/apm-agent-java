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
package co.elastic.apm.agent.process;

import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

/**
 * Instruments {@link ProcessBuilder#start()}
 */
public class ProcessStartInstrumentation extends BaseProcessInstrumentation {

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("java.lang.ProcessBuilder");
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("start")
            .and(takesArguments(0));
    }

    @Override
    public String getAdviceClassName() {
        return "co.elastic.apm.agent.process.ProcessStartInstrumentation$ProcessBuilderStartAdvice";
    }

    public static class ProcessBuilderStartAdvice {

        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
        public static void onExit(@Advice.This ProcessBuilder processBuilder,
                                  @Advice.Return @Nullable Process process,
                                  @Advice.Thrown @Nullable Throwable t) {

            AbstractSpan<?> parentSpan = tracer.getActive();
            if (parentSpan == null) {
                return;
            }

            if (t != null) {
                // unable to start process, report exception as it's likely to be a bug
                parentSpan.captureException(t);
            }

            if (process != null) {
                // when an exception is thrown, there is no return value
                ProcessHelper.startProcess(parentSpan, process, processBuilder.command());
            }
        }
    }
}
