package co.elastic.apm.agent.opentelemetry.sdk;

import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.MultiValueMapAccessor;
import co.elastic.apm.agent.util.PotentiallyMultiValuedMap;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

class ElasticOTelSpanBuilder implements SpanBuilder {

    private final String spanName;
    private final ElasticApmTracer elasticApmTracer;
    private long epochMicros = -1;
    @Nullable
    private AbstractSpan<?> parent;
    @Nullable
    private Context remoteContext;
    private final Map<String, Object> attributes = new HashMap<>();

    public ElasticOTelSpanBuilder(String spanName, ElasticApmTracer elasticApmTracer) {
        this.spanName = spanName;
        this.elasticApmTracer = elasticApmTracer;
        this.parent = elasticApmTracer.getActive();
    }

    @Override
    public SpanBuilder setParent(Context context) {
        Span span = Span.fromContext(context);
        if (span.getSpanContext().isRemote()) {
            remoteContext = context;
        } else {
            parent = ((ElasticOTelSpan) span).getInternalSpan();
        }
        return this;
    }

    @Override
    public SpanBuilder setNoParent() {
        parent = null;
        remoteContext = null;
        return this;
    }

    @Override
    public SpanBuilder addLink(SpanContext spanContext) {
        return this;
    }

    @Override
    public SpanBuilder addLink(SpanContext spanContext, Attributes attributes) {
        return this;
    }

    @Override
    public SpanBuilder setAttribute(String key, @Nonnull String value) {
        attributes.put(key, value);
        return this;
    }

    @Override
    public SpanBuilder setAttribute(String key, long value) {
        attributes.put(key, value);
        return this;
    }

    @Override
    public SpanBuilder setAttribute(String key, double value) {
        attributes.put(key, value);
        return this;
    }

    @Override
    public SpanBuilder setAttribute(String key, boolean value) {
        attributes.put(key, value);
        return this;
    }

    @Override
    public <T> SpanBuilder setAttribute(AttributeKey<T> key, @Nonnull T value) {
        attributes.put(key.getKey(), value);
        return this;
    }

    @Override
    public SpanBuilder setSpanKind(Span.Kind spanKind) {
        return this;
    }

    @Override
    public SpanBuilder setStartTimestamp(long startTimestamp, TimeUnit unit) {
        this.epochMicros = unit.toMicros(startTimestamp);
        return this;
    }

    @Override
    public Span startSpan() {
        AbstractSpan<?> span;
        if (remoteContext != null) {
            PotentiallyMultiValuedMap headers = new PotentiallyMultiValuedMap(2);
            W3CTraceContextPropagator.getInstance()
                .inject(remoteContext, headers, (carrier, key, value) -> {
                if (carrier != null) carrier.add(key, value);
            });
            span = elasticApmTracer.startChildTransaction(headers, MultiValueMapAccessor.INSTANCE, getClass().getClassLoader(), epochMicros);
        } else if (parent == null) {
            span = elasticApmTracer.startRootTransaction(getClass().getClassLoader(), epochMicros);
        } else {
            span = elasticApmTracer.startSpan(parent, epochMicros);
        }
        if (span == null) {
            return Span.getInvalid();
        }
        span.withName(spanName);
        attributes.forEach(span::addLabel);
        // TODO translate well-known attributes
        return new ElasticOTelSpan(span);
    }
}
