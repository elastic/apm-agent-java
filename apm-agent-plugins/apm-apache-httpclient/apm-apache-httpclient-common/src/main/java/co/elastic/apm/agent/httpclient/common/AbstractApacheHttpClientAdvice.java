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

    public static <REQUEST, WRAPPER extends REQUEST, HTTPHOST, RESPONSE,
        HeaderAccessor extends TextHeaderSetter<REQUEST> &
            TextHeaderGetter<REQUEST>> Object startSpan(final Tracer tracer,
                                                        final ApacheHttpClientApiAdapter<REQUEST, WRAPPER, HTTPHOST, RESPONSE> adapter,
                                                        final WRAPPER request,
                                                        final HTTPHOST httpHost,
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

    public static <REQUEST, WRAPPER extends REQUEST, HTTPHOST, RESPONSE> void endSpan(ApacheHttpClientApiAdapter<REQUEST, WRAPPER, HTTPHOST, RESPONSE> adapter,
                                                                                      Object spanObj,
                                                                                      Throwable t,
                                                                                      RESPONSE response) {
        Span<?> span = (Span<?>) spanObj;
        if (span == null) {
            return;
        }
        try {
            if (response != null && adapter.isNotNullStatusLine(response)) {
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
