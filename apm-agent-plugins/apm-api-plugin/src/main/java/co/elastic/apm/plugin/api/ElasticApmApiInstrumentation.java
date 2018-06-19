/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 Elastic and contributors
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
package co.elastic.apm.plugin.api;

import co.elastic.apm.bci.ElasticApmInstrumentation;
import co.elastic.apm.bci.VisibleForAdvice;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;

import static net.bytebuddy.matcher.ElementMatchers.named;

/**
 * Injects the actual implementation of the public API class co.elastic.apm.api.ElasticApm.
 */
public class ElasticApmApiInstrumentation extends ElasticApmInstrumentation {

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

    @Override
    public boolean includeWhenInstrumentationIsDisabled() {
        return true;
    }

    @Override
    public String getInstrumentationGroupName() {
        return PUBLIC_API_INSTRUMENTATION_GROUP;
    }

    public static class StartTransactionInstrumentation extends ElasticApmApiInstrumentation {
        public StartTransactionInstrumentation() {
            super(named("doStartTransaction"));
        }

        @VisibleForAdvice
        @Advice.OnMethodExit
        private static void doStartTransaction(@Advice.Return(readOnly = false) Object transaction) {
            if (tracer != null) {
                transaction = tracer.startTransaction();
            }
        }
    }

    public static class CurrentTransactionInstrumentation extends ElasticApmApiInstrumentation {
        public CurrentTransactionInstrumentation() {
            super(named("doGetCurrentTransaction"));
        }

        @VisibleForAdvice
        @Advice.OnMethodExit
        private static void doGetCurrentTransaction(@Advice.Return(readOnly = false) Object transaction) {
            if (tracer != null) {
                transaction = tracer.currentTransaction();
            }
        }
    }

    public static class StartSpanInstrumentation extends ElasticApmApiInstrumentation {
        public StartSpanInstrumentation() {
            super(named("doStartSpan"));
        }

        @VisibleForAdvice
        @Advice.OnMethodExit
        private static void doStartSpan(@Advice.Return(readOnly = false) Object span) {
            if (tracer != null) {
                span = tracer.startSpan();
            }
        }
    }

    public static class CurrentSpanInstrumentation extends ElasticApmApiInstrumentation {
        public CurrentSpanInstrumentation() {
            super(named("doGetCurrentSpan"));
        }

        @VisibleForAdvice
        @Advice.OnMethodExit
        private static void doGetCurrentSpan(@Advice.Return(readOnly = false) Object span) {
            if (tracer != null) {
                span = tracer.currentSpan();
            }
        }
    }

    public static class CaptureExceptionInstrumentation extends ElasticApmApiInstrumentation {
        public CaptureExceptionInstrumentation() {
            super(named("captureException"));
        }

        @VisibleForAdvice
        @Advice.OnMethodEnter
        private static void captureException(@Advice.Argument(0) @Nullable Exception e) {
            if (tracer != null) {
                tracer.captureException(e);
            }
        }
    }

}
