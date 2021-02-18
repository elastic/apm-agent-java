package co.elastic.apm.agent.rabbitmq;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageListener;

import javax.annotation.Nullable;

public class MessageListenerHelper {

    @Nullable
    public MessageListener wrapLambda(@Nullable MessageListener listener) {
        if (listener != null && listener.getClass().getName().contains("/")) {
            return new MessageListenerHelper.MessageListenerWrapper(listener);
        }
        return listener;
    }

    public static class MessageListenerWrapper implements MessageListener {

        private final MessageListener delegate;

        public MessageListenerWrapper(MessageListener delegate) {
            this.delegate = delegate;
        }

        @Override
        public void onMessage(Message message) {
            delegate.onMessage(message);
        }
    }
}
