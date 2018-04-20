package co.elastic.apm.opentracing;

import co.elastic.apm.impl.ElasticApmTracer;
import co.elastic.apm.impl.sampling.ConstantSampler;
import co.elastic.apm.impl.sampling.Sampler;
import co.elastic.apm.impl.transaction.Transaction;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.tag.Tags;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

class ApmSpanBuilder implements Tracer.SpanBuilder {
    @Nullable
    private final String operationName;
    private final ElasticApmTracer tracer;
    private final Map<String, Object> tags = new HashMap<>();
    private final ApmScopeManager scopeManager;
    @Nullable
    private ApmSpan parent;
    private long nanoTime = System.nanoTime();
    private boolean ignoreActiveSpan = false;
    private Sampler sampler;

    ApmSpanBuilder(@Nullable String operationName, ElasticApmTracer tracer, ApmScopeManager scopeManager) {
        this.operationName = operationName;
        this.tracer = tracer;
        this.scopeManager = scopeManager;
        sampler = tracer.getSampler();
    }

    @Override
    public ApmSpanBuilder asChildOf(SpanContext parent) {
        // distributed tracing and span context relationships are not supported yet
        return this;
    }

    @Override
    public ApmSpanBuilder asChildOf(Span parent) {
        this.parent = (ApmSpan) parent;
        return this;
    }

    @Override
    public ApmSpanBuilder addReference(String referenceType, SpanContext referencedContext) {
        // TODO add reference types
        asChildOf(referencedContext);
        return this;
    }

    @Override
    public ApmSpanBuilder ignoreActiveSpan() {
        this.ignoreActiveSpan = true;
        return this;
    }

    @Override
    public ApmSpanBuilder withTag(String key, String value) {
        tags.put(key, value);
        return this;
    }

    @Override
    public ApmSpanBuilder withTag(String key, boolean value) {
        if (Tags.SAMPLING_PRIORITY.getKey().equals(key)) {
            sampler = ConstantSampler.of(value);
        } else {
            tags.put(key, value);
        }
        return this;
    }

    @Override
    public ApmSpanBuilder withTag(String key, Number value) {
        tags.put(key, value);
        return this;
    }

    @Override
    public ApmSpanBuilder withStartTimestamp(long microseconds) {
        this.nanoTime = microseconds * 1000;
        return this;
    }

    @Override
    public ApmScope startActive(boolean finishSpanOnClose) {
        return scopeManager.activate(startApmSpan(), finishSpanOnClose);
    }

    @Override
    public ApmSpan start() {
        return startApmSpan();
    }

    @Override
    @Deprecated
    public ApmSpan startManual() {
        return start();
    }

    @Nonnull
    private ApmSpan startApmSpan() {
        final ApmScope active = scopeManager.active();
        if (!ignoreActiveSpan && active != null) {
            asChildOf((Span) active.span());
        }
        final ApmSpan apmSpan;
        if (parent == null) {
            final Transaction transaction = tracer.startManualTransaction(sampler, nanoTime);
            apmSpan = new ApmSpan(transaction, null, tracer).setOperationName(operationName);
        } else {
            Transaction transaction = getTransaction(parent);
            final co.elastic.apm.impl.transaction.Span span = tracer.startManualSpan(transaction, parent.getSpan(), nanoTime);
            apmSpan = new ApmSpan(null, span, tracer).setOperationName(operationName);
        }
        addTags(apmSpan);
        return apmSpan;
    }

    private void addTags(ApmSpan apmSpan) {
        for (Map.Entry<String, Object> entry : tags.entrySet()) {
            if (entry.getValue() instanceof String) {
                apmSpan.setTag(entry.getKey(), (String) entry.getValue());
            } else if (entry.getValue() instanceof Number) {
                apmSpan.setTag(entry.getKey(), (Number) entry.getValue());
            } else if (entry.getValue() instanceof Boolean) {
                apmSpan.setTag(entry.getKey(), (Boolean) entry.getValue());
            }
        }
    }

    private Transaction getTransaction(ApmSpan parent) {
        if (parent.isTransaction()) {
            return parent.getTransaction();
        } else {
            final Transaction transaction = parent.getSpan().getTransaction();
            assert transaction != null;
            return transaction;
        }
    }

}
