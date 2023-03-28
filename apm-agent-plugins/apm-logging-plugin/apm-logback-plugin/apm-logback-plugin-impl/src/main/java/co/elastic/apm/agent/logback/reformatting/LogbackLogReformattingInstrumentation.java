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
package co.elastic.apm.agent.logback.reformatting;

import co.elastic.apm.agent.loginstr.AbstractLogIntegrationInstrumentation;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import static co.elastic.apm.agent.bci.bytebuddy.CustomElementMatchers.classLoaderCanLoadClass;
import static net.bytebuddy.matcher.ElementMatchers.isBootstrapClassLoader;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;
import static net.bytebuddy.matcher.ElementMatchers.takesGenericArgument;

public abstract class LogbackLogReformattingInstrumentation extends AbstractLogIntegrationInstrumentation {

    @Override
    protected String getLoggingInstrumentationGroupName() {
        return LOG_REFORMATTING;
    }

    @Override
    public ElementMatcher.Junction<ClassLoader> getClassLoaderMatcher() {
        return not(isBootstrapClassLoader())
            .and(classLoaderCanLoadClass("ch.qos.logback.core.OutputStreamAppender"))
            .and(classLoaderCanLoadClass("ch.qos.logback.classic.LoggerContext"));
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("ch.qos.logback.core.OutputStreamAppender");
    }

    public static class ReformattingInstrumentation extends LogbackLogReformattingInstrumentation {

        /**
         * Instrumenting {@link ch.qos.logback.core.OutputStreamAppender#append(Object)}
         */
        @SuppressWarnings("JavadocReference")
        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("append").and(takesGenericArgument(0, TypeDescription.Generic.Builder.typeVariable("E").build()));
        }

        @Override
        public String getAdviceClassName() {
            return "co.elastic.apm.agent.logback.reformatting.LogbackAppenderAppendAdvice";
        }

    }

    public static class StopAppenderInstrumentation extends LogbackLogReformattingInstrumentation {

        /**
         * Instrumenting {@link ch.qos.logback.core.OutputStreamAppender#stop}
         */
        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("stop").and(takesArguments(0));
        }

        @Override
        public String getAdviceClassName() {
            return "co.elastic.apm.agent.logback.reformatting.LogbackAppenderStopAdvice";
        }

    }
}
