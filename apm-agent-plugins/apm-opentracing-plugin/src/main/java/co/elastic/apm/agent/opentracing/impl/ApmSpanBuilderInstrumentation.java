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
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.sampling.ConstantSampler;
import co.elastic.apm.agent.impl.sampling.Sampler;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.TraceContext;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import net.bytebuddy.matcher.ElementMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;

import static net.bytebuddy.matcher.ElementMatchers.named;

public class ApmSpanBuilderInstrumentation extends OpenTracingBridgeInstrumentation {

    private static final Logger logger = LoggerFactory.getLogger(ApmSpanBuilderInstrumentation.class);

    private final ElementMatcher<? super MethodDescription> methodMatcher;

    public ApmSpanBuilderInstrumentation(ElementMatcher<? super MethodDescription> methodMatcher) {
        this.methodMatcher = methodMatcher;
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("co.elastic.apm.opentracing.ApmSpanBuilder");
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return methodMatcher;
    }

    public static class CreateSpanInstrumentation extends ApmSpanBuilderInstrumentation {

        public CreateSpanInstrumentation() {
            super(named("createSpan"));
        }

        @Advice.OnMethodExit(suppress = Throwable.class)
        public static void createSpan(@Advice.Argument(value = 0, typing = Assigner.Typing.DYNAMIC) @Nullable TraceContext parentContext,
                                      @Advice.Origin Class<?> spanBuilderClass,
                                      @Advice.FieldValue(value = "tags") Map<String, Object> tags,
                                      @Advice.FieldValue(value = "operationName") String operationName,
                                      @Advice.FieldValue(value = "microseconds") long microseconds,
                                      @Advice.Argument(1) @Nullable Iterable<Map.Entry<String, String>> baggage,
                                      @Advice.Return(readOnly = false) Object span) {
            span = doCreateTransactionOrSpan(parentContext, tags, operationName, microseconds, baggage, spanBuilderClass.getClassLoader());
        }

        @Nullable
        @VisibleForAdvice
        public static AbstractSpan<?> doCreateTransactionOrSpan(@Nullable TraceContext parentContext,
                                                                Map<String, Object> tags,
                                                                String operationName, long microseconds,
                                                                @Nullable Iterable<Map.Entry<String, String>> baggage, ClassLoader applicationClassLoader) {
            if (tracer != null) {
                if (parentContext == null) {
                    return createTransaction(tags, operationName, microseconds, baggage, tracer, applicationClassLoader);
                } else {
                    if (microseconds >= 0) {
                        return tracer.startSpan(TraceContext.fromParent(), parentContext, microseconds);
                    } else {
                        return tracer.startSpan(TraceContext.fromParent(), parentContext);
                    }
                }
            }
            return null;
        }

        @Nonnull
        private static AbstractSpan<?> createTransaction(Map<String, Object> tags, String operationName, long microseconds,
                                                         @Nullable Iterable<Map.Entry<String, String>> baggage, ElasticApmTracer tracer, ClassLoader classLoader) {
            if ("client".equals(tags.get("span.kind"))) {
                logger.info("Ignoring transaction '{}', as a span.kind client can never be a transaction. " +
                    "Consider creating a span for the whole request.", operationName);
                return tracer.noopTransaction();
            } else {
                final Sampler sampler;
                final Object samplingPriority = tags.get("sampling.priority");
                if (samplingPriority instanceof Number) {
                    sampler = ConstantSampler.of(((Number) samplingPriority).intValue() > 0);
                } else {
                    sampler = tracer.getSampler();
                }
                return tracer.startTransaction(TraceContext.fromTraceparentHeader(), getTraceContextHeader(baggage), sampler, microseconds, classLoader);
            }
        }

        @Nullable
        @VisibleForAdvice
        static String getTraceContextHeader(@Nullable Iterable<Map.Entry<String, String>> baggage) {
            if (baggage != null) {
                for (Map.Entry<String, String> entry : baggage) {
                    if (entry.getKey().equalsIgnoreCase(TraceContext.TRACE_PARENT_TEXTUAL_HEADER_NAME)) {
                        return entry.getValue();
                    }
                }
            }
            return null;
        }
    }

}
