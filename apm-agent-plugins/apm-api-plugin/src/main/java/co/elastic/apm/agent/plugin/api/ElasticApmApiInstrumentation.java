/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2020 Elastic and contributors
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
package co.elastic.apm.agent.plugin.api;

import co.elastic.apm.agent.bci.VisibleForAdvice;
import co.elastic.apm.agent.bci.bytebuddy.postprocessor.AssignTo;
import co.elastic.apm.agent.impl.transaction.Transaction;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;
import java.lang.invoke.MethodHandle;

import static co.elastic.apm.agent.plugin.api.Utils.FRAMEWORK_NAME;
import static net.bytebuddy.matcher.ElementMatchers.named;

/**
 * Injects the actual implementation of the public API class co.elastic.apm.api.ElasticApm.
 */
public class ElasticApmApiInstrumentation extends ApiInstrumentation {

    static final String PUBLIC_API_INSTRUMENTATION_GROUP = "public-api";
    private final ElementMatcher<? super MethodDescription> methodMatcher;

    ElasticApmApiInstrumentation(ElementMatcher<? super MethodDescription> methodMatcher) {
        this.methodMatcher = methodMatcher;
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("co.elastic.apm.api.ElasticApm");
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return methodMatcher;
    }

    public static class StartTransactionInstrumentation extends ElasticApmApiInstrumentation {
        public StartTransactionInstrumentation() {
            super(named("doStartTransaction"));
        }

        @Nullable
        @AssignTo.Return
        @VisibleForAdvice
        @Advice.OnMethodExit(suppress = Throwable.class)
        public static Object doStartTransaction(@Advice.Origin Class<?> clazz) {
            Transaction transaction = tracer.startRootTransaction(clazz.getClassLoader());
            if (transaction != null) {
                transaction.setFrameworkName(FRAMEWORK_NAME);
            }
            return transaction;
        }
    }

    public static class StartTransactionWithRemoteParentInstrumentation extends ElasticApmApiInstrumentation {

        public StartTransactionWithRemoteParentInstrumentation() {
            super(named("doStartTransactionWithRemoteParentFunction"));
        }

        @Nullable
        @AssignTo.Return
        @SuppressWarnings({"UnusedAssignment", "ParameterCanBeLocal", "unused"})
        @VisibleForAdvice
        @Advice.OnMethodExit(suppress = Throwable.class)
        public static Transaction doStartTransaction(@Advice.Origin Class<?> clazz,
                                                     @Advice.Argument(0) MethodHandle getFirstHeader,
                                                     @Advice.Argument(1) @Nullable Object headerExtractor,
                                                     @Advice.Argument(2) MethodHandle getAllHeaders,
                                                     @Advice.Argument(3) @Nullable Object headersExtractor) {
            Transaction transaction = null;
            if (headersExtractor != null) {
                HeadersExtractorBridge headersExtractorBridge = HeadersExtractorBridge.get(getFirstHeader, getAllHeaders);
                transaction = tracer.startChildTransaction(HeadersExtractorBridge.Extractor.of(headerExtractor, headersExtractor), headersExtractorBridge, clazz.getClassLoader());
            } else if (headerExtractor != null) {
                HeaderExtractorBridge headersExtractorBridge = HeaderExtractorBridge.get(getFirstHeader);
                transaction = tracer.startChildTransaction(headerExtractor, headersExtractorBridge, clazz.getClassLoader());
            } else {
                transaction = tracer.startRootTransaction(clazz.getClassLoader());
            }
            if (transaction != null) {
                transaction.setFrameworkName(FRAMEWORK_NAME);
            }
            return transaction;
        }
    }

    public static class CurrentTransactionInstrumentation extends ElasticApmApiInstrumentation {
        public CurrentTransactionInstrumentation() {
            super(named("doGetCurrentTransaction"));
        }

        @Nullable
        @AssignTo.Return
        @VisibleForAdvice
        @Advice.OnMethodExit(suppress = Throwable.class)
        public static Object doGetCurrentTransaction() {
            return tracer.currentTransaction();
        }
    }

    public static class CurrentSpanInstrumentation extends ElasticApmApiInstrumentation {
        public CurrentSpanInstrumentation() {
            super(named("doGetCurrentSpan"));
        }

        @Nullable
        @AssignTo.Return
        @VisibleForAdvice
        @Advice.OnMethodExit(suppress = Throwable.class)
        public static Object doGetCurrentSpan() {
            return tracer.getActive();
        }
    }

    public static class CaptureExceptionInstrumentation extends ElasticApmApiInstrumentation {
        public CaptureExceptionInstrumentation() {
            super(named("captureException"));
        }

        @VisibleForAdvice
        @Advice.OnMethodEnter(suppress = Throwable.class)
        public static void captureException(@Advice.Origin Class<?> clazz, @Advice.Argument(0) @Nullable Throwable e) {
            tracer.captureAndReportException(e, clazz.getClassLoader());
        }
    }

}
