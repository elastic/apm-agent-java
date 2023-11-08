package co.elastic.apm.agent.httpclient.common;


import co.elastic.apm.agent.httpclient.HttpClientHelper;
import co.elastic.apm.agent.tracer.ElasticContext;
import co.elastic.apm.agent.tracer.Outcome;
import co.elastic.apm.agent.tracer.Span;
import co.elastic.apm.agent.tracer.Tracer;
import co.elastic.apm.agent.tracer.dispatch.TextHeaderGetter;
import co.elastic.apm.agent.tracer.dispatch.TextHeaderSetter;

import java.net.URISyntaxException;

public abstract class AbstractApacheHttpClientAdvice {

    public static <RequestObject extends HttpRequest, HttpRequest, HttpHost, CloseableResponse, StatusLine, HeaderAccessor extends TextHeaderSetter<HttpRequest> & TextHeaderGetter<HttpRequest>> Object startSpan(final Tracer tracer,
                                                                                                                                                                                                                   final ApacheHttpClientApiAdapter<RequestObject, HttpRequest, HttpHost, CloseableResponse, StatusLine> adapter,
                                                                                                                                                                                                                   final RequestObject request,
                                                                                                                                                                                                                   final HttpHost httpHost,
                                                                                                                                                                                                                   final HeaderAccessor headerAccessor) throws URISyntaxException {
        ElasticContext<?> elasticContext = tracer.currentContext();
        Span<?> span = null;
        if (elasticContext.getSpan() != null) {
            span = HttpClientHelper.startHttpClientSpan(elasticContext, adapter.getMethod(request), adapter.getUri(request), adapter.getHostName(httpHost));
            if (span != null) {
                span.activate();
            }
        }
        tracer.currentContext().propagateContext(request, headerAccessor, headerAccessor);
        return span;
    }

    public static <RequestObject extends HttpRequest, HttpRequest, HttpHost, CloseableResponse, StatusLine> void endSpan(ApacheHttpClientApiAdapter<RequestObject, HttpRequest, HttpHost, CloseableResponse, StatusLine> adapter,
                                                                                                                         Object spanObj,
                                                                                                                         Throwable t,
                                                                                                                         CloseableResponse response) {
        Span<?> span = (Span<?>) spanObj;
        if (span == null) {
            return;
        }
        try {
            if (response != null && adapter.getStatusLine(response) != null) {
                int statusCode = adapter.getResponseCode(response);
                span.getContext().getHttp().withStatusCode(statusCode);
            }
            span.captureException(t);
        } finally {
            // in case of circular redirect, we get an exception but status code won't be available without response
            // thus we have to deal with span outcome directly
            if (adapter.isCircularRedirectException(t)) {
                span.withOutcome(Outcome.FAILURE);
            }
            span.deactivate().end();
        }
    }
}
