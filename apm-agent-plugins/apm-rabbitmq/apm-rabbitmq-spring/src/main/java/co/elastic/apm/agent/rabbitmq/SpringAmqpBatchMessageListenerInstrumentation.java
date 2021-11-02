package co.elastic.apm.agent.rabbitmq;


import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.GlobalTracer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned.ToArguments.ToArgument;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.springframework.amqp.core.Message;

import javax.annotation.Nullable;
import java.util.List;

import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

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
        protected static final MessageBatchHelper messageBatchHelper;

        static {
            ElasticApmTracer elasticApmTracer = GlobalTracer.requireTracerImpl();
            messageBatchHelper = new MessageBatchHelper(elasticApmTracer, transactionHelper);
        }

        @Nullable
        @Advice.AssignReturned.ToArguments(@ToArgument(0))
        @Advice.OnMethodEnter(inline = false)
        public static List<Message> beforeOnBatch(@Advice.Argument(0) @Nullable final List<Message> messageBatch) {
            if (!tracer.isRunning() || tracer.currentTransaction() != null || messageBatch == null) {
                return messageBatch;
            }
            return messageBatchHelper.wrapMessageBatchList(messageBatch);
        }
    }
}
