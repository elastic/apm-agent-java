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
package co.elastic.apm.agent.opentracingimpl;

import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import net.bytebuddy.matcher.ElementMatcher;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

import static net.bytebuddy.matcher.ElementMatchers.named;

public abstract class SpanContextInstrumentation extends OpenTracingBridgeInstrumentation {

    public static final Logger logger = LoggerFactory.getLogger(SpanContextInstrumentation.class);

    private final ElementMatcher<? super MethodDescription> methodMatcher;

    public SpanContextInstrumentation(ElementMatcher<? super MethodDescription> methodMatcher) {
        this.methodMatcher = methodMatcher;
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("co.elastic.apm.opentracing.TraceContextSpanContext");
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return methodMatcher;
    }


    public static class BaggageItemsInstrumentation extends SpanContextInstrumentation {

        public BaggageItemsInstrumentation() {
            super(named("baggageItems"));
        }

        public static class AdviceClass {
            @Nullable
            @Advice.AssignReturned.ToReturned
            @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
            public static Iterable<Map.Entry<String, String>> baggageItems(@Advice.FieldValue(value = "traceContext", typing = Assigner.Typing.DYNAMIC) @Nullable Object context) {
                if (context instanceof AbstractSpan<?>) {
                    return doGetBaggage((AbstractSpan<?>) context);
                } else {
                    logger.info("The traceContext is null");
                    return null;
                }
            }

            public static Iterable<Map.Entry<String, String>> doGetBaggage(AbstractSpan<?> traceContext) {
                Map<String, String> baggage = new HashMap<String, String>();
                traceContext.propagateTraceContext(baggage, OpenTracingTextMapBridge.instance());
                return baggage.entrySet();
            }
        }
    }

    public static class ToTraceIdInstrumentation extends SpanContextInstrumentation {

        public ToTraceIdInstrumentation() {
            super(named("toTraceId"));
        }

        public static class AdviceClass {
            @Nullable
            @Advice.AssignReturned.ToReturned
            @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
            public static String toTraceId(@Advice.FieldValue(value = "traceContext", typing = Assigner.Typing.DYNAMIC) @Nullable Object context) {
                if (!(context instanceof AbstractSpan<?>)) {
                    return null;
                }
                AbstractSpan<?> traceContext = (AbstractSpan<?>) context;
                return traceContext.getTraceContext().getTraceId().toString();
            }
        }
    }

    public static class ToSpanIdInstrumentation extends SpanContextInstrumentation {

        public ToSpanIdInstrumentation() {
            super(named("toSpanId"));
        }

        public static class AdviceClass {
            @Nullable
            @Advice.AssignReturned.ToReturned
            @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
            public static String toTraceId(@Advice.FieldValue(value = "traceContext", typing = Assigner.Typing.DYNAMIC) @Nullable Object context) {
                if (!(context instanceof AbstractSpan<?>)) {
                    return null;
                }
                AbstractSpan<?> traceContext = (AbstractSpan<?>) context;
                return traceContext.getTraceContext().getId().toString();
            }
        }
    }
}
