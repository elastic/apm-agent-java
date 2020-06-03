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
package co.elastic.apm.agent.opentracing.impl;

import co.elastic.apm.agent.bci.VisibleForAdvice;
import co.elastic.apm.agent.bci.bytebuddy.postprocessor.AssignToField;
import co.elastic.apm.agent.bci.bytebuddy.postprocessor.AssignToReturn;
import co.elastic.apm.agent.impl.transaction.TraceContext;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import net.bytebuddy.matcher.ElementMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.Map;

import static net.bytebuddy.matcher.ElementMatchers.named;

public class ExternalSpanContextInstrumentation extends OpenTracingBridgeInstrumentation {

    private static final Logger logger = LoggerFactory.getLogger(ExternalSpanContextInstrumentation.class);

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

        @Nullable
        @AssignToField(value = "childTraceContext")
        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static TraceContext toTraceId(@Advice.FieldValue(value = "textMap", typing = Assigner.Typing.DYNAMIC) @Nullable Iterable<Map.Entry<String, String>> textMap,
                                     @Advice.FieldValue(value = "childTraceContext", typing = Assigner.Typing.DYNAMIC) @Nullable TraceContext childTraceContext) {
            if (textMap == null) {
                return childTraceContext;
            }
            return parseTextMap(textMap);

        }


        @Nullable
        @AssignToReturn
        @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
        public static String onExit(@Advice.FieldValue(value = "childTraceContext", typing = Assigner.Typing.DYNAMIC) @Nullable TraceContext childTraceContext) {
            if (childTraceContext == null) {
                return null;
            }
            return childTraceContext.getTraceId().toString();
        }
    }

    public static class ToSpanIdInstrumentation extends ExternalSpanContextInstrumentation {

        public ToSpanIdInstrumentation() {
            super(named("toSpanId"));
        }

        @Nullable
        @AssignToField(value = "childTraceContext")
        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static TraceContext toSpanId(@Advice.FieldValue(value = "textMap", typing = Assigner.Typing.DYNAMIC) @Nullable Iterable<Map.Entry<String, String>> textMap,
                                    @Advice.FieldValue(value = "childTraceContext", typing = Assigner.Typing.DYNAMIC) @Nullable TraceContext childTraceContext) {
            if (textMap == null) {
                return childTraceContext;
            }
            return parseTextMap(textMap);
        }

        @Nullable
        @AssignToReturn
        @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
        public static String onExit(@Advice.FieldValue(value = "childTraceContext", typing = Assigner.Typing.DYNAMIC) @Nullable TraceContext childTraceContext) {
            if (childTraceContext == null) {
                return null;
            }
            return childTraceContext.getParentId().toString();
        }
    }

    @VisibleForAdvice
    @Nullable
    public static TraceContext parseTextMap(Iterable<Map.Entry<String, String>> textMap) {
        if (tracer != null) {
            TraceContext childTraceContext = TraceContext.with64BitId(tracer);
            if (TraceContext.<Iterable<Map.Entry<String, String>>>getFromTraceContextTextHeaders().asChildOf(childTraceContext, textMap, OpenTracingTextMapBridge.instance())) {
                return childTraceContext;
            }
        }
        return null;
    }
}
