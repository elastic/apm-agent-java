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
package co.elastic.apm.opentracing.impl;

import co.elastic.apm.bci.ElasticApmInstrumentation;
import co.elastic.apm.bci.VisibleForAdvice;
import co.elastic.apm.impl.transaction.AbstractSpan;
import co.elastic.apm.impl.transaction.Span;
import co.elastic.apm.impl.transaction.TraceContext;
import co.elastic.apm.impl.transaction.Transaction;
import co.elastic.apm.web.ResultUtil;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import static net.bytebuddy.matcher.ElementMatchers.named;

public class ApmSpanInstrumentation extends ElasticApmInstrumentation {

    static final String OPENTRACING_INSTRUMENTATION_GROUP = "opentracing";

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

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Collections.singleton(OPENTRACING_INSTRUMENTATION_GROUP);
    }

    public static class FinishInstrumentation extends ApmSpanInstrumentation {
        public FinishInstrumentation() {
            super(named("finishInternal"));
        }

        @Advice.OnMethodEnter
        private static void finishInternal(@Advice.FieldValue(value = "dispatcher", readOnly = false) @Nullable Object dispatcher,
                                           @Advice.Argument(0) long finishMicros) {
            final Object newDispatcher = doFinishInternal(dispatcher, finishMicros);
            if (newDispatcher != null) {
                dispatcher = newDispatcher;
            }
        }

        @Nullable
        @VisibleForAdvice
        public static Object doFinishInternal(@Nullable Object dispatcher, long finishMicros) {
            if (dispatcher == null) {
                return null;
            }

            if (dispatcher instanceof AbstractSpan) {
                final AbstractSpan<?> span = (AbstractSpan) dispatcher;

                if (span.getType() == null) {
                    if (span instanceof Transaction) {
                        Transaction transaction = (Transaction) span;
                        if (transaction.getType() == null) {
                            if (transaction.getContext().getRequest().hasContent()) {
                                transaction.withType(Transaction.TYPE_REQUEST);
                            } else {
                                transaction.withType("unknown");
                            }
                        }
                    } else {
                        span.withType("unknown");
                    }
                }

                if (finishMicros >= 0) {
                    span.end(finishMicros);
                } else {
                    span.end();
                }
                return span.getTraceContext().serialize();
            }
            return null;
        }
    }

    public static class SetOperationName extends ApmSpanInstrumentation {
        public SetOperationName() {
            super(named("setOperationName"));
        }

        @VisibleForAdvice
        @Advice.OnMethodEnter(inline = false)
        public static void setOperationName(@Advice.FieldValue(value = "dispatcher") @Nullable Object dispatcher,
                                            @Advice.Argument(0) @Nullable String operationName) {
            if (dispatcher instanceof AbstractSpan) {
                AbstractSpan<?> span = (AbstractSpan) dispatcher;
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
        public static void createError(@Advice.FieldValue(value = "dispatcher") @Nullable Object dispatcher,
                                       @Advice.Argument(0) long epochTimestampMillis,
                                       @Advice.Argument(1) Map<String, ?> fields) {
            final Object errorObject = fields.get("error.object");
            if (dispatcher instanceof AbstractSpan && errorObject instanceof Throwable) {
                ((AbstractSpan) dispatcher).captureException(epochTimestampMillis, (Throwable) errorObject);
            }
        }
    }

    public static class TagInstrumentation extends ApmSpanInstrumentation {

        public TagInstrumentation() {
            super(named("handleTag"));
        }

        @VisibleForAdvice
        @Advice.OnMethodEnter(inline = false)
        public static void handleTag(@Advice.FieldValue(value = "dispatcher") @Nullable Object dispatcher,
                                     @Advice.Argument(0) String key,
                                     @Advice.Argument(1) @Nullable Object value) {
            if (value == null) {
                return;
            }
            if (dispatcher instanceof Transaction) {
                handleTransactionTag((Transaction) dispatcher, key, value);
            } else if (dispatcher instanceof Span) {
                handleSpanTag((Span) dispatcher, key, value);
            }
        }

        private static void handleTransactionTag(Transaction transaction, String key, Object value) {
            if (!handleSpecialTransactionTag(transaction, key, value)) {
                transaction.addTag(key, value.toString());
            }
        }

        private static void handleSpanTag(Span span, String key, Object value) {
            if (!handleSpecialSpanTag(span, key, value)) {
                span.addTag(key, value.toString());
            }
        }

        // unfortunately, we can't use the constants in io.opentracing.tag.Tags,
        // as we can't declare a direct dependency on the OT API
        private static boolean handleSpecialTransactionTag(Transaction transaction, String key, Object value) {
            if ("type".equals(key)) {
                transaction.withType(value.toString());
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
                transaction.withType(Transaction.TYPE_REQUEST);
                return true;
            } else if ("http.method".equals(key)) {
                transaction.getContext().getRequest().withMethod(value.toString());
                transaction.withType(Transaction.TYPE_REQUEST);
                return true;
            } else if ("http.url".equals(key)) {
                transaction.getContext().getRequest().getUrl().appendToFull(value.toString());
                transaction.withType(Transaction.TYPE_REQUEST);
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
                span.withType(value.toString());
                return true;
            } else if ("sampling.priority".equals(key)) {
                // mid-trace sampling is not allowed
                return true;
            } else if ("db.type".equals(key)) {
                span.getContext().getDb().withType(value.toString());
                if (isCache(value)) {
                    span.withType("cache");
                } else {
                    span.withType("db");
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
                    span.withType("ext");
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
        public static void baggageItems(@Advice.FieldValue(value = "dispatcher") @Nullable Object dispatcher,
                                        @Advice.Return(readOnly = false) Iterable<Map.Entry<String, String>> baggage) {
            baggage = doGetBaggage(dispatcher);
        }

        @VisibleForAdvice
        public static Iterable<Map.Entry<String, String>> doGetBaggage(@Nullable Object dispatcher) {
            if (dispatcher instanceof AbstractSpan) {
                String traceParentHeader = ((AbstractSpan) dispatcher).getTraceContext().getOutgoingTraceParentHeader().toString();
                return Collections.singletonMap(TraceContext.TRACE_PARENT_HEADER, traceParentHeader).entrySet();
            } else {
                return Collections.emptyList();
            }
        }
    }

}
