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
package co.elastic.apm.agent.profiler;

import co.elastic.apm.agent.bci.ElasticApmInstrumentation;
import co.elastic.apm.agent.bci.VisibleForAdvice;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;
import java.lang.management.ThreadInfo;
import java.util.Collection;
import java.util.Collections;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

public class ThreadInfoStacktraceAccessor extends ElasticApmInstrumentation {

    private static final ThreadLocal<Boolean> nonClonedStackTrace = new ThreadLocal<Boolean>();

    static {
        nonClonedStackTrace.set(Boolean.FALSE);
    }

    @VisibleForAdvice
    public static Boolean getNonClonedStackTrace() {
        return nonClonedStackTrace.get();
    }

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class, skipOn = Advice.OnNonDefaultValue.class)
    private static StackTraceElement[] onEnter(@Advice.FieldValue("stackTrace") StackTraceElement[] stackTrace) {
        if (getNonClonedStackTrace()) {
            return stackTrace;
        } else {
            return null;
        }
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
    private static void onExit(@Nullable @Advice.Enter StackTraceElement[] stackTrace, @Advice.Return(readOnly = false) StackTraceElement[] result) {
        if (stackTrace != null) {
            result = stackTrace;
        }
    }

    /**
     * Gets the {@link ThreadInfo#stackTrace}.
     * In order to reduce allocations, avoids the cloning the {@link StackTraceElement}{@code []} which has been introduced with a Java 9 update.
     *
     * @param info the thread info
     * @return {@link ThreadInfo#stackTrace}
     */
    public static StackTraceElement[] getStackTrace(ThreadInfo info) {
        nonClonedStackTrace.set(Boolean.TRUE);
        try {
            return info.getStackTrace();
        } finally {
            nonClonedStackTrace.set(Boolean.FALSE);
        }
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return new ElementMatcher.Junction.AbstractBase<TypeDescription>() {
            @Override
            public boolean matches(TypeDescription target) {
                return named("java.lang.management.ThreadInfo").matches(target);
            }
        };
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("getStackTrace").and(takesArguments(0));
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Collections.singletonList("thread-info-stacktrace");
    }
}
