package co.elastic.apm.agent.spring.webflux;

import co.elastic.apm.agent.bci.ElasticApmInstrumentation;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.springframework.web.reactive.function.server.HandlerFunction;
import org.springframework.web.reactive.function.server.RequestPredicate;
import org.springframework.web.reactive.function.server.RouterFunctions;

import java.util.Collection;
import java.util.Collections;

import static net.bytebuddy.matcher.ElementMatchers.*;


public class FunctionalHandlerInstrumentation extends ElasticApmInstrumentation {

    @SuppressWarnings("unused")
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onHandlerAdd(@Advice.Argument(readOnly = false, value = 0) RequestPredicate predicate,
                                    @Advice.Argument(readOnly = false, value = 1) HandlerFunction handlerFunction) {

        //already wrapped
        if (handlerFunction instanceof HandlerFunctionWrapper) {
            return;
        }
        handlerFunction = new HandlerFunctionWrapper(handlerFunction);
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return hasSuperType(is(RouterFunctions.Builder.class));
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("add")
            .and(takesArguments(RequestPredicate.class, HandlerFunction.class));
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Collections.singletonList("webflux-functional-handler");
    }
}
