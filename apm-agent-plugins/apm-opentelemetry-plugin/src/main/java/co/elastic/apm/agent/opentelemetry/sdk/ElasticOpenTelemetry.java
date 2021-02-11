package co.elastic.apm.agent.opentelemetry.sdk;

import co.elastic.apm.agent.impl.ElasticApmTracer;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.TracerProvider;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;

public class ElasticOpenTelemetry implements OpenTelemetry {

    private final ContextPropagators contextPropagators;
    private final ElasticOTelTracerProvider elasticOTelTracerProvider;

    public ElasticOpenTelemetry(ElasticApmTracer tracer) {
        elasticOTelTracerProvider = new ElasticOTelTracerProvider(new ElasticOTelTracer(tracer));
        contextPropagators = ContextPropagators.create(W3CTraceContextPropagator.getInstance());
    }

    @Override
    public TracerProvider getTracerProvider() {
        return elasticOTelTracerProvider;
    }

    @Override
    public ContextPropagators getPropagators() {
        return contextPropagators;
    }
}
