/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2019 Elastic and contributors
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package co.elastic.apm.agent.plugin.api;

import co.elastic.apm.agent.bci.VisibleForAdvice;
import co.elastic.apm.agent.impl.transaction.TraceContext;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;
import java.lang.invoke.MethodHandle;
import java.util.Iterator;

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

        @VisibleForAdvice
        @Advice.OnMethodExit(suppress = Throwable.class)
        private static void doStartTransaction(@Advice.Return(readOnly = false) Object transaction) {
            if (tracer != null) {
                transaction = tracer.startTransaction();
            }
        }
    }

    public static class StartTransactionWithRemoteParentInstrumentation extends ElasticApmApiInstrumentation {

        public StartTransactionWithRemoteParentInstrumentation() {
            super(named("doStartTransactionWithRemoteParentFunction"));
        }

        @VisibleForAdvice
        @Advice.OnMethodExit(suppress = Throwable.class)
        private static void doStartTransaction(@Advice.Return(readOnly = false) Object transaction,
                                               @Advice.Argument(0) MethodHandle getFirstHeader,
                                               @Advice.Argument(1) @Nullable Object headerExtractor,
                                               @Advice.Argument(2) MethodHandle getAllHeaders,
                                               @Advice.Argument(3) @Nullable Object headersExtractor) throws Throwable {
            if (tracer != null) {
                if (headerExtractor != null) {
                    final String traceparentHeader = (String) getFirstHeader.invoke(headerExtractor, TraceContext.TRACE_PARENT_HEADER);
                    transaction = tracer.startTransaction(TraceContext.fromTraceparentHeader(), traceparentHeader);
                } else if (headersExtractor != null) {
                    final Iterable<String> traceparentHeader = (Iterable<String>) getAllHeaders.invoke(headersExtractor, TraceContext.TRACE_PARENT_HEADER);
                    final Iterator<String> iterator = traceparentHeader.iterator();
                    if (iterator.hasNext()) {
                        transaction = tracer.startTransaction(TraceContext.fromTraceparentHeader(), iterator.next());
                    } else {
                        transaction = tracer.startTransaction();
                    }
                } else {
                    transaction = tracer.startTransaction();
                }
            }
        }
    }

    public static class CurrentTransactionInstrumentation extends ElasticApmApiInstrumentation {
        public CurrentTransactionInstrumentation() {
            super(named("doGetCurrentTransaction"));
        }

        @VisibleForAdvice
        @Advice.OnMethodExit(suppress = Throwable.class)
        private static void doGetCurrentTransaction(@Advice.Return(readOnly = false) Object transaction) {
            if (tracer != null) {
                transaction = tracer.currentTransaction();
            }
        }
    }

    public static class CurrentSpanInstrumentation extends ElasticApmApiInstrumentation {
        public CurrentSpanInstrumentation() {
            super(named("doGetCurrentSpan"));
        }

        @VisibleForAdvice
        @Advice.OnMethodExit(suppress = Throwable.class)
        private static void doGetCurrentSpan(@Advice.Return(readOnly = false) Object span) {
            if (tracer != null) {
                span = tracer.activeSpan();
            }
        }
    }

    public static class CaptureExceptionInstrumentation extends ElasticApmApiInstrumentation {
        public CaptureExceptionInstrumentation() {
            super(named("captureException"));
        }

        @VisibleForAdvice
        @Advice.OnMethodEnter(suppress = Throwable.class)
        private static void captureException(@Advice.Argument(0) @Nullable Throwable e) {
            if (tracer != null) {
                tracer.captureException(e);
            }
        }
    }

}
