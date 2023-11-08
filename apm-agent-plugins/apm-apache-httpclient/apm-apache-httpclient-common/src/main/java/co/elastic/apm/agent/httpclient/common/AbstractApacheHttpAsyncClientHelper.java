package co.elastic.apm.agent.httpclient.common;


import co.elastic.apm.agent.tracer.ElasticContext;
import co.elastic.apm.agent.tracer.Span;

public interface AbstractApacheHttpAsyncClientHelper<AsyncProducer, AsyncProducerWrapper extends AsyncProducer, FutureCallback, FutureCallbackWrapper extends FutureCallback, HttpContext> {

    AsyncProducerWrapper wrapRequestProducer(AsyncProducer asyncRequestProducer, Span<?> span, ElasticContext<?> toPropagate);

    FutureCallbackWrapper wrapFutureCallback(FutureCallback futureCallback, HttpContext httpContext, Span<?> span);

    void failedWithoutException(FutureCallbackWrapper cb, Throwable t);

    void recycle(AsyncProducerWrapper requestProducerWrapper);

}
