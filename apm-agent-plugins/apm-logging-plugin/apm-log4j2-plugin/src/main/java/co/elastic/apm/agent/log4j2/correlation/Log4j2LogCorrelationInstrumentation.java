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
package co.elastic.apm.agent.log4j2.correlation;

import co.elastic.apm.agent.bci.TracerAwareInstrumentation;
import co.elastic.apm.agent.bci.bytebuddy.CustomElementMatchers;
import co.elastic.apm.agent.loginstr.AbstractLogIntegrationInstrumentation;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import java.security.ProtectionDomain;
import java.util.Collection;
import java.util.Collections;

import static co.elastic.apm.agent.bci.bytebuddy.CustomElementMatchers.classLoaderCanLoadClass;
import static co.elastic.apm.agent.bci.bytebuddy.CustomElementMatchers.implementationVersionGte;
import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isBootstrapClassLoader;
import static net.bytebuddy.matcher.ElementMatchers.nameEndsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;

/**
 * Instruments {@link org.apache.logging.log4j.core.impl.LogEventFactory#createEvent}
 */
public abstract class Log4j2LogCorrelationInstrumentation extends AbstractLogIntegrationInstrumentation {

    @Override
    protected String getLoggingInstrumentationGroupName() {
        return LOG_CORRELATION;
    }

    @Override
    public ElementMatcher.Junction<ClassLoader> getClassLoaderMatcher() {
        return not(isBootstrapClassLoader())
            // Do not instrument the agent log4j2 lib
            .and(not(CustomElementMatchers.isAgentClassLoader()))
            .and(classLoaderCanLoadClass("org.apache.logging.log4j.core.impl.LogEventFactory"));
    }

    @Override
    public ElementMatcher<? super NamedElement> getTypeMatcherPreFilter() {
        return nameEndsWith("LogEventFactory");
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return hasSuperType(
            named("org.apache.logging.log4j.core.impl.LogEventFactory")
                .or(named("org.apache.logging.log4j.core.impl.LocationAwareLogEventFactory"))
        );
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("createEvent");
    }

    public static class Log4J2_6LogCorrelationInstrumentation extends Log4j2LogCorrelationInstrumentation {
        @Override
        public ElementMatcher.Junction<ProtectionDomain> getProtectionDomainPostFilter() {
            return implementationVersionGte("2.6").and(not(implementationVersionGte("2.7")));
        }

        public static class AdviceClass {
            private static final Log4j2_6LogCorrelationHelper helper = new Log4j2_6LogCorrelationHelper();

            @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
            public static boolean addToThreadContext() {
                return helper.beforeLoggingEvent();
            }

            @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
            public static void removeFromThreadContext(@Advice.Enter boolean addedToMdc) {
                helper.afterLoggingEvent(addedToMdc);
            }
        }
    }

    public static class Log4J2_7PlusLogCorrelationInstrumentation extends Log4j2LogCorrelationInstrumentation {
        @Override
        public ElementMatcher.Junction<ProtectionDomain> getProtectionDomainPostFilter() {
            return implementationVersionGte("2.7");
        }

        public static class AdviceClass {

            private static final Log4j2_7PlusLogCorrelationHelper helper = new Log4j2_7PlusLogCorrelationHelper();

            @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
            public static boolean addToThreadContext() {
                return helper.beforeLoggingEvent();
            }

            @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
            public static void removeFromThreadContext(@Advice.Enter boolean addedToThreadContext) {
                helper.afterLoggingEvent(addedToThreadContext);
            }
        }
    }
}
