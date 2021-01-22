package co.elastic.apm.agent.opentelemetry.sdk;

import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.StatusCode;

import javax.annotation.Nonnull;
import java.util.concurrent.TimeUnit;

public class ElasticOTelSpan implements Span {
    private final AbstractSpan<?> span;

    public ElasticOTelSpan(AbstractSpan<?> span) {
        this.span = span;
    }

    @Override
    public <T> Span setAttribute(AttributeKey<T> key, @Nonnull T value) {
        AttributeMapper.mapAttribute(span, key, value);
        return this;
    }

    @Override
    public Span addEvent(String name, Attributes attributes) {
        return this;
    }

    @Override
    public Span addEvent(String name, Attributes attributes, long timestamp, TimeUnit unit) {
        return this;
    }

    @Override
    public Span setStatus(StatusCode statusCode, String description) {
        // TODO set outcome
        return this;
    }

    @Override
    public Span recordException(Throwable exception, Attributes additionalAttributes) {
        span.captureException(exception);
        return this;
    }

    @Override
    public Span updateName(String name) {
        span.withName(name);
        return this;
    }

    @Override
    public void end() {
        span.end();
    }

    @Override
    public void end(long timestamp, TimeUnit unit) {
        span.end(unit.toMicros(timestamp));
    }

    @Override
    public SpanContext getSpanContext() {
        return new ElasticOTelSpanContext(span.getTraceContext());
    }

    @Override
    public boolean isRecording() {
        return span.isSampled();
    }

    public AbstractSpan<?> getInternalSpan() {
        return span;
    }
}
