package co.elastic.apm.agent.ecs_logging.logback;

import co.elastic.apm.agent.ecs_logging.EcsLoggingInstrumentation;
import co.elastic.apm.agent.ecs_logging.EcsLoggingUtils;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.GlobalTracer;
import co.elastic.logging.logback.EcsEncoder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;

public class LogbackServiceNameInstrumentation extends EcsLoggingInstrumentation {

    @Override
    public ElementMatcher.Junction<? super TypeDescription> getTypeMatcher() {
        return named("co.elastic.logging.logback.EcsEncoder");
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return isConstructor();
    }

    public static class AdviceClass {

        private static final ElasticApmTracer tracer = GlobalTracer.requireTracerImpl();

        @Advice.OnMethodExit(inline = false)
        public static void onExit(@Advice.This EcsEncoder ecsFormatter) {
            ecsFormatter.setServiceName(EcsLoggingUtils.getServiceName(tracer));
        }
    }
}
