package co.elastic.apm.agent.httpclient.common;


import co.elastic.apm.agent.httpclient.HttpClientHelper;
import co.elastic.apm.agent.tracer.ElasticContext;
import co.elastic.apm.agent.tracer.Span;
import co.elastic.apm.agent.tracer.Tracer;

public abstract class AbstractApacheHttpAsyncClientAdvice {

    public static <PRODUCER, WRAPPER extends PRODUCER, CALLBACK, CALLBACK_WRAPPER extends CALLBACK, CONTEXT> Object[] startSpan(
        Tracer tracer, AbstractApacheHttpAsyncClientHelper<PRODUCER, WRAPPER, CALLBACK, CALLBACK_WRAPPER, CONTEXT> asyncHelper,
        PRODUCER asyncRequestProducer, CONTEXT context, CALLBACK futureCallback) {

        ElasticContext<?> parentContext = tracer.currentContext();
        if (parentContext.isEmpty()) {
            return null;
        }
        CALLBACK wrappedFutureCallback = futureCallback;
        ElasticContext<?> activeContext = tracer.currentContext();
        Span<?> span = activeContext.createExitSpan();
        if (span != null) {
            span.withType(HttpClientHelper.EXTERNAL_TYPE)
                .withSubtype(HttpClientHelper.HTTP_SUBTYPE)
                .withSync(false)
                .activate();
            wrappedFutureCallback = asyncHelper.wrapFutureCallback(futureCallback, context, span);
        }
        PRODUCER wrappedProducer = asyncHelper.wrapRequestProducer(asyncRequestProducer, span, tracer.currentContext());
        return new Object[]{wrappedProducer, wrappedFutureCallback, span};
    }

    public static <PRODUCER, WRAPPER extends PRODUCER, CALLBACK, CALLBACK_WRAPPER extends CALLBACK, CONTEXT> void endSpan(
        AbstractApacheHttpAsyncClientHelper<PRODUCER, WRAPPER, CALLBACK, CALLBACK_WRAPPER, CONTEXT> asyncHelper, Object[] enter, Throwable t) {
        Span<?> span = enter != null ? (Span<?>) enter[2] : null;
        if (span != null) {
            // Deactivate in this thread
            span.deactivate();
            // End the span if the method terminated with an exception.
            // The exception means that the listener who normally does the ending will not be invoked.
            WRAPPER wrapper = (WRAPPER) enter[0];
            if (t != null) {
                CALLBACK_WRAPPER cb = (CALLBACK_WRAPPER) enter[1];
                // only for apachehttpclient_v4
                asyncHelper.failedWithoutException(cb, t);

                asyncHelper.recycle(wrapper);
            }
        }
    }
}
