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
package co.elastic.apm.agent.log4j2.error;

import co.elastic.apm.agent.bci.bytebuddy.CustomElementMatchers;
import co.elastic.apm.agent.loginstr.error.AbstractLoggerErrorCapturingInstrumentation;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import java.util.Collection;

import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

/**
 * Instruments {@link org.apache.logging.log4j.Logger#error(String, Throwable)} and {@link org.apache.logging.log4j.Logger#fatal(Object, Throwable)}
 */
public class Log4j2LoggerErrorCapturingInstrumentation extends AbstractLoggerErrorCapturingInstrumentation {

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return hasSuperType(named(LOG4J2_LOGGER)).and(not(hasSuperType(named(SLF4J_LOGGER))));
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("fatal").and(takesArgument(1, named("java.lang.Throwable")))
            .or(super.getMethodMatcher());
    }

    @Override
    public ElementMatcher.Junction<ClassLoader> getClassLoaderMatcher() {
        // Do not instrument the internal agent log4j2 loggers
        return not(CustomElementMatchers.isAgentClassLoader());
    }
}
