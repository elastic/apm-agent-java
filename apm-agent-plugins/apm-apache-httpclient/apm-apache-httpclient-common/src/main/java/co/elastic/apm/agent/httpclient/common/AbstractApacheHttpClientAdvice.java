package co.elastic.apm.agent.httpclient.common;

import co.elastic.apm.agent.httpclient.HttpClientHelper;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.GlobalTracer;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Outcome;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.TextHeaderGetter;
import co.elastic.apm.agent.impl.transaction.TextHeaderSetter;
import co.elastic.apm.agent.impl.transaction.TraceContext;

import java.net.URISyntaxException;

public abstract class AbstractApacheHttpClientAdvice {

    public static <RequestObject extends HttpRequest, HttpRequest, HttpHost, CloseableResponse, HeaderAccessor extends TextHeaderSetter<HttpRequest> & TextHeaderGetter<HttpRequest>> Object startSpan(final ApacheHttpClientApiAdapter<RequestObject, HttpRequest, HttpHost, CloseableResponse> adapter,
                                                                                                                                                                                                       final RequestObject request,
                                                                                                                                                                                                       final HttpHost httpHost,
                                                                                                                                                                                                       final HeaderAccessor headerAccessor) throws URISyntaxException {
        ElasticApmTracer tracer = GlobalTracer.getTracerImpl();
        if (tracer == null) {
            return null;
        }
        AbstractSpan<?> parent = tracer.getActive();
        if (parent == null) {
            return null;
        }
        Span span = HttpClientHelper.startHttpClientSpan(parent, adapter.getMethod(request), adapter.getUri(request), adapter.getHostName(httpHost));
        if (span != null) {
            span.activate();
        }
        if (!TraceContext.containsTraceContextTextHeaders(request, headerAccessor)) {
            if (span != null) {
                span.propagateTraceContext(request, headerAccessor);
            } else if (!TraceContext.containsTraceContextTextHeaders(request, headerAccessor)) {
                // re-adds the header on redirects
                parent.propagateTraceContext(request, headerAccessor);
            }
        }
        return span;
    }

    public static <RequestObject extends HttpRequest, HttpRequest, HttpHost, CloseableResponse> void endSpan(Object spanObj,
                                                                                                             Throwable t,
                                                                                                             ApacheHttpClientApiAdapter<RequestObject, HttpRequest, HttpHost, CloseableResponse> adapter,
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
