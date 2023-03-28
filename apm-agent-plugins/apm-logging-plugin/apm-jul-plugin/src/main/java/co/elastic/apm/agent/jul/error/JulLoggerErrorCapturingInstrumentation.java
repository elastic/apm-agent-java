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
package co.elastic.apm.agent.jul.error;

import co.elastic.apm.agent.impl.Tracer;
import co.elastic.apm.agent.loginstr.error.AbstractLoggerErrorCapturingInstrumentation;
import co.elastic.apm.agent.loginstr.error.LoggerErrorHelper;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;
import java.util.logging.LogRecord;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

public class JulLoggerErrorCapturingInstrumentation extends AbstractLoggerErrorCapturingInstrumentation {

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("java.util.logging.Logger");
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("log")
            .and(takesArgument(0, named("java.util.logging.LogRecord")));
    }

    @Override
    public String getAdviceClassName() {
        return "co.elastic.apm.agent.jul.error.JulLoggerErrorCapturingInstrumentation$LoggingAdvice";
    }

    public static class LoggingAdvice {

        private static final LoggerErrorHelper helper = new LoggerErrorHelper(LoggingAdvice.class, tracer);

        @Nullable
        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static Object onEnter(@Advice.Argument(0) LogRecord record, @Advice.Origin Class<?> clazz) {
            Throwable thrown = record.getThrown();

            // ignore levels < SEVERE
            if (record.getLevel().intValue() < 1000) {
                thrown = null;
            }

            return helper.enter(thrown, clazz);
        }

        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
        public static void onExit(@Advice.Enter @Nullable Object errorCaptureObj) {
            helper.exit(errorCaptureObj);
        }
    }


}
