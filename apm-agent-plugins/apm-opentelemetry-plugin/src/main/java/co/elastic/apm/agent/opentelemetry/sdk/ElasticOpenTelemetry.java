package co.elastic.apm.agent.opentelemetry.sdk;

import co.elastic.apm.agent.impl.ElasticApmTracer;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.TracerProvider;
import io.opentelemetry.context.propagation.ContextPropagators;

public class ElasticOpenTelemetry implements OpenTelemetry {
    private final TracerProvider traceProvider;
    private final ContextPropagators contextPropagators;

    public ElasticOpenTelemetry(ElasticApmTracer tracer) {
        this.traceProvider = new ElasticOTelTracerProvider(new ElasticOTelTracer(tracer));
        this.contextPropagators = ElasticOTelContextPropagators.INSTANCE;
    }

    @Override
    public TracerProvider getTracerProvider() {
        return this.traceProvider;
    }

    @Override
    public ContextPropagators getPropagators() {
        return this.contextPropagators;
    }
}
