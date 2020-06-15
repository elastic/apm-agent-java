package co.elastic.apm.agent.spring.scheduled;

import co.elastic.apm.agent.bci.ElasticApmInstrumentation;
import co.elastic.apm.agent.bci.VisibleForAdvice;
import co.elastic.apm.agent.bci.bytebuddy.SimpleMethodSignatureOffsetMappingFactory;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.stacktrace.StacktraceConfiguration;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Transaction;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collection;

import static co.elastic.apm.agent.bci.bytebuddy.CustomElementMatchers.isInAnyPackage;
import static net.bytebuddy.matcher.ElementMatchers.hasSuperClass;
import static net.bytebuddy.matcher.ElementMatchers.named;

public class TimerTaskInstrumentation extends ElasticApmInstrumentation {

    @VisibleForAdvice
    public static final Logger logger = LoggerFactory.getLogger(TimerTaskInstrumentation.class);

    private final Collection<String> applicationPackages;

    public TimerTaskInstrumentation(ElasticApmTracer tracer) {
        applicationPackages = tracer.getConfig(StacktraceConfiguration.class).getApplicationPackages();
    }

    @Advice.OnMethodEnter(suppress = Throwable.class)
    private static void setTransactionName(@SimpleMethodSignatureOffsetMappingFactory.SimpleMethodSignature String signature, @Advice.Origin Class<?> clazz, @Advice.Local("transaction") Transaction transaction) {
        if (tracer != null) {
            AbstractSpan<?> active = tracer.getActive();
            if (active == null) {
                transaction = tracer.startRootTransaction(clazz.getClassLoader());
                if (transaction != null) {
                    transaction.withName(signature)
                        .withType("scheduled")
                        .activate();
                }

            } else {
                logger.debug("Not creating transaction for method {} because there is already a transaction running ({})", signature, active);
            }
        }
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
    public static void onMethodExit(@Nullable @Advice.Local("transaction") Transaction transaction,
                                    @Advice.Thrown Throwable t) {
        if (transaction != null) {
            transaction.captureException(t)
                .deactivate()
                .end();
        }
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return isInAnyPackage(applicationPackages, ElementMatchers.<NamedElement>none())
            .and(hasSuperClass(named("java.util.TimerTask")));
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("run");
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Arrays.asList("timer-task");
    }
}
