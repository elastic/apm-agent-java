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

import co.elastic.apm.agent.bci.TracerAwareInstrumentation;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.transaction.TraceContext;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned.ToFields.ToField;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;
import java.util.Map;

import static net.bytebuddy.matcher.ElementMatchers.named;

public abstract class ExternalSpanContextInstrumentation extends OpenTracingBridgeInstrumentation {

    private final ElementMatcher<? super MethodDescription> methodMatcher;

    public ExternalSpanContextInstrumentation(ElementMatcher<? super MethodDescription> methodMatcher) {
        this.methodMatcher = methodMatcher;
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("co.elastic.apm.opentracing.ExternalProcessSpanContext");
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return methodMatcher;
    }

    public static class ToTraceIdInstrumentation extends ExternalSpanContextInstrumentation {

        public ToTraceIdInstrumentation() {
            super(named("toTraceId"));
        }

        public static class AdviceClass {

            @Nullable
            @Advice.AssignReturned.ToFields(@ToField(value = "childTraceContext"))
            @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
            public static Object toTraceId(@Advice.FieldValue(value = "textMap", typing = Assigner.Typing.DYNAMIC) @Nullable Iterable<Map.Entry<String, String>> textMap,
                                           @Advice.FieldValue(value = "childTraceContext", typing = Assigner.Typing.DYNAMIC) @Nullable Object childTraceContextObj) {
                if (textMap == null) {
                    return childTraceContextObj;
                }
                return parseTextMap(textMap);

            }


            @Nullable
            @Advice.AssignReturned.ToReturned
            @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
            public static String onExit(@Advice.FieldValue(value = "childTraceContext", typing = Assigner.Typing.DYNAMIC) @Nullable Object childTraceContextObj) {
                if (!(childTraceContextObj instanceof TraceContext)) {
                    return null;
                }
                return ((TraceContext) childTraceContextObj).getTraceId().toString();
            }
        }
    }

    public static class ToSpanIdInstrumentation extends ExternalSpanContextInstrumentation {

        public ToSpanIdInstrumentation() {
            super(named("toSpanId"));
        }

        public static class AdviceClass {

            @Nullable
            @Advice.AssignReturned.ToFields(@ToField(value = "childTraceContext"))
            @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
            public static Object toSpanId(@Advice.FieldValue(value = "textMap", typing = Assigner.Typing.DYNAMIC) @Nullable Iterable<Map.Entry<String, String>> textMap,
                                          @Advice.FieldValue(value = "childTraceContext", typing = Assigner.Typing.DYNAMIC) @Nullable Object childTraceContextObj) {
                if (textMap == null) {
                    return childTraceContextObj;
                }
                return parseTextMap(textMap);
            }

            @Nullable
            @Advice.AssignReturned.ToReturned
            @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
            public static String onExit(@Advice.FieldValue(value = "childTraceContext", typing = Assigner.Typing.DYNAMIC) @Nullable Object childTraceContextObj) {
                if (!(childTraceContextObj instanceof TraceContext)) {
                    return null;
                }
                return ((TraceContext) childTraceContextObj).getParentId().toString();
            }
        }
    }

    @Nullable
    public static TraceContext parseTextMap(Iterable<Map.Entry<String, String>> textMap) {
        ElasticApmTracer tracer = TracerAwareInstrumentation.tracer.require(ElasticApmTracer.class);
        if (tracer != null) {
            TraceContext childTraceContext = TraceContext.with64BitId(tracer);
            if (TraceContext.<Iterable<Map.Entry<String, String>>>getFromTraceContextTextHeaders().asChildOf(childTraceContext, textMap, OpenTracingTextMapBridge.instance())) {
                return childTraceContext;
            }
        }
        return null;
    }
}
