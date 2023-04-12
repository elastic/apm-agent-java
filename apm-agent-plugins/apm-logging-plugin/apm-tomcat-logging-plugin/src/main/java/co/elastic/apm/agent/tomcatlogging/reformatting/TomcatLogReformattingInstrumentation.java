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
package co.elastic.apm.agent.tomcatlogging.reformatting;

import co.elastic.apm.agent.loginstr.AbstractLogIntegrationInstrumentation;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import java.util.logging.LogRecord;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

public abstract class TomcatLogReformattingInstrumentation extends AbstractLogIntegrationInstrumentation {

    @Override
    protected String getLoggingInstrumentationGroupName() {
        return LOG_REFORMATTING;
    }

    /**
     * Instruments {@link org.apache.juli.FileHandler#close()}
     */
    public static class StopAppenderInstrumentation extends TomcatLogReformattingInstrumentation {

        @Override
        public ElementMatcher<? super TypeDescription> getTypeMatcher() {
            return named("org.apache.juli.FileHandler");
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("close").and(takesArguments(0));
        }

        @Override
        public String getAdviceClassName() {
            return "co.elastic.apm.agent.tomcatlogging.reformatting.FileHandlerCloseAdvice";
        }
    }

    /**
     * Instruments {@link org.apache.juli.FileHandler#publish(LogRecord)}
     */
    public static class FileReformattingInstrumentation extends TomcatLogReformattingInstrumentation {

        @Override
        public ElementMatcher<? super TypeDescription> getTypeMatcher() {
            return named("org.apache.juli.FileHandler");
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("publish").and(takesArgument(0, named("java.util.logging.LogRecord")));
        }

        @Override
        public String getAdviceClassName() {
            return "co.elastic.apm.agent.tomcatlogging.reformatting.FileHandlerPublishAdvice";
        }

    }
}
