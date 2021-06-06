package co.elastic.apm.agent.rabbitmq;


import co.elastic.apm.agent.sdk.advice.AssignTo;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.springframework.amqp.core.Message;

import javax.annotation.Nullable;
import java.util.List;

import static net.bytebuddy.matcher.ElementMatchers.*;

public class SpringAmqpBatchMessageListenerInstrumentation extends SpringBaseInstrumentation {
    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("onMessageBatch")
            .and(takesArgument(0, List.class)).and(isPublic());
    }

    @Override
    public String getAdviceClassName() {
        return "co.elastic.apm.agent.rabbitmq.SpringAmqpBatchMessageListenerInstrumentation$MessageListenerContainerWrappingAdvice";
    }

    public static class MessageListenerContainerWrappingAdvice extends BaseAdvice {

        @Nullable
        @AssignTo.Argument(0)
        @Advice.OnMethodEnter(inline = false)
        public static List<Message> beforeOnBatch(@Advice.Argument(0) @Nullable final List<Message> messageBatch) {
            if (!tracer.isRunning() || tracer.currentTransaction() != null || messageBatch == null) {
                return messageBatch;
            }
            return messageBatchHelper.wrapMessageBatchList(messageBatch);
        }
    }
}
