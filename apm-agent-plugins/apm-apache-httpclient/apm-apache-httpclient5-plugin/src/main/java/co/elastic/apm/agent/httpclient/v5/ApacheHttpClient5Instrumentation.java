package co.elastic.apm.agent.httpclient.v5;

import co.elastic.apm.agent.db.signature.SignatureParser;
import co.elastic.apm.agent.httpclient.HttpClientHelper;
import co.elastic.apm.agent.httpclient.v5.helper.RequestHeaderAccessor;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.TraceContext;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.protocol.HttpContext;

import javax.annotation.Nullable;

import java.net.URISyntaxException;

import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.nameContains;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

public class ApacheHttpClient5Instrumentation extends BaseApacheHttpClient5Instrumentation {

    public static class HttpClient5Advice {

        @Nullable
        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static Object onBeforeExecute(@Advice.Argument(0) HttpHost httpHost,
                                             @Advice.Argument(1) ClassicHttpRequest request,
                                             @Advice.Argument(2) HttpContext context) throws URISyntaxException {
            AbstractSpan<?> parent = tracer.getActive();
            if (parent == null) {
                return null;
            }
            Span span = HttpClientHelper.startHttpClientSpan(parent, request.getMethod(), request.getUri(), httpHost.getHostName());
            if (span != null) {
                span.activate();
            }
            if (!TraceContext.containsTraceContextTextHeaders(request, RequestHeaderAccessor.INSTANCE)) {
                if (span != null) {
                    span.propagateTraceContext(request, RequestHeaderAccessor.INSTANCE);
                } else if (!TraceContext.containsTraceContextTextHeaders(request, RequestHeaderAccessor.INSTANCE)) {
                    // re-adds the header on redirects
                    parent.propagateTraceContext(request, RequestHeaderAccessor.INSTANCE);
                }
            }
            return span;
        }
    }

    @Override
    public String getAdviceClassName() {
        return "co.elastic.apm.agent.httpclient.v5.ApacheHttpClient5Instrumentation$HttpClient5Advice";
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return hasSuperType(named("org.apache.hc.client5.http.impl.classic.CloseableHttpClient"));
    }

    @Override
    public ElementMatcher<? super NamedElement> getTypeMatcherPreFilter() {
        return nameContains("HttpClient");
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("doExecute")
            .and(takesArguments(4))
            .and(returns(hasSuperType(named("org.apache.hc.client5.http.impl.classic.CloseableHttpResponse"))))
            .and(takesArgument(0, hasSuperType(named("org.apache.hc.core5.http.HttpHost"))))
            .and(takesArgument(1, hasSuperType(named("org.apache.hc.core5.http.ClassicHttpRequest"))))
            .and(takesArgument(2, hasSuperType(named("org.apache.hc.core5.http.protocol.HttpContext"))));
    }
}
