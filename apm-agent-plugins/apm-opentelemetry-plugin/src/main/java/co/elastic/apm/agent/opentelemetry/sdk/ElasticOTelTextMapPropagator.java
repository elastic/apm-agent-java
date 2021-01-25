package co.elastic.apm.agent.opentelemetry.sdk;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapPropagator;

import javax.annotation.Nullable;
import java.util.Collection;

public class ElasticOTelTextMapPropagator implements TextMapPropagator {

    private static final TextMapPropagator W3C_PROPAGATOR = W3CTraceContextPropagator.getInstance();

    @Override
    public Collection<String> fields() {
        return W3C_PROPAGATOR.fields();
    }

    @Override
    public <C> void inject(Context context, @Nullable C carrier, Setter<C> setter) {
        Span span = Span.fromContext(context);
        if (span instanceof ElasticOTelSpan) {
            ((ElasticOTelSpan) span).getInternalSpan().setNonDiscardable();
        }
        W3C_PROPAGATOR.inject(context, carrier, setter);
    }

    @Override
    public <C> Context extract(Context context, @Nullable C carrier, Getter<C> getter) {
        return W3C_PROPAGATOR.extract(context, carrier, getter);
    }
}
