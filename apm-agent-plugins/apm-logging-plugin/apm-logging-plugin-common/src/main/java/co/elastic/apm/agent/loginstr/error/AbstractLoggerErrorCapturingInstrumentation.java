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
package co.elastic.apm.agent.loginstr.error;

import co.elastic.apm.agent.loginstr.AbstractLogIntegrationInstrumentation;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;

import static net.bytebuddy.matcher.ElementMatchers.nameContains;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

public abstract class AbstractLoggerErrorCapturingInstrumentation extends AbstractLogIntegrationInstrumentation {

    public static final String SLF4J_LOGGER = "org.slf4j.Logger";
    public static final String LOG4J2_LOGGER = "org.apache.logging.log4j.Logger";

    @Override
    protected String getLoggingInstrumentationGroupName() {
        return LOG_ERROR;
    }

    @Override
    public String getAdviceClassName() {
        return "co.elastic.apm.agent.loginstr.error.AbstractLoggerErrorCapturingInstrumentation$LoggingAdvice";
    }

    public static class LoggingAdvice {

        private static final LoggerErrorHelper helper = new LoggerErrorHelper(LoggingAdvice.class, tracer);

        @Nullable
        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static Object logEnter(@Advice.Argument(1) Throwable exception, @Advice.Origin Class<?> clazz) {
            return helper.enter(exception, clazz);
        }

        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
        public static void logExit(@Advice.Enter @Nullable Object errorCaptureObj) {
            helper.exit(errorCaptureObj);
        }
    }

    @Override
    public ElementMatcher<? super NamedElement> getTypeMatcherPreFilter() {
        return nameContains("Logger");
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("error")
            .and(takesArgument(1, named("java.lang.Throwable")));
    }

}
