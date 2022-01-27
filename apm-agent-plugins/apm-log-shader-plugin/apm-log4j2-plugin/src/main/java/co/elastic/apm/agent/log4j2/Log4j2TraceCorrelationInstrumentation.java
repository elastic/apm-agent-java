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

import co.elastic.apm.agent.log.shader.AbstractLogCorrelationInstrumentation;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import java.util.Collection;
import java.util.Collections;

import static co.elastic.apm.agent.bci.bytebuddy.CustomElementMatchers.classLoaderCanLoadClass;
import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.nameContains;
import static net.bytebuddy.matcher.ElementMatchers.named;

public class Log4j2TraceCorrelationInstrumentation extends AbstractLogCorrelationInstrumentation {

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Collections.singleton("log4j2-correlation");
    }

    @Override
    public ElementMatcher.Junction<ClassLoader> getClassLoaderMatcher() {
        return super.getClassLoaderMatcher().and(classLoaderCanLoadClass("org.apache.logging.log4j.Logger"));
    }

    @Override
    public ElementMatcher<? super NamedElement> getTypeMatcherPreFilter() {
        return nameContains("Logger");
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return hasSuperType(named("org.apache.logging.log4j.Logger"));
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("fatal").or(
            named("catching").or(
                super.getMethodMatcher())
        );
    }

    public static class AdviceClass {

        private static final Log4j2LogCorrelationHelper helper = new Log4j2LogCorrelationHelper();

        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static boolean addToThreadContext() {
            return helper.beforeLoggingApiCall(tracer.currentTransaction());
        }

        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
        public static void removeFromThreadContext(@Advice.Enter boolean addedToThreadContext) {
            helper.afterLoggingApi(addedToThreadContext);
        }
    }
}
