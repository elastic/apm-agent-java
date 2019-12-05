package co.elastic.apm.agent.process;

import co.elastic.apm.agent.bci.ElasticApmInstrumentation;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import java.util.Arrays;
import java.util.Collection;

import static net.bytebuddy.matcher.ElementMatchers.hasSuperClass;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

/**
 * Provides context propagation for apache commons-exec library that delegates to a background thread for
 * asynchronous process execution. Synchronous execution is already covered with {@link Process} instrumentation.
 * <p>
 * Instruments {@code org.apache.commons.exec.DefaultExecutor#createThread(Runnable, String)} and any direct subclass
 * that overrides it.
 */
public class CommonsExecAsyncInstrumentation extends ElasticApmInstrumentation {

    private static final String DEFAULT_EXECUTOR_CLASS = "org.apache.commons.exec.DefaultExecutor";

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        // instrument default implementation and direct subclasses
        return named(DEFAULT_EXECUTOR_CLASS)
            .or(hasSuperClass(named(DEFAULT_EXECUTOR_CLASS)));
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("createThread")
            .and(takesArgument(0, Runnable.class));
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        // part of 'process' group, as usage is not relevant without it, relies on generic Process instrumentation
        return Arrays.asList("apache-commons-exec", "process", "incubating");
    }

    @Override
    public Class<?> getAdviceClass() {
        return CommonsExecAdvice.class;
    }

    public static final class CommonsExecAdvice {

        @Advice.OnMethodEnter(suppress = Throwable.class)
        private static void onEnter(@Advice.Argument(value = 0, readOnly = false) Runnable runnable) {
            if (tracer == null || tracer.getActive() == null) {
                return;
            }
            // context propagation is done by wrapping existing runnable argument

            //noinspection UnusedAssignment
            runnable = tracer.getActive().withActive(runnable);
        }
    }
}
