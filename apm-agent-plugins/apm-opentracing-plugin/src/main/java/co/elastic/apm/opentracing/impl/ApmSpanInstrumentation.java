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
import co.elastic.apm.impl.transaction.Span;
import co.elastic.apm.impl.transaction.TraceContext;
import co.elastic.apm.impl.transaction.Transaction;
import co.elastic.apm.web.ResultUtil;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Map;

import static net.bytebuddy.matcher.ElementMatchers.named;

public class ApmSpanInstrumentation extends ElasticApmInstrumentation {

    private final ElementMatcher<? super MethodDescription> methodMatcher;

    public ApmSpanInstrumentation(ElementMatcher<? super MethodDescription> methodMatcher) {
        this.methodMatcher = methodMatcher;
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("co.elastic.apm.opentracing.ApmSpan");
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return methodMatcher;
    }

    @Override
    public boolean includeWhenInstrumentationIsDisabled() {
        return true;
    }

    public static class FinishInstrumentation extends ApmSpanInstrumentation {
        public FinishInstrumentation() {
            super(named("finishInternal"));
        }

        @VisibleForAdvice
        @Advice.OnMethodEnter(inline = false)
        public static void finishInternal(@Advice.FieldValue(value = "transaction", typing = Assigner.Typing.DYNAMIC) @Nullable Transaction transaction,
                                          @Advice.FieldValue(value = "span", typing = Assigner.Typing.DYNAMIC) @Nullable Span span,
                                          @Advice.Argument(0) long finishMicros) {
            if (transaction != null) {
                if (transaction.getType() == null) {
                    if (transaction.getContext().getRequest().hasContent()) {
                        transaction.withType(co.elastic.apm.api.Transaction.TYPE_REQUEST);
                    } else {
                        transaction.withType("unknown");
                    }
                }
                transaction.end(finishMicros * 1000, false);
            } else if (span != null) {
                if (span.getType() == null) {
                    span.withType("unknown");
                }
                span.end(finishMicros * 1000, false);
            }
        }
    }

    public static class SetOperationName extends ApmSpanInstrumentation {
        public SetOperationName() {
            super(named("setOperationName"));
        }

        @VisibleForAdvice
        @Advice.OnMethodEnter(inline = false)
        public static void setOperationName(@Advice.FieldValue(value = "transaction", typing = Assigner.Typing.DYNAMIC) @Nullable Transaction transaction,
                                            @Advice.FieldValue(value = "span", typing = Assigner.Typing.DYNAMIC) @Nullable Span span,
                                            @Advice.Argument(0) @Nullable String operationName) {
            if (transaction != null) {
                transaction.setName(operationName);
            } else if (span != null) {
                span.setName(operationName);
            }
        }
    }

    public static class CreateErrorInstrumentation extends ApmSpanInstrumentation {
        public CreateErrorInstrumentation() {
            super(named("createError"));
        }

        @VisibleForAdvice
        @Advice.OnMethodEnter(inline = false)
        public static void createError(@Advice.Argument(0) long epochTimestampMillis,
                                       @Advice.Argument(1) Map<String, ?> fields) {
            final Object errorObject = fields.get("error.object");
            if (tracer != null && errorObject instanceof Exception) {
                tracer.captureException(epochTimestampMillis, (Exception) errorObject);
            }
        }
    }

    public static class TagInstrumentation extends ApmSpanInstrumentation {

        public TagInstrumentation() {
            super(named("handleTag"));
        }

        @VisibleForAdvice
        @Advice.OnMethodEnter(inline = false)
        public static void handleTag(@Advice.FieldValue(value = "transaction", typing = Assigner.Typing.DYNAMIC) @Nullable Transaction transaction,
                                     @Advice.FieldValue(value = "span", typing = Assigner.Typing.DYNAMIC) @Nullable Span span,
                                     @Advice.Argument(0) String key,
                                     @Advice.Argument(1) @Nullable Object value) {
            if (value == null) {
                return;
            }
            if (transaction != null) {
                handleTransactionTag(transaction, key, value);
            } else if (span != null) {
                handleSpanTag(span, key, value);
            }
        }

        private static void handleTransactionTag(Transaction transaction, String key, Object value) {
            if (!handleSpecialTransactionTag(transaction, key, value)) {
                transaction.addTag(key, value.toString());
            }
        }

        private static void handleSpanTag(Span span, String key, Object value) {
            handleSpecialSpanTag(span, key, value);
            // TODO implement span tags
        }

        // unfortunately, we can't use the constants in io.opentracing.tag.Tags,
        // as we can't declare a direct dependency on the OT API
        private static boolean handleSpecialTransactionTag(Transaction transaction, String key, Object value) {
            if ("type".equals(key)) {
                transaction.setType(value.toString());
                return true;
            } else if ("result".equals(key)) {
                transaction.withResult(value.toString());
                return true;
            } else if ("error".equals(key)) {
                if (transaction.getResult() == null && Boolean.FALSE.equals(value)) {
                    transaction.withResult("error");
                }
                return true;
            } else if ("http.status_code".equals(key) && value instanceof Number) {
                transaction.getContext().getResponse().withStatusCode(((Number) value).intValue());
                if (transaction.getResult() == null) {
                    transaction.withResult(ResultUtil.getResultByHttpStatus(((Number) value).intValue()));
                }
                transaction.setType(co.elastic.apm.api.Transaction.TYPE_REQUEST);
                return true;
            } else if ("http.method".equals(key)) {
                transaction.getContext().getRequest().withMethod(value.toString());
                transaction.setType(co.elastic.apm.api.Transaction.TYPE_REQUEST);
                return true;
            } else if ("http.url".equals(key)) {
                transaction.getContext().getRequest().getUrl().appendToFull(value.toString());
                transaction.setType(co.elastic.apm.api.Transaction.TYPE_REQUEST);
                return true;
            } else if ("sampling.priority".equals(key)) {
                // mid-trace sampling is not allowed
                return true;
            } else if ("user.id".equals(key)) {
                transaction.getContext().getUser().withId(value.toString());
                return true;
            } else if ("user.email".equals(key)) {
                transaction.getContext().getUser().withEmail(value.toString());
                return true;
            } else if ("user.username".equals(key)) {
                transaction.getContext().getUser().withUsername(value.toString());
                return true;
            }
            return false;
        }

        private static boolean handleSpecialSpanTag(Span span, String key, Object value) {
            if ("type".equals(key)) {
                span.setType(value.toString());
                return true;
            } else if ("sampling.priority".equals(key)) {
                // mid-trace sampling is not allowed
                return true;
            } else if ("db.type".equals(key)) {
                span.getContext().getDb().withType(value.toString());
                if (isCache(value)) {
                    span.setType("cache");
                } else {
                    span.setType("db");
                }
                return true;
            } else if ("db.instance".equals(key)) {
                span.getContext().getDb().withInstance(value.toString());
                return true;
            } else if ("db.statement".equals(key)) {
                span.getContext().getDb().withStatement(value.toString());
                return true;
            } else if ("span.kind".equals(key)) {
                if (span.getType() == null && ("producer".equals(value) || "client".equals(value))) {
                    span.setType("ext");
                }
                return true;
            }
            return false;
        }

        private static boolean isCache(Object dbType) {
            return "redis".equals(dbType);
        }
    }

    public static class BaggageItemsInstrumentation extends ApmSpanInstrumentation {

        public BaggageItemsInstrumentation() {
            super(named("baggageItems"));
        }

        @Advice.OnMethodExit
        public static void getTraceParentHeader(@Advice.FieldValue(value = "transaction", typing = Assigner.Typing.DYNAMIC) @Nullable Transaction transaction,
                                                @Advice.FieldValue(value = "span", typing = Assigner.Typing.DYNAMIC) @Nullable Span span,
                                                @Advice.Return(readOnly = false) Iterable<Map.Entry<String, String>> baggage) {
            baggage = doGetBaggage(transaction, span);
        }

        @VisibleForAdvice
        public static Iterable<Map.Entry<String, String>> doGetBaggage(@Nullable Transaction transaction, @Nullable Span span) {
            String traceParentHeader = null;
            if (transaction != null) {
                traceParentHeader = transaction.getTraceContext().getOutgoingTraceParentHeader().toString();
            }
            if (span != null) {
                traceParentHeader = span.getTraceContext().getOutgoingTraceParentHeader().toString();
            }
            if (traceParentHeader != null) {
                return Collections.singletonMap(TraceContext.TRACE_PARENT_HEADER, traceParentHeader).entrySet();
            } else {
                return Collections.emptyList();
            }
        }
    }

}
