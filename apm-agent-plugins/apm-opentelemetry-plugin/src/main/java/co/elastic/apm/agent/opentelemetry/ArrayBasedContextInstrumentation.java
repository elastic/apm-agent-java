package co.elastic.apm.agent.opentelemetry;

import co.elastic.apm.agent.impl.GlobalTracer;
import co.elastic.apm.agent.opentelemetry.sdk.OTelBridgeContext;
import co.elastic.apm.agent.sdk.advice.AssignTo;
import io.opentelemetry.context.Context;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;

import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isStatic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesNoArguments;

/**
 * Instruments {@code io.opentelemetry.context.ArrayBasedContext#root()} to capture original context root
 * and allow relying ot the provided context implementation for key/value storage in context
 */
public class ArrayBasedContextInstrumentation extends AbstractOpenTelemetryInstrumentation {

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("io.opentelemetry.context.ArrayBasedContext");
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("root")
            .and(isStatic())
            .and(returns(hasSuperType(named("io.opentelemetry.context.Context"))))
            .and(takesNoArguments());
    }

    @Override
    public String getAdviceClassName() {
        return "co.elastic.apm.agent.opentelemetry.ArrayBasedContextInstrumentation$RootAdvice";
    }

    public static class RootAdvice {

        @Nullable
        @AssignTo.Return
        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
        public static Context onExit(@Advice.Return @Nullable Context returnValue) {

            if (returnValue == null) {
                return null;
            }

            return OTelBridgeContext.bridgeRootContext(GlobalTracer.requireTracerImpl(), returnValue);
        }
    }
}
