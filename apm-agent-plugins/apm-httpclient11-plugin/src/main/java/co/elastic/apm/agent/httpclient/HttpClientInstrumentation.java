package co.elastic.apm.agent.httpclient;

import co.elastic.apm.agent.bci.VisibleForAdvice;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.sdk.advice.AssignTo;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;

public class HttpClientInstrumentation extends AbstractHttpClientInstrumentation {

    @Override
    public Class<?> getAdviceClass() {
        return HttpClient11Advice.class;
    }

    @VisibleForAdvice
    public static class HttpClient11Advice {

        @Nullable
        @AssignTo.Argument(value = 0, typing = Assigner.Typing.DYNAMIC)
        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static Object onBeforeExecute(@Advice.Argument(value = 0) HttpRequest httpRequest) {
            if (tracer.getActive() == null) {
                return httpRequest;
            }
            final AbstractSpan<?> parent = tracer.getActive();
            URI uri = httpRequest.uri();
            Span span = co.elastic.apm.agent.http.client.HttpClientHelper.startHttpClientSpan(parent, httpRequest.method(), uri.toString(), uri.getScheme(),
                HttpClientHelper.computeHostName(uri.getHost()), uri.getPort());
            if (span != null) {
                span.activate();
                HttpRequest.Builder builder = HttpRequest.newBuilder(httpRequest.uri())
                    .method(httpRequest.method(), httpRequest.bodyPublisher().orElse(HttpRequest.BodyPublishers.noBody()))
                    .expectContinue(httpRequest.expectContinue());
                if (httpRequest.timeout().isPresent()) {
                    builder = builder.timeout(httpRequest.timeout().get());
                }
                if (httpRequest.version().isPresent()) {
                    builder = builder.version(httpRequest.version().get());
                }
                for (String header : httpRequest.headers().map().keySet()) {
                    builder.header(header, httpRequest.headers().firstValue(header).orElse(null));
                }
                span.propagateTraceContext(builder, HttpClientRequestPropertyAccessor.instance());
                return builder.build();
            }
            return httpRequest;
        }

        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
        public static void onAfterExecute(@Advice.Return @Nullable HttpResponse response,
                                          @Advice.Thrown @Nullable Throwable t) {
            final Span span = tracer.getActiveExitSpan();
            if (span != null) {
                try {
                    if (response != null) {
                        int statusCode = response.statusCode();
                        span.getContext().getHttp().withStatusCode(statusCode);
                    }
                    span.captureException(t);
                } finally {
                    span.deactivate().end();
                }
            }
        }
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("jdk.internal.net.http.HttpClientImpl");
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("send").and(returns(named("java.net.http.HttpResponse")));
    }

    @Override
    public boolean indyPlugin() {
        return true;
    }
}
