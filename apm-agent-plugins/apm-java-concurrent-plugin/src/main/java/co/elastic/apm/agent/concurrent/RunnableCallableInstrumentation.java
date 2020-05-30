package co.elastic.apm.agent.concurrent;

import co.elastic.apm.agent.bci.ElasticApmInstrumentation;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.Callable;

import static net.bytebuddy.matcher.ElementMatchers.any;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

/**
 * Used only within {@link JavaConcurrent#withContext} to
 * {@linkplain co.elastic.apm.agent.bci.ElasticApmAgent#ensureInstrumented(Class, Collection) ensure}
 * that particular {@link Callable} or {@link Runnable} classes are instrumented.
 */
public class RunnableCallableInstrumentation extends ElasticApmInstrumentation {

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return any();
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("run").and(takesArguments(0))
            .or(named("call").and(takesArguments(0)));
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Arrays.asList("concurrent", "executor");
    }

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class)
    private static AbstractSpan<?> onEnter(@Advice.This Object thiz) {
        return JavaConcurrent.restoreContext(thiz, tracer);
    }


    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
    private static void onExit(@Advice.Thrown Throwable thrown,
                               @Nullable @Advice.Enter AbstractSpan<?> span) {
        if (span != null) {
            span.deactivate();
        }
    }
}
