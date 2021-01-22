package co.elastic.apm.agent.opentelemetry.sdk;

import co.elastic.apm.agent.impl.ElasticApmTracer;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.Tracer;

class ElasticOTelTracer implements Tracer {
    private final ElasticApmTracer elasticApmTracer;

    ElasticOTelTracer(ElasticApmTracer elasticApmTracer) {
        this.elasticApmTracer = elasticApmTracer;
    }

    @Override
    public SpanBuilder spanBuilder(String spanName) {
        return new ElasticOTelSpanBuilder(spanName, elasticApmTracer);
    }
}
