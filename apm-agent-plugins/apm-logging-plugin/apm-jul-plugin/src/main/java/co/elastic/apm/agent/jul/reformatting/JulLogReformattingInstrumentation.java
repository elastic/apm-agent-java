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
package co.elastic.apm.agent.jul.reformatting;

import co.elastic.apm.agent.loginstr.AbstractLogIntegrationInstrumentation;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import java.util.Collection;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

import static net.bytebuddy.matcher.ElementMatchers.isBootstrapClassLoader;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

public abstract class JulLogReformattingInstrumentation extends AbstractLogIntegrationInstrumentation {

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        Collection<String> ret = super.getInstrumentationGroupNames();
        ret.add("jul-ecs");
        return ret;
    }

    @Override
    public ElementMatcher.Junction<ClassLoader> getClassLoaderMatcher() {
        // todo: change when adding support for instrumentation of Tomcat and JBoss logging
        return isBootstrapClassLoader();
    }

    /**
     * Instruments {@link java.util.logging.FileHandler#publish(LogRecord)}
     */
    public static class FileReformattingInstrumentation extends JulLogReformattingInstrumentation {

        @Override
        public ElementMatcher<? super TypeDescription> getTypeMatcher() {
            return named("java.util.logging.FileHandler");
        }

        /**
         * Instrumenting {@link java.util.logging.FileHandler#publish(LogRecord)}
         */
        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("publish").and(takesArgument(0, named("java.util.logging.LogRecord")));
        }

        @Override
        public String getAdviceClassName() {
            return "co.elastic.apm.agent.jul.reformatting.JulFileHandlerPublishAdvice";
        }
    }

    /**
     * Instruments {@link java.util.logging.ConsoleHandler#publish(LogRecord)}
     */
    public static class ConsoleReformattingInstrumentation extends JulLogReformattingInstrumentation {

        @Override
        public ElementMatcher<? super TypeDescription> getTypeMatcher() {
            return named("java.util.logging.ConsoleHandler");
        }

        /**
         * Instrumenting {@link java.util.logging.ConsoleHandler#publish(LogRecord)}
         */
        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("publish").and(takesArgument(0, named("java.util.logging.LogRecord")));
        }

        @Override
        public String getAdviceClassName() {
            return "co.elastic.apm.agent.jul.reformatting.JulConsoleHandlerPublishAdvice";
        }
    }

    public static class StopAppenderInstrumentation extends JulLogReformattingInstrumentation {

        @Override
        public ElementMatcher<? super TypeDescription> getTypeMatcher() {
            return named("java.util.logging.ConsoleHandler").or(named("java.util.logging.FileHandler"));
        }

        /**
         * Instrumenting {@link Handler#close()}
         */
        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("close").and(takesArguments(0));
        }

        @Override
        public String getAdviceClassName() {
            return "co.elastic.apm.agent.jul.reformatting.JulHandlerCloseAdvice";
        }
    }
}
