package co.elastic.apm.agent.httpclient;

import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Span;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

public class HttpClientAsyncInstrumentation extends AbstractHttpClientInstrumentation {

    @Override
    public Class<?> getAdviceClass() {
        return HttpClient11Advice.class;
    }

    public static class HttpClient11Advice {

        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static void onBeforeExecute(@Advice.Argument(value = 0) HttpRequest httpRequest) {
            if (tracer.getActive() == null) {
                return;
            }
            final AbstractSpan<?> parent = tracer.getActive();
            URI uri = httpRequest.uri();
            Span span = co.elastic.apm.agent.http.client.HttpClientHelper.startHttpClientSpan(parent, httpRequest.method(), uri.toString(), uri.getScheme(),
                HttpClientHelper.computeHostName(uri.getHost()), uri.getPort());
            if (span != null) {
                span.activate();
            }
        }

        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
        public static void onAfterExecute(@Advice.Return @Nullable CompletableFuture completableFuture,
                                          @Advice.Thrown @Nullable Throwable t) {
            final Span activeSpan = tracer.getActiveExitSpan();
            if (activeSpan == null) {
                return;
            }
            activeSpan.deactivate();
            BiConsumer<HttpResponse, Throwable> callback = (response, throwable) -> {
                try {
                    if (response != null) {
                        int statusCode = response.statusCode();
                        activeSpan.getContext().getHttp().withStatusCode(statusCode);
                    }
                    activeSpan.captureException(throwable);
                } finally {
                    activeSpan.end();
                }
            };
            if (completableFuture != null) {
                completableFuture.whenComplete(callback);
            } else {
                activeSpan.captureException(t)
                    .end();
            }
        }
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return hasSuperType(named("java.net.http.HttpClient"));
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("sendAsync")
            .and(returns(named("java.util.concurrent.CompletableFuture")))
            .and(takesArguments(3));
    }

    @Override
    public boolean indyPlugin() {
        return true;
    }
}
