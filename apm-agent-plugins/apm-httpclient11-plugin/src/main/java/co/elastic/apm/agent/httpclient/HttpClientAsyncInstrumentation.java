package co.elastic.apm.agent.httpclient;

import co.elastic.apm.agent.bci.VisibleForAdvice;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.sdk.DynamicTransformer;
import co.elastic.apm.agent.sdk.advice.AssignTo;
import co.elastic.apm.agent.sdk.state.GlobalThreadLocal;
import co.elastic.apm.agent.sdk.weakmap.WeakMapSupplier;
import com.blogspot.mydailyjava.weaklockfree.WeakConcurrentMap;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;

import static net.bytebuddy.matcher.ElementMatchers.any;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

public class HttpClientAsyncInstrumentation extends AbstractHttpClientInstrumentation {

    @Override
    public Class<?> getAdviceClass() {
        return HttpClient11Advice.class;
    }

    @VisibleForAdvice
    public static final WeakConcurrentMap<CompletableFuture<?>, Span> handlerSpanMap = WeakMapSupplier.createMap();

    @VisibleForAdvice
    public static class HttpClient11Advice {

        @VisibleForAdvice
        public final static GlobalThreadLocal<Span> spanTls = GlobalThreadLocal.get(HttpClient11Advice.class, "spanTls");

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
                spanTls.set(span);
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
                return builder.build();
            }
            return httpRequest;
        }

        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
        public static void onAfterExecute(@Advice.Return @Nullable HttpResponse response,
                                          @Advice.Thrown @Nullable Throwable t) {
            final Span span = spanTls.getAndRemove();
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
        return named("sendAsync").and(returns(named("java.util.concurrent.CompletableFuture")));
    }

    @Override
    public boolean indyPlugin() {
        return true;
    }

    public abstract static class AbstractCompletableFutureInstrumentation extends HttpClientAsyncInstrumentation {
        private final ElementMatcher<? super MethodDescription> methodMatcher;

        protected AbstractCompletableFutureInstrumentation(ElasticApmTracer tracer, ElementMatcher<? super MethodDescription> methodMatcher) {
            this.methodMatcher = methodMatcher;
        }

        /**
         * Overridden in {@link DynamicTransformer#ensureInstrumented(Class, Collection)},
         * based on the type of the {@linkplain java.util.concurrent.CompletableFuture} implementation class.
         */
        @Override
        public ElementMatcher<? super TypeDescription> getTypeMatcher() {
            return any();
        }

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return methodMatcher;
        }
    }

    public static class CompletableFutureReportGetInstrumentation extends AbstractCompletableFutureInstrumentation {

        public CompletableFutureReportGetInstrumentation(ElasticApmTracer tracer) {
            super(tracer, named("reportGet").and(takesArguments(Throwable.class)));
        }

        @Advice.OnMethodEnter(suppress = Throwable.class)
        private static void onMethodEnter(@Advice.This CompletableFuture<?> completableFuture, @Advice.Local("span") Span span) {
            span = handlerSpanMap.remove(completableFuture);
            if (span != null) {
                span.activate();
            }
        }

        @Advice.OnMethodExit(suppress = Throwable.class)
        private static void onMethodExit(@Nullable @Advice.Local("span") Span span, @Advice.Argument(0) Throwable t) {
            if (span != null) {
                span.captureException(t).end();
                span.deactivate();
            }
        }
    }

}
