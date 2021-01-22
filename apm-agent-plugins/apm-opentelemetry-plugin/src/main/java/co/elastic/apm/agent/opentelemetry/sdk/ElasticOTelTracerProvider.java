package co.elastic.apm.agent.opentelemetry.sdk;

import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.TracerProvider;

import javax.annotation.Nullable;

class ElasticOTelTracerProvider implements TracerProvider {
    private final Tracer tracer;

    ElasticOTelTracerProvider(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    public Tracer get(String instrumentationName) {
        return get(instrumentationName, null);
    }

    @Override
    public Tracer get(String instrumentationName, @Nullable String instrumentationVersion) {
        return this.tracer;
    }
}
