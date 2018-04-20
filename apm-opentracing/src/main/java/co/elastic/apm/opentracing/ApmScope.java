package co.elastic.apm.opentracing;

import co.elastic.apm.impl.ElasticApmTracer;
import io.opentracing.Scope;

class ApmScope implements Scope {

    private final boolean finishSpanOnClose;
    private final ApmSpan apmSpan;
    private final ElasticApmTracer tracer;

    ApmScope(boolean finishSpanOnClose, ApmSpan apmSpan, ElasticApmTracer tracer) {
        this.finishSpanOnClose = finishSpanOnClose;
        this.apmSpan = apmSpan;
        this.tracer = tracer;
    }

    @Override
    public void close() {
        if (finishSpanOnClose) {
            apmSpan.finish();
        } else {
            if (apmSpan.isTransaction()) {
                tracer.releaseActiveTransaction();
            } else {
                tracer.releaseActiveSpan();
            }
        }
    }

    @Override
    public ApmSpan span() {
        return apmSpan;
    }
}
