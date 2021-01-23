package co.elastic.apm.agent.opentelemetry;

import co.elastic.apm.agent.bci.TracerAwareInstrumentation;
import co.elastic.apm.agent.impl.GlobalTracer;
import co.elastic.apm.agent.opentelemetry.context.ElasticOTelContextStorage;
import co.elastic.apm.agent.sdk.advice.AssignTo;
import io.opentelemetry.context.ContextStorage;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import java.util.Arrays;
import java.util.Collection;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;

public class ContextStorageInstrumentation extends TracerAwareInstrumentation {

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("io.opentelemetry.context.ContextStorage");
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("get").and(returns(named("io.opentelemetry.context.ContextStorage")));
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Arrays.asList("opentelemetry", "experimental");
    }

    @Override
    public boolean includeWhenInstrumentationIsDisabled() {
        return true;
    }

    @Override
    public Class<?> getAdviceClass() {
        return ContextStorageAdvice.class;
    }

    public static class ContextStorageAdvice {

        private static final ElasticOTelContextStorage CONTEXT_STORAGE = new ElasticOTelContextStorage(GlobalTracer.requireTracerImpl());

        @AssignTo.Return
        @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
        public static ContextStorage onExit() {
            return CONTEXT_STORAGE;
        }
    }
}
