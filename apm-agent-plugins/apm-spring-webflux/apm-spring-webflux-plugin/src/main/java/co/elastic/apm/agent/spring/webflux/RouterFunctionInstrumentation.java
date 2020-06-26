package co.elastic.apm.agent.spring.webflux;

import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.springframework.web.reactive.function.server.HandlerFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import reactor.core.publisher.Mono;

import javax.annotation.Nullable;

import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

/**
 * Instruments {@link org.springframework.web.reactive.function.server.RouterFunction#route(ServerRequest)} to capture
 * path template without parameters for functional routes.
 */
public class RouterFunctionInstrumentation extends WebFluxInstrumentation {

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return nameStartsWith("org.springframework.web.reactive.function.server")
            .and(hasSuperType(named("org.springframework.web.reactive.function.server.RouterFunction")))
            .and(not(isInterface()));
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("route")
            .and(takesArgument(0, named("org.springframework.web.reactive.function.server.ServerRequest")));
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
    private static void onExit(@Advice.Thrown @Nullable Throwable thrown,
                               @Advice.Argument(0) ServerRequest request,
                               @Advice.Return(readOnly = false) Mono<HandlerFunction<?>> returnValue) {
        if (tracer == null || thrown != null) {
            return;
        }

        returnValue = WebFluxInstrumentation.setNameOnComplete(returnValue, request.exchange());
    }
}
