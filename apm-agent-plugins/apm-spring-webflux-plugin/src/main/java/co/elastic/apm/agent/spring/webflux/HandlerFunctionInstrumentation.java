package co.elastic.apm.agent.spring.webflux;

import co.elastic.apm.agent.bci.ElasticApmInstrumentation;
import co.elastic.apm.agent.impl.transaction.Transaction;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.springframework.web.reactive.function.server.ServerRequest;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;

import static net.bytebuddy.matcher.ElementMatchers.*;

public class HandlerFunctionInstrumentation extends ElasticApmInstrumentation {

    @SuppressWarnings("unused")
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void beforeRequestHandle(@Advice.Argument(value = 0) ServerRequest serverRequest,
                                           @Advice.Local("transaction") @Nullable Transaction transaction) {

        if (tracer == null) {
            return;
        }
        if (tracer.getActive() != null) {
            return;
        }
        if (serverRequest instanceof ServerRequestWrapper) {
            final ServerRequestWrapper wrapper = (ServerRequestWrapper) serverRequest;
            //if not null than this request was delegated to another handler
            if (wrapper.getTransaction() == null) {
                transaction = WebFluxInstrumentationHelper.createAndActivateTransaction(tracer, wrapper);
            }
        }
    }

    @SuppressWarnings("unused")
    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
    public static void afterRequestHandle(@Advice.Argument(value = 0) ServerRequest serverRequest,
                                          @Advice.Local("transaction") @Nullable Transaction transaction,
                                          @Advice.Thrown @Nullable Throwable t) {

        if (transaction != null) {
            transaction.captureException(t)
                .deactivate()
                .end();
        }
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return is(HandlerFunctionWrapper.class);
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("handle")
            .and(takesArguments(ServerRequest.class));
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Collections.singletonList("webflux-wrapper-handler");
    }
}
