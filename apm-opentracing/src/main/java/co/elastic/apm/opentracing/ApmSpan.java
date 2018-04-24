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
package co.elastic.apm.opentracing;

import co.elastic.apm.impl.ElasticApmTracer;
import co.elastic.apm.impl.transaction.Transaction;
import co.elastic.apm.web.ResultUtil;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.log.Fields;
import io.opentracing.tag.Tags;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Map;

import static io.opentracing.log.Fields.ERROR_OBJECT;
import static io.opentracing.tag.Tags.DB_TYPE;

class ApmSpan implements Span, SpanContext {

    @Nullable
    private final Transaction transaction;
    @Nullable
    private final co.elastic.apm.impl.transaction.Span span;
    private final ElasticApmTracer tracer;

    ApmSpan(@Nullable Transaction transaction, @Nullable co.elastic.apm.impl.transaction.Span span, ElasticApmTracer tracer) {
        this.tracer = tracer;
        if (transaction == null && span == null) {
            throw new IllegalArgumentException();
        } else if (transaction != null && span != null) {
            throw new IllegalArgumentException();
        }

        this.transaction = transaction;
        this.span = span;
    }

    boolean isTransaction() {
        return transaction != null;
    }

    @Override
    public SpanContext context() {
        return this;
    }

    @Override
    public ApmSpan setTag(String key, String value) {
        handleTag(key, value);
        return this;
    }

    @Override
    public ApmSpan setTag(String key, boolean value) {
        handleTag(key, value);
        return this;
    }

    @Override
    public ApmSpan setTag(String key, Number value) {
        handleTag(key, value);
        return this;
    }

    @Override
    public ApmSpan setOperationName(String operationName) {
        if (transaction != null) {
            transaction.setName(operationName);
        } else {
            assert span != null;
            span.setName(operationName);
        }
        return this;
    }

    @Override
    public void finish() {
        finishInternal(System.nanoTime());
    }

    @Override
    public void finish(long finishMicros) {
        finishInternal(finishMicros * 1000);
    }

    private void finishInternal(long nanoTime) {
        if (transaction != null) {
            if (transaction.getType() == null) {
                if (transaction.getContext().getRequest().hasContent()) {
                    transaction.withType(co.elastic.apm.api.Transaction.TYPE_REQUEST);
                } else {
                    transaction.withType("unknown");
                }
            }
            transaction.end(nanoTime, false);
        } else if (span != null) {
            if (span.getType() == null) {
                span.withType("unknown");
            }
            span.end(nanoTime, false);
        }
    }

    @Nullable
    co.elastic.apm.impl.transaction.Span getSpan() {
        return span;
    }

    @Nullable
    Transaction getTransaction() {
        return transaction;
    }

    // unsupported

    @Override
    public ApmSpan log(Map<String, ?> fields) {
        if ("error".equals(fields.get(Fields.EVENT))) {
            createError(System.currentTimeMillis(), fields);
        }
        return this;
    }

    @Override
    public ApmSpan log(long timestampMicroseconds, Map<String, ?> fields) {
        if ("error".equals(fields.get(Fields.EVENT))) {
            createError(timestampMicroseconds / 1000, fields);
        }
        return this;
    }

    private void createError(long epchTimestampMillis, Map<String, ?> fields) {
        final Object errorObject = fields.get(ERROR_OBJECT);
        if (errorObject instanceof Exception) {
            tracer.captureException(epchTimestampMillis, (Exception) errorObject);
        }
    }

    @Override
    public ApmSpan log(String event) {
        log(Collections.singletonMap(Fields.EVENT, event));
        return this;
    }

    @Override
    public ApmSpan log(long timestampMicroseconds, String event) {
        log(timestampMicroseconds, Collections.singletonMap(Fields.EVENT, event));
        return this;
    }

    @Override
    public ApmSpan setBaggageItem(String key, String value) {
        return this;
    }

    @Override
    @Nullable
    public String getBaggageItem(String key) {
        return null;
    }

    @Override
    public Iterable<Map.Entry<String, String>> baggageItems() {
        return Collections.emptyList();
    }


    private void handleTag(String key, @Nullable Object value) {
        if (value == null) {
            return;
        }
        if (transaction != null) {
            handleTransactionTag(key, value);
        } else {
            handleSpanTag(key, value);
        }
    }

    private void handleTransactionTag(String key, Object value) {
        if (!handleSpecialTransactionTag(key, value)) {
            assert transaction != null;
            transaction.addTag(key, value.toString());
        }
    }

    private void handleSpanTag(String key, Object value) {
        handleSpecialSpanTag(key, value);
        // TODO implement span tags
    }

    private boolean handleSpecialTransactionTag(String key, Object value) {
        assert transaction != null;
        if (ElasticApmTags.TYPE.getKey().equals(key)) {
            transaction.setType(value.toString());
            return true;
        } else if (ElasticApmTags.RESULT.getKey().equals(key)) {
            transaction.withResult(value.toString());
            return true;
        } else if (Tags.ERROR.getKey().equals(key)) {
            if (transaction.getResult() == null && Boolean.FALSE.equals(value)) {
                transaction.withResult("error");
            }
            return true;
        } else if (Tags.HTTP_STATUS.getKey().equals(key) && value instanceof Number) {
            transaction.getContext().getResponse().withStatusCode(((Number) value).intValue());
            if (transaction.getResult() == null) {
                transaction.withResult(ResultUtil.getResultByHttpStatus(((Number) value).intValue()));
            }
            transaction.setType(co.elastic.apm.api.Transaction.TYPE_REQUEST);
            return true;
        } else if (Tags.HTTP_METHOD.getKey().equals(key)) {
            transaction.getContext().getRequest().withMethod(value.toString());
            transaction.setType(co.elastic.apm.api.Transaction.TYPE_REQUEST);
            return true;
        } else if (Tags.HTTP_URL.getKey().equals(key)) {
            transaction.getContext().getRequest().getUrl().appendToFull(value.toString());
            transaction.setType(co.elastic.apm.api.Transaction.TYPE_REQUEST);
            return true;
        } else if (Tags.SAMPLING_PRIORITY.getKey().equals(key)) {
            // mid-trace sampling is not allowed
            return true;
        } else if (ElasticApmTags.USER_ID.getKey().equals(key)) {
            transaction.getContext().getUser().withId(value.toString());
            return true;
        } else if (ElasticApmTags.USER_EMAIL.getKey().equals(key)) {
            transaction.getContext().getUser().withEmail(value.toString());
            return true;
        } else if (ElasticApmTags.USER_USERNAME.getKey().equals(key)) {
            transaction.getContext().getUser().withUsername(value.toString());
            return true;
        }
        return false;
    }

    private boolean handleSpecialSpanTag(String key, Object value) {
        assert span != null;
        if (ElasticApmTags.TYPE.getKey().equals(key)) {
            span.setType(value.toString());
            return true;
        } else if (Tags.SAMPLING_PRIORITY.getKey().equals(key)) {
            // mid-trace sampling is not allowed
            return true;
        } else if (DB_TYPE.getKey().equals(key)) {
            span.getContext().getDb().withType(value.toString());
            if (isCache(value)) {
                span.setType("cache");
            } else {
                span.setType("db");
            }
            return true;
        } else if (Tags.DB_INSTANCE.getKey().equals(key)) {
            span.getContext().getDb().withInstance(value.toString());
            return true;
        } else if (Tags.DB_STATEMENT.getKey().equals(key)) {
            span.getContext().getDb().withStatement(value.toString());
            return true;
        } else if (Tags.SPAN_KIND.getKey().equals(key)) {
            if (span.getType() == null && (Tags.SPAN_KIND_PRODUCER.equals(value) || Tags.SPAN_KIND_CLIENT.equals(value))) {
                span.setType("ext");
            }
            return true;
        }
        return false;
    }

    private boolean isCache(Object dbType) {
        return "redis".equals(dbType);
    }

}
