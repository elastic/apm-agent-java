package co.elastic.apm.agent.quartzjob;

import co.elastic.apm.agent.bci.bytebuddy.SimpleMethodSignatureOffsetMappingFactory;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.quartz.JobExecutionContext;

import javax.annotation.Nullable;

import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

public class Quartz1JobTransactionNameInstrumentation extends AbstractJobTransactionNameInstrumentation {
    public Quartz1JobTransactionNameInstrumentation(ElasticApmTracer tracer) {
        super(tracer);
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("execute").or(named("executeInternal"))
            .and(takesArgument(0, named("org.quartz.JobExecutionContext").and(not(isInterface()))));
    }

    public static class AdviceClass extends BaseAdvice {
        private static final JobExecutionContextHandler helper;

        static {
            helper = new Quartz1JobExecutionContextHandler();
        }

        @Nullable
        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static Object setTransactionName(@Advice.Argument(value = 0) @Nullable JobExecutionContext jobExecutionContext,
                                                @SimpleMethodSignatureOffsetMappingFactory.SimpleMethodSignature String signature,
                                                @Advice.Origin Class<?> clazz) {
            return BaseAdvice.createAndActivateTransaction(jobExecutionContext, signature, clazz, helper);
        }

        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
        public static void onMethodExitException(@Advice.Argument(value = 0) @Nullable JobExecutionContext jobExecutionContext,
                                                 @Advice.Enter @Nullable Object transactionObj,
                                                 @Advice.Thrown @Nullable Throwable t) {
            BaseAdvice.endTransaction(jobExecutionContext, transactionObj, t, helper);
        }
    }
}
