package co.elastic.apm.opentracing;

import io.opentracing.SpanContext;
import io.opentracing.propagation.TextMap;

import java.util.Map;

class ExternalProcessSpanContext implements SpanContext {
    private final TextMap textMap;

    private ExternalProcessSpanContext(TextMap textMap) {
        this.textMap = textMap;
    }

    static ExternalProcessSpanContext of(TextMap textMap) {
        return new ExternalProcessSpanContext(textMap);
    }

    @Override
    public Iterable<Map.Entry<String, String>> baggageItems() {
        return textMap;
    }
}
