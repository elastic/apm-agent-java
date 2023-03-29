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
package co.elastic.apm.agent.log4j1.reformatting;

import co.elastic.apm.agent.loginstr.AbstractLogIntegrationInstrumentation;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import java.util.Collection;

import static co.elastic.apm.agent.bci.bytebuddy.CustomElementMatchers.classLoaderCanLoadClass;
import static net.bytebuddy.matcher.ElementMatchers.isBootstrapClassLoader;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

public abstract class Log4j1LogReformattingInstrumentation extends AbstractLogIntegrationInstrumentation {

    @Override
    protected String getLoggingInstrumentationGroupName() {
        return LOG_REFORMATTING;
    }

    @Override
    public ElementMatcher.Junction<ClassLoader> getClassLoaderMatcher() {
        return not(isBootstrapClassLoader())
            .and(classLoaderCanLoadClass("org.apache.log4j.WriterAppender"));
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("org.apache.log4j.WriterAppender");
    }

    public static class ReformattingInstrumentation extends Log4j1LogReformattingInstrumentation {

        /**
         * Instrumenting {@link org.apache.log4j.WriterAppender#subAppend(org.apache.log4j.spi.LoggingEvent)}
         */
        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("subAppend").and(takesArgument(0, named("org.apache.log4j.spi.LoggingEvent")));
        }

        @Override
        public String getAdviceClassName() {
            return "co.elastic.apm.agent.log4j1.reformatting.Log4j1AppenderAppendAdvice";
        }

    }

    public static class StopAppenderInstrumentation extends Log4j1LogReformattingInstrumentation {

        /**
         * Instrumenting {@link org.apache.log4j.WriterAppender#close()}
         */
        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("close").and(takesArguments(0));
        }

        @Override
        public String getAdviceClassName() {
            return "co.elastic.apm.agent.log4j1.reformatting.Log4j1AppenderStopAdvice";
        }

    }
}
