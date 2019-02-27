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
package co.elastic.apm.agent.opentracing.impl;

import co.elastic.apm.agent.bci.VisibleForAdvice;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.TraceContext;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.web.ResultUtil;
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
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

public class ApmSpanInstrumentation extends OpenTracingBridgeInstrumentation {

    @VisibleForAdvice
    public static final Logger logger = LoggerFactory.getLogger(ApmSpanInstrumentation.class);

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

    public static class FinishInstrumentation extends ApmSpanInstrumentation {
        public FinishInstrumentation() {
            super(named("finishInternal"));
        }

        @Advice.OnMethodEnter(suppress = Throwable.class)
        private static void finishInternal(@Advice.FieldValue(value = "dispatcher", readOnly = false, typing = Assigner.Typing.DYNAMIC) @Nullable AbstractSpan<?> span,
                                           @Advice.Argument(0) long finishMicros,
                                           @Advice.Argument(value = 1, optional = true) @Nullable Object traceContext) {
            if (span != null) {
                doFinishInternal(span, finishMicros, traceContext);
                span = null;
            }
        }

        @VisibleForAdvice
        public static void doFinishInternal(AbstractSpan<?> abstractSpan, long finishMicros, @Nullable Object traceContext) {
            if (abstractSpan instanceof Transaction) {
                Transaction transaction = (Transaction) abstractSpan;
                if (transaction.getType() == null) {
                    if (transaction.getContext().getRequest().hasContent()) {
                        transaction.withType(Transaction.TYPE_REQUEST);
                    } else {
                        transaction.withType("unknown");
                    }
                }
            } else {
                Span span = (Span) abstractSpan;
                if (span.getType() == null) {
                    span.withType("unknown");
                }
            }

            if (finishMicros >= 0) {
                abstractSpan.end(finishMicros);
            } else {
                abstractSpan.end();
            }

            // If the finished span is the active span, replace with the corresponding TraceContext
            if (tracer != null && traceContext != null && abstractSpan == tracer.getActive() && traceContext instanceof TraceContext) {
                tracer.deactivate(abstractSpan);
                tracer.activate((TraceContext) traceContext);
            }
        }
    }

    public static class SetOperationName extends ApmSpanInstrumentation {
        public SetOperationName() {
            super(named("setOperationName"));
        }

        @VisibleForAdvice
        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static void setOperationName(@Advice.FieldValue(value = "dispatcher", typing = Assigner.Typing.DYNAMIC) @Nullable AbstractSpan<?> span,
                                            @Advice.Argument(0) @Nullable String operationName) {
            if (span != null) {
                span.setName(operationName);
            } else {
                logger.warn("Calling setOperationName on an already finished span");
            }
        }
    }

    public static class LogInstrumentation extends ApmSpanInstrumentation {
        public LogInstrumentation() {
            super(named("log").and(takesArguments(long.class, Map.class)));
        }

        @VisibleForAdvice
        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static void log(@Advice.FieldValue(value = "dispatcher", typing = Assigner.Typing.DYNAMIC) @Nullable AbstractSpan<?> span,
                               @Advice.Argument(0) long epochTimestampMicros,
                               @Advice.Argument(1) Map<String, ?> fields) {

            if (span != null) {
                if ("error".equals(fields.get("event"))) {
                    final Object errorObject = fields.get("error.object");
                    if (errorObject instanceof Throwable) {
                        if (epochTimestampMicros > 0) {
                            span.captureException(epochTimestampMicros, (Throwable) errorObject);
                        } else {
                            span.captureException((Throwable) errorObject);
                        }
                    }
                }
            } else {
                logger.warn("Calling log on an already finished span");
            }
        }
    }

    public static class TagInstrumentation extends ApmSpanInstrumentation {

        public TagInstrumentation() {
            super(named("handleTag"));
        }

        @VisibleForAdvice
        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static void handleTag(@Advice.FieldValue(value = "dispatcher", typing = Assigner.Typing.DYNAMIC) @Nullable AbstractSpan<?> span,
                                     @Advice.Argument(0) String key,
                                     @Advice.Argument(1) @Nullable Object value) {
            if (value == null) {
                return;
            }
            if (span instanceof Transaction) {
                handleTransactionTag((Transaction) span, key, value);
            } else if (span instanceof Span) {
                handleSpanTag((Span) span, key, value);
            } else {
                logger.warn("Calling setTag on an already finished span");
            }
        }

        private static void handleTransactionTag(Transaction transaction, String key, Object value) {
            if (!handleSpecialTransactionTag(transaction, key, value)) {
                addTag(transaction, key, value);
            }
        }

        private static void handleSpanTag(Span span, String key, Object value) {
            if (!handleSpecialSpanTag(span, key, value)) {
                addTag(span, key, value);
            }
        }

        private static void addTag(AbstractSpan transaction, String key, Object value) {
            if (value instanceof Number) {
                transaction.addLabel(key, (Number) value);
            } else if (value instanceof Boolean) {
                transaction.addLabel(key, (Boolean) value);
            } else {
                transaction.addLabel(key, value.toString());
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
                if (span.getSubtype() == null && span.getAction() == null) {
                    span.setType(value.toString(), null, null);
                } else {
                    span.withType(value.toString());
                }
                return true;
            } else if ("subtype".equals(key)) {
                span.withSubtype(value.toString());
                return true;
            } else if ("action".equals(key)) {
                span.withAction(value.toString());
                return true;
            } else if ("sampling.priority".equals(key)) {
                // mid-trace sampling is not allowed
                return true;
            } else if ("db.type".equals(key)) {
                span.getContext().getDb().withType(value.toString());
                if (isCache(value)) {
                    span.withType("cache").withSubtype(value.toString());
                } else {
                    span.withType("db").withSubtype(value.toString());
                }
                return true;
            } else if ("db.instance".equals(key)) {
                span.getContext().getDb().withInstance(value.toString());
                return true;
            } else if ("db.statement".equals(key)) {
                span.getContext().getDb().withStatement(value.toString());
                span.withAction("query");
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

    public static class GetTraceContextInstrumentation extends ApmSpanInstrumentation {

        public GetTraceContextInstrumentation() {
            super(named("getTraceContext"));
        }

        @Advice.OnMethodExit(suppress = Throwable.class)
        public static void getTraceContext(@Advice.Argument(value = 0, typing = Assigner.Typing.DYNAMIC) @Nullable AbstractSpan<?> abstractSpan,
                                           @Advice.Return(readOnly = false) Object traceContext) {
            if (abstractSpan != null) {
                traceContext = abstractSpan.getTraceContext().copy();
            }
        }
    }

}
