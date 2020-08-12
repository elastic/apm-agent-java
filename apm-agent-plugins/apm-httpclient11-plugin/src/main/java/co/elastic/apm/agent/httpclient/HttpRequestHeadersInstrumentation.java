package co.elastic.apm.agent.httpclient;

import co.elastic.apm.agent.bci.VisibleForAdvice;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.sdk.advice.AssignTo;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nonnull;
import java.net.http.HttpHeaders;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.nameContains;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;

public class HttpRequestHeadersInstrumentation extends AbstractHttpClientInstrumentation {

    @Override
    public Class<?> getAdviceClass() {
        return HttpRequestHeadersAdvice.class;
    }

    @VisibleForAdvice
    public static class HttpRequestHeadersAdvice {

        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static void onBeforeExecute() {

        }

        @Nonnull
        @AssignTo.Return
        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
        public static HttpHeaders onAfterExecute(@Advice.Return @Nonnull final HttpHeaders httpHeaders) {
            Span span = tracer.getActiveSpan();
            if (span == null) {
                return httpHeaders;
            }
            Map<String, List<String>> headersMap = new LinkedHashMap<>(httpHeaders.map());
            span.propagateTraceContext(headersMap, HttpClientRequestPropertyAccessor.instance());
            return HttpHeaders.of(headersMap, (x, y) -> true);
        }
    }

    @Override
    public ElementMatcher<? super NamedElement> getTypeMatcherPreFilter() {
        return nameContains("HttpRequest");
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return hasSuperType(named("java.net.http.HttpRequest"));
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("headers").and(returns(named("java.net.http.HttpHeaders")));
    }

    @Override
    public boolean indyPlugin() {
        return true;
    }
}
