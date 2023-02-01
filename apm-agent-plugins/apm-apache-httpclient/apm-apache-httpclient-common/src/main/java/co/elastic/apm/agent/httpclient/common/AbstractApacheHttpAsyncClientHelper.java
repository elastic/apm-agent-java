package co.elastic.apm.agent.httpclient.common;

import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Span;

public interface AbstractApacheHttpAsyncClientHelper<AsyncProducer, FutureCallback, HttpContext> {

    AsyncProducer wrapRequestProducer(AsyncProducer asyncRequestProducer, Span span, AbstractSpan<?> parent);

    FutureCallback wrapFutureCallback(FutureCallback futureCallback, HttpContext httpContext, Span span);
}
