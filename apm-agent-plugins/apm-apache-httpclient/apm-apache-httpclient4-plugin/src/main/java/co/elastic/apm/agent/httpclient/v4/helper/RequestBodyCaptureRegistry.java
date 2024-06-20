package co.elastic.apm.agent.httpclient.v4.helper;

import co.elastic.apm.agent.httpclient.v4.ApacheHttpClientInstrumentation;
import co.elastic.apm.agent.httpclient.v4.BaseApacheHttpClientInstrumentation;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;
import co.elastic.apm.agent.sdk.state.GlobalState;
import co.elastic.apm.agent.tracer.GlobalTracer;
import co.elastic.apm.agent.tracer.Span;
import co.elastic.apm.agent.tracer.Tracer;
import co.elastic.apm.agent.tracer.configuration.WebConfiguration;
import co.elastic.apm.agent.tracer.reference.ReferenceCountedMap;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpRequest;
import org.apache.http.client.methods.HttpRequestWrapper;

import javax.annotation.Nullable;

public class RequestBodyCaptureRegistry {

    private static final Logger logger = LoggerFactory.getLogger(RequestBodyCaptureRegistry.class);

    private static final Tracer tracer = GlobalTracer.get();

    @GlobalState
    public static class MapHolder {
        private static final ReferenceCountedMap<Object, Span<?>> entityToClientSpan = GlobalTracer.get().newReferenceCountedMap();


        public static void captureBodyFor(Object entity, Span<?> httpClientSpan) {
            entityToClientSpan.put(entity, httpClientSpan);
        }

        @Nullable
        public static Span<?> removeSpanFor(Object entity) {
            return entityToClientSpan.remove(entity);
        }
    }


    public static void potentiallyCaptureRequestBody(HttpRequest request, @Nullable Span<?> span) {
        if (span != null && span.isSampled() && !tracer.getConfig(WebConfiguration.class).getCaptureClientRequestContentTypes().isEmpty()) {
            if (request instanceof HttpEntityEnclosingRequest) {
                HttpEntity entity = ((HttpEntityEnclosingRequest) request).getEntity();
                if (entity != null) {
                    logger.debug("Enabling request capture for entity {}() for span {}", entity.getClass().getName(), System.identityHashCode(entity), span);
                    MapHolder.captureBodyFor(entity, span);
                } else {
                    logger.debug("HttpEntity is null for span {}", span);
                }

            } else {
                logger.debug("Not capturing request body because {} is not an HttpEntityEnclosingRequest", request.getClass().getName());
            }
        }
    }

    @Nullable
    public static Span<?> removeSpanFor(HttpEntity entity) {
        return MapHolder.removeSpanFor(entity);
    }

}
