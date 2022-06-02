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

import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.GlobalTracer;
import co.elastic.apm.agent.impl.sampling.ConstantSampler;
import co.elastic.apm.agent.impl.sampling.Sampler;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.TraceContext;
import co.elastic.apm.agent.impl.transaction.Transaction;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import net.bytebuddy.matcher.ElementMatcher;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;

import javax.annotation.Nullable;
import java.util.Map;

import static net.bytebuddy.matcher.ElementMatchers.named;

public abstract class ApmSpanBuilderInstrumentation extends OpenTracingBridgeInstrumentation {

    private static final String FRAMEWORK_NAME = "OpenTracing";

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

        public static class AdviceClass {
            @Nullable
            @Advice.AssignReturned.ToReturned
            @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
            public static Object createSpan(@Advice.Argument(value = 0, typing = Assigner.Typing.DYNAMIC) @Nullable Object parentContext,
                                            @Advice.Origin Class<?> spanBuilderClass,
                                            @Advice.FieldValue(value = "tags") Map<String, Object> tags,
                                            @Advice.FieldValue(value = "operationName") String operationName,
                                            @Advice.FieldValue(value = "microseconds") long microseconds,
                                            @Advice.Argument(1) @Nullable Iterable<Map.Entry<String, String>> baggage) {
                AbstractSpan<?> parent = null;
                if (parentContext instanceof AbstractSpan<?>) {
                    parent = (AbstractSpan<?>) parentContext;
                }
                return doCreateTransactionOrSpan(parent, tags, operationName, microseconds, baggage, spanBuilderClass.getClassLoader());
            }

            @Nullable
            public static AbstractSpan<?> doCreateTransactionOrSpan(@Nullable AbstractSpan<?> parentContext,
                                                                    Map<String, Object> tags,
                                                                    String operationName, long microseconds,
                                                                    @Nullable Iterable<Map.Entry<String, String>> baggage, ClassLoader applicationClassLoader) {
                AbstractSpan<?> result = null;
                ElasticApmTracer tracer = GlobalTracer.getTracerImpl();
                if (tracer != null) {
                    if (parentContext == null) {
                        result = createTransaction(tags, operationName, microseconds, baggage, tracer, applicationClassLoader);
                    } else {
                        if (microseconds >= 0) {
                            result = tracer.startSpan(TraceContext.fromParent(), parentContext, microseconds);
                        } else {
                            result = tracer.startSpan(TraceContext.fromParent(), parentContext);
                        }
                    }
                }
                if (result != null) {
                    // This reference count never gets decremented, which means it will be handled by GC rather than being recycled.
                    // The OpenTracing API allows interactions with the span, such as span.getTraceContext even after the span has finished
                    // This makes it hard to recycle the span as the life cycle is unclear.
                    // See also https://github.com/opentracing/opentracing-java/issues/312
                    // Previously, we kept a permanent copy of the trace context around and recycled the span on finish.
                    // But that meant lots of complexity in the internal API,
                    // as it had to deal with the fact that a TraceContext might be returned by ElasticApmTracer.getActive.
                    // The complexity doesn't seem worth the OT specific optimization that a bit less memory gets allocated.
                    result.incrementReferences();
                }
                return result;
            }

            @Nullable
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
                    Transaction transaction = tracer.startChildTransaction(baggage, OpenTracingTextMapBridge.instance(), sampler, microseconds, classLoader);
                    if (transaction != null) {
                        transaction.setFrameworkName(FRAMEWORK_NAME);
                    }
                    return transaction;
                }
            }
        }
    }

}
