package co.elastic.apm.opentracing;

import co.elastic.apm.impl.ElasticApmTracer;
import io.opentracing.SpanContext;
import io.opentracing.propagation.Format;

import javax.annotation.Nullable;

class ApmTracer implements io.opentracing.Tracer {
    private final ElasticApmTracer tracer;
    private final ApmScopeManager scopeManager;

    ApmTracer(ElasticApmTracer tracer, ApmScopeManager scopeManager) {
        this.tracer = tracer;
        this.scopeManager = scopeManager;
    }

    @Override
    public ApmScopeManager scopeManager() {
        return scopeManager;
    }

    @Override
    @Nullable
    public ApmSpan activeSpan() {
        final ApmScope active = scopeManager().active();
        if (active != null) {
            return active.span();
        }
        return null;
    }

    @Override
    public ApmSpanBuilder buildSpan(String operationName) {
        return new ApmSpanBuilder(operationName, tracer, scopeManager());
    }

    @Override
    public <C> void inject(SpanContext spanContext, Format<C> format, C carrier) {
        // distributed tracing is not supported yet
    }

    @Override
    @Nullable
    public <C> SpanContext extract(Format<C> format, C carrier) {
        // distributed tracing is not supported yet
        return null;
    }
}
