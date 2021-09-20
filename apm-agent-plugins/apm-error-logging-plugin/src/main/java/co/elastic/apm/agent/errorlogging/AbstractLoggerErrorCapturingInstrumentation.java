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
package co.elastic.apm.agent.errorlogging;

import co.elastic.apm.agent.bci.TracerAwareInstrumentation;
import co.elastic.apm.agent.impl.error.ErrorCapture;
import co.elastic.apm.agent.sdk.state.CallDepth;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;

import static net.bytebuddy.matcher.ElementMatchers.nameContains;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.ofType;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

public abstract class AbstractLoggerErrorCapturingInstrumentation extends TracerAwareInstrumentation {

    @Override
    public String getAdviceClassName() {
        return "co.elastic.apm.agent.errorlogging.AbstractLoggerErrorCapturingInstrumentation$LoggingAdvice";
    }

    public static class LoggingAdvice {

        private static final CallDepth callDepth = CallDepth.get(LoggingAdvice.class);

        @Nullable
        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static Object logEnter(@Advice.Argument(1) Throwable exception,
                                        @Advice.Origin Class<?> clazz) {
            if (!callDepth.isNestedCallAndIncrement()) {
                ErrorCapture error = tracer.captureException(exception, tracer.getActive(), clazz.getClassLoader());
                if (error != null) {
                    error.activate();
                }
                return error;
            }
            return null;
        }

        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
        public static void logExit(@Advice.Enter @Nullable Object errorCaptureObj) {
            callDepth.decrement();
            if (errorCaptureObj instanceof ErrorCapture) {
                ErrorCapture error = (ErrorCapture) errorCaptureObj;
                error.deactivate().end();
            }
        }
    }

    @Override
    public ElementMatcher<? super NamedElement> getTypeMatcherPreFilter() {
        return nameContains("Logger");
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("error")
            .and(takesArgument(0, named("java.lang.String"))
                .and(takesArgument(1, named("java.lang.Throwable"))));
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        Collection<String> ret = new ArrayList<>();
        ret.add("logging");
        return ret;
    }

    @Override
    public ElementMatcher.Junction<ClassLoader> getClassLoaderMatcher() {
        return not(ofType(nameStartsWith("co.elastic.apm.")));
    }
}
