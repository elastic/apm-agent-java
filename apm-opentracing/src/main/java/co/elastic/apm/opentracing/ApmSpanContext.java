package co.elastic.apm.opentracing;

import io.opentracing.SpanContext;

import javax.annotation.Nullable;

public interface ApmSpanContext extends SpanContext {
    @Nullable
    Object getTraceContext();
}
