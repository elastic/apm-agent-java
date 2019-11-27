/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2019 Elastic and contributors
 * %%
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
 * #L%
 */
package co.elastic.apm.agent.error.logging;

import co.elastic.apm.agent.bci.ElasticApmInstrumentation;
import co.elastic.apm.agent.bci.VisibleForAdvice;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;

import java.util.ArrayList;
import java.util.Collection;

import static net.bytebuddy.matcher.ElementMatchers.nameContains;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

public abstract class AbstractLoggingInstrumentation extends ElasticApmInstrumentation {

    @SuppressWarnings({"WeakerAccess", "AnonymousHasLambdaAlternative"})
    @VisibleForAdvice
    public static final ThreadLocal<Boolean> nestedThreadLocal = new ThreadLocal<Boolean>() {
        @Override
        protected Boolean initialValue() {
            return Boolean.FALSE;
        }
    };

    @Override
    public Class<?> getAdviceClass() {
        return LoggingAdvice.class;
    }

    public static class LoggingAdvice {

        @Advice.OnMethodEnter(suppress = Throwable.class)
        public static void logEnter(@Advice.Argument(1) Throwable exception, @Advice.Local("nested") boolean nested) {
            if (tracer == null || tracer.getActive() == null) {
                return;
            }
            nested = nestedThreadLocal.get();
            if (!nested) {
                tracer.getActive().captureException(exception);
                nestedThreadLocal.set(Boolean.TRUE);
            }
        }

        @Advice.OnMethodExit(suppress = Throwable.class)
        public static void logExit(@Advice.Local("nested") boolean nested) {
            if (!nested) {
                nestedThreadLocal.set(Boolean.FALSE);
            }
        }
    }

    @Override
    public ElementMatcher<? super NamedElement> getTypeMatcherPreFilter() {
        return nameContains("Logger");
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("error")
            .and(takesArgument(0, named("java.lang.String"))
                .and(takesArgument(1, named("java.lang.Throwable"))));
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        Collection<String> ret = new ArrayList<>();
        ret.add("logging");
        return ret;
    }
}
