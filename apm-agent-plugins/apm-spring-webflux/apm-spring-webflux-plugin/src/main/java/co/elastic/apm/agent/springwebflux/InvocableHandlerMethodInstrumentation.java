package co.elastic.apm.agent.springwebflux;

import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.sdk.advice.AssignTo;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.springframework.web.reactive.HandlerResult;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.annotation.Nullable;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

/**
 * Instruments {@link org.springframework.web.reactive.result.method.InvocableHandlerMethod#invoke} to activate
 * transaction before result Mono is built.
 */
public class InvocableHandlerMethodInstrumentation extends WebFluxInstrumentation {

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("org.springframework.web.reactive.result.method.InvocableHandlerMethod");
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("invoke")
            .and(takesArgument(0, named("org.springframework.web.server.ServerWebExchange")))
            .and(returns(named("reactor.core.publisher.Mono")));
    }

    @Nullable
    @AssignTo.Return
    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
    public static Mono<HandlerResult> onExit(@Advice.Thrown @Nullable Throwable thrown,
                                             @Advice.Argument(0) ServerWebExchange exchange,
                                             @Advice.Return @Nullable Mono<HandlerResult> resultMono) {

        Object attribute = exchange.getAttribute(TRANSACTION_ATTRIBUTE);
        if (!(attribute instanceof Transaction)) {
            return resultMono;
        }
        Transaction transaction = (Transaction) attribute;

        if (resultMono == null || thrown != null) {
            // no need to wrap in case of exception
            return resultMono;
        }

        return wrapInvocableHandlerMethod(resultMono, transaction, exchange);
    }
}
