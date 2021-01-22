package co.elastic.apm.agent.opentelemetry.context;

import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.opentelemetry.sdk.ElasticOTelSpan;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextStorage;
import io.opentelemetry.context.Scope;

import javax.annotation.Nullable;

public class ElasticOTelContextStorage implements ContextStorage {
    private final ElasticApmTracer elasticApmTracer;

    public ElasticOTelContextStorage(ElasticApmTracer elasticApmTracer) {
        this.elasticApmTracer = elasticApmTracer;
    }

    @Override
    public Scope attach(Context toAttach) {
        AbstractSpan<?> span = ((ElasticOTelSpan)Span.fromContextOrNull(toAttach)).getInternalSpan();
        elasticApmTracer.activate(span);
        return new ElasticOTelScope(span);
    }

    @Nullable
    @Override
    public Context current() {
        AbstractSpan<?> active = elasticApmTracer.getActive();
        if (active == null) {
            return null;
        }
        return new ElasticOTelSpan(active).storeInContext(new ElasticOTelContext());
    }
}
