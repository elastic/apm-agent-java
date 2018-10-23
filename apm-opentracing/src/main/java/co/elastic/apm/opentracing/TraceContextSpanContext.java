package co.elastic.apm.opentracing;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Map;

public class TraceContextSpanContext implements ApmSpanContext {

    // byte[] (serialized TraceContext)
    @Nullable
    private final Object traceContext;

    TraceContextSpanContext(@Nullable Object traceContext) {
        this.traceContext = traceContext;
    }

    @Override
    public Iterable<Map.Entry<String, String>> baggageItems() {
        return Collections.emptyList();
    }

    @Override
    @Nullable
    public Object getTraceContext() {
        return traceContext;
    }
}
