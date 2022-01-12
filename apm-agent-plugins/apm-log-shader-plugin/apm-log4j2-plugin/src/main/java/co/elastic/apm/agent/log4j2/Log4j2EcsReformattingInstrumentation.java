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
package co.elastic.apm.agent.log4j2;

import co.elastic.apm.agent.bci.bytebuddy.CustomElementMatchers;
import co.elastic.apm.agent.log.shader.AbstractLogShadingInstrumentation;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import java.util.Collection;

import static co.elastic.apm.agent.bci.bytebuddy.CustomElementMatchers.classLoaderCanLoadClass;
import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isBootstrapClassLoader;
import static net.bytebuddy.matcher.ElementMatchers.nameContains;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

public abstract class Log4j2EcsReformattingInstrumentation extends AbstractLogShadingInstrumentation {

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        Collection<String> ret = super.getInstrumentationGroupNames();
        ret.add("log4j2-ecs");
        return ret;
    }

    @Override
    public ElementMatcher.Junction<ClassLoader> getClassLoaderMatcher() {
        return not(isBootstrapClassLoader())
            .and(not(CustomElementMatchers.isAgentClassLoader()))
            .and(classLoaderCanLoadClass("org.apache.logging.log4j.core.Appender"));
    }

    @Override
    public ElementMatcher<? super NamedElement> getTypeMatcherPreFilter() {
        return nameContains("Appender");
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return hasSuperType(named("org.apache.logging.log4j.core.Appender"));
    }

    public static class ShadingInstrumentation extends Log4j2EcsReformattingInstrumentation {

        /**
         * Instrumenting {@link org.apache.logging.log4j.core.Appender#append(org.apache.logging.log4j.core.LogEvent)} implementations
         */
        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("append").and(takesArgument(0, named("org.apache.logging.log4j.core.LogEvent")));
        }

        @Override
        public String getAdviceClassName() {
            return "co.elastic.apm.agent.log4j2.Log4j2AppenderAppendAdvice";
        }

    }

    public static class StopAppenderInstrumentation extends Log4j2EcsReformattingInstrumentation {

        /**
         * Instrumenting {@link org.apache.logging.log4j.core.Appender#stop()} implementations
         */
        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("stop");
        }

        @Override
        public String getAdviceClassName() {
            return "co.elastic.apm.agent.log4j2.Log4j2AppenderStopAdvice";
        }

    }

    public static class OverridingInstrumentation extends Log4j2EcsReformattingInstrumentation {

        /**
         * Instrumenting {@link org.apache.logging.log4j.core.appender.AbstractAppender#getLayout()}
         */
        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("getLayout").and(returns(hasSuperType(named("org.apache.logging.log4j.core.Layout"))));
        }

        @Override
        public String getAdviceClassName() {
            return "co.elastic.apm.agent.log4j2.Log4j2AppenderGetLayoutAdvice";
        }
    }
}
