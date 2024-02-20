package co.elastic.apm.agent.opentelemetry.tracing;

import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.TracerBuilder;

public class OtelTracerBuilder implements TracerBuilder {

    private final Tracer tracer;

    public OtelTracerBuilder(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    public TracerBuilder setSchemaUrl(String s) {
        return this;
    }

    @Override
    public TracerBuilder setInstrumentationVersion(String s) {
        return this;
    }

    @Override
    public Tracer build() {
        return tracer;
    }
}
