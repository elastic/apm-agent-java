package co.elastic.apm.agent.httpclient.common;

import co.elastic.apm.agent.httpclient.HttpClientHelper;
import co.elastic.apm.agent.impl.Tracer;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Outcome;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.TextHeaderGetter;
import co.elastic.apm.agent.impl.transaction.TextHeaderSetter;
import co.elastic.apm.agent.impl.transaction.TraceContext;

import java.net.URISyntaxException;

public abstract class ApacheHttpClientAdvice {

    public static <RequestObject, HttpHost, CloseableResponse> Object startSpan(Tracer tracer,
                                                                                ApacheHttpClientApiAdapter<RequestObject, HttpHost, CloseableResponse> adapter,
                                                                                RequestObject request, HttpHost httpHost,
                                                                                TextHeaderGetter<RequestObject> requestHeaderGetter,
                                                                                TextHeaderSetter<RequestObject> requestHeaderSetter) throws URISyntaxException {
        AbstractSpan<?> parent = tracer.getActive();
        if (parent == null) {
            return null;
        }
        Span span = HttpClientHelper.startHttpClientSpan(parent, adapter.getMethod(request), adapter.getUri(request), adapter.getHostName(httpHost));
        if (span != null) {
            span.activate();
        }
        if (!TraceContext.containsTraceContextTextHeaders(request, requestHeaderGetter)) {
            if (span != null) {
                span.propagateTraceContext(request, requestHeaderSetter);
            } else if (!TraceContext.containsTraceContextTextHeaders(request, requestHeaderGetter)) {
                // re-adds the header on redirects
                parent.propagateTraceContext(request, requestHeaderSetter);
            }
        }
        return span;
    }

    public static <RequestObject, HttpHost, CloseableResponse> void endSpan(Object spanObj,
                                                                            Throwable t,
                                                                            ApacheHttpClientApiAdapter<RequestObject, HttpHost, CloseableResponse> adapter,
                                                                            CloseableResponse response) {
        Span span = (Span) spanObj;
        if (span == null) {
            return;
        }
        try {
            if (response != null) {
                int statusCode = adapter.getResponseCode(response);
                span.getContext().getHttp().withStatusCode(statusCode);
            }
            span.captureException(t);
        } finally {
            if (adapter.isCircularRedirectException(t)) {
                span.withOutcome(Outcome.FAILURE);
            }

            span.deactivate().end();
        }
    }
}
