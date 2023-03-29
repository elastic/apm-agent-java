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
package co.elastic.apm.agent.log4j1.error;

import co.elastic.apm.agent.loginstr.error.AbstractLoggerErrorCapturingInstrumentation;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import java.util.Collection;

import static net.bytebuddy.matcher.ElementMatchers.any;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

/**
 * Instruments {@link org.apache.log4j.Category#error(Object, Throwable)} and {@link org.apache.log4j.Category#fatal(Object, Throwable)}
 */
public class Log4j1LoggerErrorCapturingInstrumentation extends AbstractLoggerErrorCapturingInstrumentation {

    @Override
    public ElementMatcher<? super NamedElement> getTypeMatcherPreFilter() {
        return any();
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("org.apache.log4j.Category");
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("fatal").and(takesArgument(1, named("java.lang.Throwable"))).or(super.getMethodMatcher());
    }
}
