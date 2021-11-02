package co.elastic.apm.agent.webfluxclient;

import co.elastic.apm.agent.http.client.HttpClientHelper;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Span;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned.ToArguments.ToArgument;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import net.bytebuddy.matcher.ElementMatcher;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.client.ClientRequest;

import javax.annotation.Nullable;
import java.net.URI;

import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

public class WebClientExchangeFunctionInstrumentation extends AbstractWebClientInstrumentation {

    private static final Logger logger = LoggerFactory.getLogger(WebClientExchangeFunctionInstrumentation.class);

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return hasSuperType(named("org.springframework.web.reactive.function.client.ExchangeFunction"))
            .and(not(isInterface()));
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("exchange")
            .and(takesArgument(0, named("org.springframework.web.reactive.function.client.ClientRequest")));
    }

    public static class AdviceClass {

        @Nullable
        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        @Advice.AssignReturned.ToArguments(@ToArgument(index = 0, value = 0, typing = Assigner.Typing.DYNAMIC))
        public static Object[] onBefore(@Advice.Argument(0) ClientRequest clientRequest) {
            final AbstractSpan<?> parent = tracer.getActive();
            if (parent == null) {
                return null;
            }
            ClientRequest.Builder builder = ClientRequest.from(clientRequest);
            URI uri = clientRequest.url();
            Span span = HttpClientHelper.startHttpClientSpan(parent, clientRequest.method().name(), uri, uri.getHost());
            if (span != null) {
                span.activate();
                span.propagateTraceContext(builder, WebClientRequestHeaderSetter.INSTANCE);
            } else {
                parent.propagateTraceContext(builder, WebClientRequestHeaderSetter.INSTANCE);
            }
            clientRequest = builder.build();
            return new Object[]{clientRequest, span};
        }


        @Advice.AssignReturned.ToReturned(typing = Assigner.Typing.DYNAMIC)
        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
        public static Object afterExecute(@Advice.Return @Nullable Publisher returnValue,
                                          @Advice.Enter @Nullable Object[] spanRequestObj,
                                          @Advice.Thrown @Nullable Throwable t) {
            if (spanRequestObj == null || spanRequestObj.length < 2) {
                return returnValue;
            }
            Object spanObj = spanRequestObj[1];
            if (!(spanObj instanceof Span)) {
                return returnValue;
            }
            Span span = (Span) spanObj;
            span = span.captureException(t).deactivate();
            if (t != null || returnValue == null) {
                return returnValue;
            }
            return WebfluxClientHelper.wrapSubscriber(returnValue, span, tracer);
        }
    }
}
