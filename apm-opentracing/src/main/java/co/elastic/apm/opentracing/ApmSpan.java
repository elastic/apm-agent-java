package co.elastic.apm.opentracing;

import co.elastic.apm.impl.ElasticApmTracer;
import co.elastic.apm.impl.transaction.Transaction;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.log.Fields;
import io.opentracing.tag.Tags;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Map;

import static io.opentracing.log.Fields.ERROR_OBJECT;

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
        finish(System.nanoTime());
    }

    @Override
    public void finish(long finishMicros) {
        if (transaction != null) {
            if (transaction.getType() == null) {
                transaction.withType("unknown");
            }
            if (transaction.getResult() == null) {
                transaction.withResult("success");
            }
            transaction.end(finishMicros * 1000);
        } else {
            assert span != null;
            if (span.getType() == null) {
                span.withType("unknown");
            }
            span.end(finishMicros * 1000);
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
        if (Tags.COMPONENT.getKey().equals(key)) {
            if (transaction.getType() == null) {
                transaction.setType(value.toString());
            }
            // return false so that component also lands in the custom tags
            return false;
        } else if (ElasticApmTags.TYPE.getKey().equals(key)) {
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
                transaction.withResult(getResult(((Number) value).intValue()));
            }
            return true;
        } else if (Tags.HTTP_METHOD.getKey().equals(key)) {
            transaction.getContext().getRequest().withMethod(value.toString());
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
        if (Tags.COMPONENT.getKey().equals(key)) {
            span.setType(value.toString());
            // return false so that component also lands in the custom tags
            return false;
        } else if (ElasticApmTags.TYPE.getKey().equals(key)) {
            span.setType(value.toString());
            return true;
        } else if (Tags.SAMPLING_PRIORITY.getKey().equals(key)) {
            // mid-trace sampling is not allowed
            return true;
        } else if (Tags.DB_TYPE.getKey().equals(key)) {
            span.getContext().getDb().withType(value.toString());
            return true;
        } else if (Tags.DB_INSTANCE.getKey().equals(key)) {
            span.getContext().getDb().withInstance(value.toString());
            return true;
        } else if (Tags.DB_STATEMENT.getKey().equals(key)) {
            span.getContext().getDb().withStatement(value.toString());
            return true;
        }
        return false;
    }

    @Nullable
    String getResult(int status) {
        if (status >= 200 && status < 300) {
            return "HTTP 2xx";
        }
        if (status >= 300 && status < 400) {
            return "HTTP 3xx";
        }
        if (status >= 400 && status < 500) {
            return "HTTP 4xx";
        }
        if (status >= 500 && status < 600) {
            return "HTTP 5xx";
        }
        if (status >= 100 && status < 200) {
            return "HTTP 1xx";
        }
        return null;
    }
}
