package co.elastic.apm.agent.error.logging;

import co.elastic.apm.agent.bci.ElasticApmInstrumentation;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import java.util.Arrays;
import java.util.Collection;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

public class JavaUtilLoggingInstrumentation extends ElasticApmInstrumentation {

    @Override
    public Class<?> getAdviceClass() {
        return JavaUtlLoggingAdviceService.class;
    }

    public static class JavaUtlLoggingAdviceService {

        @Advice.OnMethodEnter(suppress = Throwable.class)
        public static void logThrown(@Advice.Argument(0) Throwable exception) {
            if (tracer == null || tracer.getActive() == null) {
                return;
            }
            tracer.getActive().captureException(exception);
        }
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("java.util.logging.LogRecord");
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("setThrown")
            .and(takesArgument(0, named("java.lang.Throwable")));
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Arrays.asList("logging", "java-util-logging");
    }

}
