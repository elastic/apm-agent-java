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

import co.elastic.apm.agent.impl.context.web.ResultUtil;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Span;
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

import static co.elastic.apm.agent.impl.transaction.AbstractSpan.PRIO_USER_SUPPLIED;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

public abstract class ApmSpanInstrumentation extends OpenTracingBridgeInstrumentation {

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

        public static class AdviceClass {
            @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
            public static void finishInternal(@Advice.FieldValue(value = "dispatcher", typing = Assigner.Typing.DYNAMIC) @Nullable Object context,
                                              @Advice.Argument(0) long finishMicros) {
                if (context instanceof AbstractSpan<?>) {
                    doFinishInternal((AbstractSpan<?>) context, finishMicros);
                }
            }

            public static void doFinishInternal(AbstractSpan<?> abstractSpan, long finishMicros) {
                abstractSpan.incrementReferences();
                if (abstractSpan instanceof Transaction) {
                    Transaction transaction = (Transaction) abstractSpan;
                    if (transaction.getType() == null) {
                        if (transaction.getContext().getRequest().hasContent()) {
                            transaction.withType(Transaction.TYPE_REQUEST);
                        }
                    }
                }
                if (finishMicros >= 0) {
                    abstractSpan.end(finishMicros);
                } else {
                    abstractSpan.end();
                }
            }
        }
    }

    public static class SetOperationName extends ApmSpanInstrumentation {
        public SetOperationName() {
            super(named("setOperationName"));
        }

        public static class AdviceClass {
            @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
            public static void setOperationName(@Advice.FieldValue(value = "dispatcher", typing = Assigner.Typing.DYNAMIC) @Nullable Object context,
                                                @Advice.Argument(0) @Nullable String operationName) {
                if (context instanceof AbstractSpan<?>) {
                    ((AbstractSpan<?>) context).withName(operationName, PRIO_USER_SUPPLIED);
                } else {
                    logger.warn("Calling setOperationName on an already finished span");
                }
            }
        }
    }

    public static class LogInstrumentation extends ApmSpanInstrumentation {
        public LogInstrumentation() {
            super(named("log").and(takesArguments(long.class, Map.class)));
        }

        public static class AdviceClass {
            @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
            public static void log(@Advice.FieldValue(value = "dispatcher", typing = Assigner.Typing.DYNAMIC) @Nullable Object context,
                                   @Advice.Argument(0) long epochTimestampMicros,
                                   @Advice.Argument(1) Map<String, ?> fields) {

                if (context instanceof AbstractSpan<?>) {
                    AbstractSpan<?> span = (AbstractSpan<?>) context;
                    if ("error".equals(fields.get("event"))) {
                        final Object errorObject = fields.get("error.object");
                        if (errorObject instanceof Throwable) {
                            if (epochTimestampMicros > 0) {
                                span.captureExceptionAndGetErrorId(epochTimestampMicros, (Throwable) errorObject);
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
    }

    public static class TagInstrumentation extends ApmSpanInstrumentation {

        public TagInstrumentation() {
            super(named("handleTag"));
        }

        public static class AdviceClass {
            @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
            public static void handleTag(@Advice.FieldValue(value = "dispatcher", typing = Assigner.Typing.DYNAMIC) @Nullable Object abstractSpanObj,
                                         @Advice.Argument(0) String key,
                                         @Advice.Argument(1) @Nullable Object value) {
                if (value == null) {
                    return;
                }
                if (abstractSpanObj instanceof Transaction) {
                    handleTransactionTag((Transaction) abstractSpanObj, key, value);
                } else if (abstractSpanObj instanceof Span) {
                    handleSpanTag((Span) abstractSpanObj, key, value);
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

            private static void addTag(AbstractSpan<?> transaction, String key, Object value) {
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
                    if (Boolean.TRUE.equals(value)) {
                        transaction.withResultIfUnset("error");
                    }
                    return true;
                } else if ("http.status_code".equals(key) && value instanceof Number) {
                    int status = ((Number) value).intValue();
                    transaction.getContext().getResponse().withStatusCode(status);
                    transaction.withResultIfUnset(ResultUtil.getResultByHttpStatus(status));
                    transaction.withOutcome(ResultUtil.getOutcomeByHttpServerStatus(status));
                    transaction.withType(Transaction.TYPE_REQUEST);
                    return true;
                } else if ("http.method".equals(key)) {
                    transaction.getContext().getRequest().withMethod(value.toString());
                    transaction.withType(Transaction.TYPE_REQUEST);
                    return true;
                } else if ("http.url".equals(key)) {
                    transaction.getContext().getRequest().getUrl().withFull(value.toString());
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
                //noinspection IfCanBeSwitch
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
                    span.withType("db").withSubtype(value.toString());
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
                        span.withType("external");
                    }
                    return true;
                } else if ("http.status_code".equals(key) && value instanceof Number) {
                    int status = ((Number) value).intValue();
                    span.getContext().getHttp().withStatusCode(status);
                    span.withSubtype("http")
                        .withOutcome(ResultUtil.getOutcomeByHttpClientStatus(status));
                    return true;
                } else if ("http.url".equals(key) && value instanceof String) {
                    span.getContext().getHttp().withUrl((String) value);
                    return true;
                } else if ("http.method".equals(key) && value instanceof String) {
                    span.getContext().getHttp().withMethod((String) value);
                    return true;
                }
                return false;
            }
        }

    }

    public static class GetTraceContextInstrumentation extends ApmSpanInstrumentation {

        public GetTraceContextInstrumentation() {
            super(named("getTraceContext"));
        }

        public static class AdviceClass {
            @Nullable
            @Advice.AssignReturned.ToReturned
            @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
            public static Object getTraceContext(@Advice.Argument(value = 0, typing = Assigner.Typing.DYNAMIC) @Nullable Object abstractSpanObj) {
                return abstractSpanObj;
            }
        }
    }

}
