package co.elastic.apm.agent.httpclient.common;

import co.elastic.apm.agent.httpclient.HttpClientHelper;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.GlobalTracer;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Span;

public abstract class AbstractApacheHttpAsyncClientAdvice {

    public static <AsyncProducer, FutureCallback, HttpContext> Object[] startSpan(AbstractApacheHttpAsyncClientHelper<AsyncProducer, FutureCallback, HttpContext> asyncHelper,
                                                                                  AsyncProducer asyncRequestProducer,
                                                                                  HttpContext httpContext,
                                                                                  FutureCallback futureCallback) {
        ElasticApmTracer tracer = GlobalTracer.getTracerImpl();
        if (tracer == null) {
            return null;
        }
        AbstractSpan<?> parent = tracer.getActive();
        if (parent == null) {
            return null;
        }
        Span span = parent.createExitSpan();
        AsyncProducer wrappedProducer = asyncRequestProducer;
        FutureCallback wrappedFutureCallback = futureCallback;
        boolean responseFutureWrapped = false;
        if (span != null) {
            span.withType(HttpClientHelper.EXTERNAL_TYPE)
                .withSubtype(HttpClientHelper.HTTP_SUBTYPE)
                .activate();

            wrappedProducer = asyncHelper.wrapRequestProducer(asyncRequestProducer, span, parent);
            wrappedFutureCallback = asyncHelper.wrapFutureCallback(futureCallback, httpContext, span);
            responseFutureWrapped = true;
        } else {
            wrappedProducer = asyncHelper.wrapRequestProducer(asyncRequestProducer, null, parent);
        }
        return new Object[]{wrappedProducer, wrappedFutureCallback, responseFutureWrapped, span};
    }

    public static void endSpan(Object[] enter, Throwable t) {
        Span span = enter != null ? (Span) enter[3] : null;
        if (span == null) {
            return;
        }
        span.deactivate();

        if (!((Boolean) enter[2])) {
            // Listener is not wrapped - we need to end the span so to avoid leak and report error if occurred during method invocation
            span.captureException(t);
            span.end();
        }
    }
}
