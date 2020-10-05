package co.elastic.apm.agent.resttemplate;

import co.elastic.apm.agent.bci.TracerAwareInstrumentation;
import co.elastic.apm.agent.http.client.HttpClientHelper;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Span;
import net.bytebuddy.asm.Advice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpResponse;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.Objects;

public class SpringRestTemplateAdvice {

    private static final Logger logger = LoggerFactory.getLogger(SpringRestTemplateAdvice.class);

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Object beforeExecute(@Advice.This ClientHttpRequest request) {
        logger.trace("Enter advice for method {}#execute()", request.getClass().getName());
        if (TracerAwareInstrumentation.tracer.getActive() == null) {
            return null;
        }
        final AbstractSpan<?> parent = TracerAwareInstrumentation.tracer.getActive();
        Span span = HttpClientHelper.startHttpClientSpan(parent, Objects.toString(request.getMethod()), request.getURI(), request.getURI().getHost());
        if (span != null) {
            span.activate();
            span.propagateTraceContext(request, SpringRestRequestHeaderSetter.INSTANCE);
            return span;
        }
        return null;
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
    public static void afterExecute(@Advice.Return @Nullable ClientHttpResponse clientHttpResponse,
                                    @Advice.Enter @Nullable Object spanObj,
                                    @Advice.Thrown @Nullable Throwable t) throws IOException {
        logger.trace("Exit advice for RestTemplate client execute() method, span object: {}", spanObj);
        if (spanObj instanceof Span) {
            Span span = (Span) spanObj;
            try {
                if (clientHttpResponse != null) {
                    int statusCode = clientHttpResponse.getRawStatusCode();
                    span.getContext().getHttp().withStatusCode(statusCode);
                }
                span.captureException(t);
            } finally {
                span.deactivate().end();
            }
        }
    }
}
