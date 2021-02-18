package co.elastic.apm.agent.rabbitmq;

import co.elastic.apm.agent.sdk.advice.AssignTo;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.springframework.amqp.core.MessageListener;

import javax.annotation.Nullable;

import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.nameContains;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

public class SetMessageListenerInstrumentation extends SpringBaseInstrumentation {
    @Override
    public ElementMatcher<? super NamedElement> getTypeMatcherPreFilter() {
        return nameContains("Container");
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return not(isInterface()).and(hasSuperType(named("org.springframework.amqp.rabbit.listener.AbstractMessageListenerContainer")));
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("setMessageListener").and(takesArgument(0, named("org.springframework.amqp.core.MessageListener")));
    }

    @Override
    public Class<?> getAdviceClass() {
        return MessageListenerContainerWrappingAdvice.class;
    }

    public static class MessageListenerContainerWrappingAdvice extends SpringBaseAdvice {

        @Nullable
        @AssignTo.Argument(0)
        @Advice.OnMethodEnter(inline = false)
        public static MessageListener beforeSetListener(@Advice.Argument(0) @Nullable MessageListener original) {
            return helper.wrapLambda(original);
        }
    }
}
