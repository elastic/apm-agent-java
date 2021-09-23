package co.elastic.apm.agent.r2dbc;

import co.elastic.apm.agent.r2dbc.helper.R2dbcHelper;
import co.elastic.apm.agent.r2dbc.helper.R2dbcReactorHelper;
import co.elastic.apm.agent.sdk.advice.AssignTo;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import net.bytebuddy.matcher.ElementMatcher;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesNoArguments;

public class R2dbcConnectionFactoryInstrumentation extends AbstractR2dbcInstrumentation {
    private static final Logger logger = LoggerFactory.getLogger(R2dbcConnectionFactoryInstrumentation.class);

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return not(isInterface())
            .and(hasSuperType(named("io.r2dbc.spi.ConnectionFactory")));
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("create")
            .and(takesNoArguments())
            .and(isPublic());
    }

    public static class AdviceClass {

        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static void onBeforeExecute(@Advice.This Object connectionFactoryObject) {
            if (connectionFactoryObject == null) {
                return;
            }
            R2dbcHelper helper = R2dbcHelper.get();
            logger.info("Trying to handle creating connection {} on thread = {}", connectionFactoryObject, Thread.currentThread().getName());
        }

        @Nullable
        @AssignTo.Return(typing = Assigner.Typing.DYNAMIC)
        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
        public static Object onAfterExecute(@Advice.This ConnectionFactory connectionFactory,
                                            @Advice.Thrown @Nullable Throwable t,
                                            @Advice.Return @Nullable Publisher<? extends Connection> returnValue) {
            if (t != null || returnValue == null) {
                return returnValue;
            }
            R2dbcHelper helper = R2dbcHelper.get();
            ConnectionFactoryOptions connectionFactoryOptions = helper.getConnectionFactoryOptions(connectionFactory);
            return R2dbcReactorHelper.wrapConnectionPublisher(returnValue, connectionFactoryOptions);
        }
    }
}
