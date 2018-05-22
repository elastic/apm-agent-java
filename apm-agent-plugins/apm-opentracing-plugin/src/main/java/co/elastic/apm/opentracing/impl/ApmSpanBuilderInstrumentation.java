/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 the original author or authors
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
package co.elastic.apm.opentracing.impl;

import co.elastic.apm.bci.ElasticApmInstrumentation;
import co.elastic.apm.bci.VisibleForAdvice;
import co.elastic.apm.impl.sampling.ConstantSampler;
import co.elastic.apm.impl.sampling.Sampler;
import co.elastic.apm.impl.transaction.Span;
import co.elastic.apm.impl.transaction.TraceContext;
import co.elastic.apm.impl.transaction.Transaction;
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

public class ApmSpanBuilderInstrumentation extends ElasticApmInstrumentation {

    private static final Logger logger = LoggerFactory.getLogger(ApmSpanBuilderInstrumentation.class);

    private final ElementMatcher<? super MethodDescription> methodMatcher;

    public ApmSpanBuilderInstrumentation(ElementMatcher<? super MethodDescription> methodMatcher) {
        this.methodMatcher = methodMatcher;
    }

    private static long getStartTime(long microseconds) {
        return microseconds >= 0 ? microseconds * 1000 : System.nanoTime();
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("co.elastic.apm.opentracing.ApmSpanBuilder");
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return methodMatcher;
    }

    @Override
    public boolean includeWhenInstrumentationIsDisabled() {
        return true;
    }

    public static class StartApmSpanInstrumentation extends ApmSpanBuilderInstrumentation {

        public StartApmSpanInstrumentation() {
            super(named("startApmSpan"));
        }

        @Advice.OnMethodEnter
        private static void startApmSpan(@Advice.FieldValue(value = "ignoreActiveSpan") boolean ignoreActiveSpan,
                                         @Advice.FieldValue(value = "parentTransaction", readOnly = false)
                                         @Nullable Object parentTransaction,
                                         @Advice.FieldValue(value = "parentSpan", readOnly = false)
                                         @Nullable Object parentSpan) {
            if (tracer != null && isActive(ignoreActiveSpan, parentTransaction, parentSpan)) {
                parentTransaction = tracer.currentTransaction();
                parentSpan = tracer.currentSpan();
            }
        }

        @VisibleForAdvice
        public static boolean isActive(boolean ignoreActiveSpan, @Nullable Object parentTransaction, @Nullable Object parentSpan) {
            return !ignoreActiveSpan && parentSpan == null && parentTransaction == null;
        }
    }

    public static class CreateTransactionInstrumentation extends ApmSpanBuilderInstrumentation {

        public CreateTransactionInstrumentation() {
            super(named("createTransaction"));
        }

        @Advice.OnMethodExit
        public static void createTransaction(@Advice.FieldValue(value = "parentTransaction", typing = Assigner.Typing.DYNAMIC)
                                             @Nullable Transaction parentTransaction,
                                             @Advice.FieldValue(value = "tags") Map<String, Object> tags,
                                             @Advice.FieldValue(value = "operationName") String operationName,
                                             @Advice.FieldValue(value = "microseconds") long microseconds,
                                             @Advice.Argument(0) @Nullable Iterable<Map.Entry<String, String>> baggage,
                                             @Advice.Return(readOnly = false) Object transaction) {
            transaction = doCreateTransaction(parentTransaction, tags, operationName, microseconds, baggage);
        }

        @Nullable
        @VisibleForAdvice
        public static Transaction doCreateTransaction(@Nullable Transaction parentTransaction, Map<String, Object> tags,
                                                      String operationName, long microseconds,
                                                      @Nullable Iterable<Map.Entry<String, String>> baggage) {
            if (tracer != null && parentTransaction == null) {
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
                    return tracer.startManualTransaction(getTraceContextHeader(baggage), sampler, getStartTime(microseconds));
                }
            }
            return null;
        }

        @Nullable
        @VisibleForAdvice
        public static String getTraceContextHeader(@Nullable Iterable<Map.Entry<String, String>> baggage) {
            if (baggage != null) {
                for (Map.Entry<String, String> entry : baggage) {
                    if (entry.getKey().equalsIgnoreCase(TraceContext.TRACE_PARENT_HEADER)) {
                        return entry.getValue();
                    }
                }
            }
            return null;
        }
    }

    public static class CreateSpanInstrumentation extends ApmSpanBuilderInstrumentation {

        public CreateSpanInstrumentation() {
            super(named("createSpan"));
        }

        @Advice.OnMethodExit
        private static void createSpan(@Advice.FieldValue(value = "parentTransaction", typing = Assigner.Typing.DYNAMIC)
                                       @Nullable Transaction parentTransaction,
                                       @Advice.FieldValue(value = "parentSpan", typing = Assigner.Typing.DYNAMIC)
                                       @Nullable Span parentSpan,
                                       @Advice.FieldValue(value = "microseconds") long microseconds,
                                       @Advice.Return(readOnly = false) Object span) {
            span = doCreateSpan(parentTransaction, parentSpan, microseconds);
        }

        @Nullable
        @VisibleForAdvice
        public static Span doCreateSpan(@Nullable Transaction parentTransaction, @Nullable Span parentSpan, long microseconds) {
            if (tracer != null && parentTransaction != null) {
                Transaction transaction = getTransaction(parentTransaction, parentSpan);
                return tracer.startManualSpan(transaction, parentSpan, getStartTime(microseconds));
            }
            return null;
        }

        @Nullable
        private static Transaction getTransaction(@Nullable Transaction parentTransaction, @Nullable Span parentSpan) {
            if (parentTransaction != null) {
                return parentTransaction;
            } else if (parentSpan != null) {
                return parentSpan.getTransaction();
            } else {
                return null;
            }
        }
    }

}
