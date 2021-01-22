package co.elastic.apm.agent.opentelemetry.context;

import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import io.opentelemetry.context.Scope;

public class ElasticOTelScope implements Scope {

    private final AbstractSpan<?> span;

    public ElasticOTelScope(AbstractSpan<?> span) {
        this.span = span;
    }

    @Override
    public void close() {
        span.deactivate();
    }
}
