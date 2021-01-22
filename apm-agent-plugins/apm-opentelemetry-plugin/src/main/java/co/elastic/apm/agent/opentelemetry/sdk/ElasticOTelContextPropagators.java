package co.elastic.apm.agent.opentelemetry.sdk;

import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapPropagator;

class ElasticOTelContextPropagators implements ContextPropagators {
    public static final ContextPropagators INSTANCE = new ElasticOTelContextPropagators();

    @Override
    public TextMapPropagator getTextMapPropagator() {
        return W3CTraceContextPropagator.getInstance();
    }
}
