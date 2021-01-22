package co.elastic.apm.agent.opentelemetry.context;

import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.opentelemetry.sdk.ElasticOTelSpan;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
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
        Span span = Span.fromContext(toAttach);
        if (span instanceof ElasticOTelSpan) {
            AbstractSpan<?> internalSpan = ((ElasticOTelSpan) span).getInternalSpan();
            elasticApmTracer.activate(internalSpan);
            return new ElasticOTelScope(internalSpan);
        } else {
            return Scope.noop();
        }
    }

    /**
     * NOTE: the returned context is not the same as the one provided in {@link #attach(Context)}.
     * The consequence of this is that it will not have the context keys of the original context.
     * In other words, {@link Context#get(ContextKey)} will return {@code null} for any key besides the span key.
     */
    @Nullable
    @Override
    public Context current() {
        AbstractSpan<?> active = elasticApmTracer.getActive();
        if (active == null) {
            return null;
        }
        return Context.root().with(new ElasticOTelSpan(active));
    }
}
