package co.elastic.apm.agent.httpclient.common;


import co.elastic.apm.agent.httpclient.HttpClientHelper;
import co.elastic.apm.agent.tracer.ElasticContext;
import co.elastic.apm.agent.tracer.Span;
import co.elastic.apm.agent.tracer.Tracer;

public abstract class AbstractApacheHttpAsyncClientAdvice {

    public static <AsyncProducer, AsyncProducerWrapper extends AsyncProducer, FutureCallback, FutureCallbackWrapper extends FutureCallback, HttpContext> Object[] startSpan(Tracer tracer,
                                                                                                                                                                            AbstractApacheHttpAsyncClientHelper<AsyncProducer, AsyncProducerWrapper, FutureCallback, FutureCallbackWrapper, HttpContext> asyncHelper,
                                                                                                                                                                            AsyncProducer asyncRequestProducer,
                                                                                                                                                                            HttpContext context,
                                                                                                                                                                            FutureCallback futureCallback) {
        ElasticContext<?> parentContext = tracer.currentContext();
        if (parentContext.isEmpty()) {
            return null;
        }
        FutureCallback wrappedFutureCallback = futureCallback;
        ElasticContext<?> activeContext = tracer.currentContext();
        Span<?> span = activeContext.createExitSpan();
        if (span != null) {
            span.withType(HttpClientHelper.EXTERNAL_TYPE)
                .withSubtype(HttpClientHelper.HTTP_SUBTYPE)
                .withSync(false)
                .activate();
            wrappedFutureCallback = asyncHelper.wrapFutureCallback(futureCallback, context, span);
        }
        AsyncProducer wrappedProducer = asyncHelper.wrapRequestProducer(asyncRequestProducer, span, tracer.currentContext());
        return new Object[]{wrappedProducer, wrappedFutureCallback, span};
    }

    public static <AsyncProducer, AsyncProducerWrapper extends AsyncProducer, FutureCallback, FutureCallbackWrapper extends FutureCallback, HttpContext> void endSpan(AbstractApacheHttpAsyncClientHelper<AsyncProducer, AsyncProducerWrapper, FutureCallback, FutureCallbackWrapper, HttpContext> asyncHelper,
                                                                                                                                                                      Object[] enter, Throwable t) {
        Span<?> span = enter != null ? (Span<?>) enter[2] : null;
        if (span != null) {
            // Deactivate in this thread
            span.deactivate();
            // End the span if the method terminated with an exception.
            // The exception means that the listener who normally does the ending will not be invoked.
            if (t != null) {
                AsyncProducerWrapper wrapper = (AsyncProducerWrapper) enter[0];
                FutureCallbackWrapper cb = (FutureCallbackWrapper) enter[1];
                asyncHelper.failedWithoutException(cb, t);
                asyncHelper.recycle(wrapper);
            }
        }
    }
}
