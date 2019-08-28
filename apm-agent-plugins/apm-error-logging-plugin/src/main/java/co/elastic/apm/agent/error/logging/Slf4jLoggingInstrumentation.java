package co.elastic.apm.agent.error.logging;

import co.elastic.apm.agent.bci.ElasticApmInstrumentation;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import java.util.Arrays;
import java.util.Collection;

import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

public class Slf4jLoggingInstrumentation extends ElasticApmInstrumentation {

    @Override
    public Class<?> getAdviceClass() {
        return Slf4jLoggingAdviceService.class;
    }

    public static class Slf4jLoggingAdviceService {

        @Advice.OnMethodEnter(suppress = Throwable.class)
        public static void logEnter(@Advice.Argument(1) Throwable exception) {
            if (tracer == null || tracer.getActive() == null) {
                return;
            }
            tracer.getActive().captureException(exception);
        }
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return hasSuperType(named("org.slf4j.Logger"));
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("error")
            .and(takesArgument(0, named("java.lang.String"))
                .and(takesArgument(1, named("java.lang.Throwable"))));
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Arrays.asList("logging", "slf4j");
    }
}
