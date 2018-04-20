package co.elastic.apm.opentracing;

import co.elastic.apm.impl.ElasticApmTracer;
import co.elastic.apm.impl.transaction.Transaction;
import io.opentracing.ScopeManager;
import io.opentracing.Span;

import javax.annotation.Nullable;

class ApmScopeManager implements ScopeManager {

    private final ElasticApmTracer tracer;

    ApmScopeManager(ElasticApmTracer tracer) {
        this.tracer = tracer;
    }

    @Override
    public ApmScope activate(Span span, boolean finishSpanOnClose) {
        final ApmSpan apmSpan = (ApmSpan) span;
        if (apmSpan.isTransaction()) {
            tracer.activate(apmSpan.getTransaction());
        } else {
            tracer.activate(apmSpan.getSpan());
        }
        return new ApmScope(finishSpanOnClose, apmSpan, tracer);
    }

    @Override
    @Nullable
    public ApmScope active() {
        final co.elastic.apm.impl.transaction.Span span = tracer.currentSpan();
        final Transaction transaction = tracer.currentTransaction();
        if (span == null && transaction == null) {
            return null;
        } else if (span != null) {
            return new ApmScope(false, new ApmSpan(null, span), tracer);
        } else {
            return new ApmScope(false, new ApmSpan(transaction, null), tracer);
        }
    }
}
