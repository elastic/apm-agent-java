package co.elastic.apm.agent.rabbitmq;


import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.rabbitmq.header.SpringRabbitMQTextHeaderGetter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.util.Iterator;

public class MessageBatchIteratorWrapper implements Iterator<Message> {

    public static final Logger logger = LoggerFactory.getLogger(MessageBatchIteratorWrapper.class);

    private final Iterator<Message> delegate;
    private final ElasticApmTracer tracer;

    public MessageBatchIteratorWrapper(Iterator<Message> delegate, ElasticApmTracer tracer) {
        this.delegate = delegate;
        this.tracer = tracer;
    }

    @Override
    public boolean hasNext() {
        endCurrentTransaction();
        return delegate.hasNext();
    }

    public void endCurrentTransaction() {
        try {
            Transaction transaction = tracer.currentTransaction();
            if (transaction != null && "messaging".equals(transaction.getType())) {
                transaction.deactivate().end();
            }
        } catch (Exception e) {
            logger.error("Error in Spring AMQP iterator wrapper", e);
        }
    }

    @Override
    public Message next() {
        endCurrentTransaction();

        Message message = delegate.next();

        try {
            if (message == null) {
                return message;
            }
            MessageProperties messageProperties = message.getMessageProperties();
            if (messageProperties == null) {
                return message;
            }
            String exchange = messageProperties.getReceivedExchange();
            if (null == exchange || AbstractBaseInstrumentation.isIgnored(exchange)) {
                return null;
            }

            Transaction transaction = tracer.currentTransaction();
            if (transaction != null) {
                return null;
            }
            transaction = tracer.startChildTransaction(messageProperties, SpringRabbitMQTextHeaderGetter.INSTANCE, message.getClass().getClassLoader());
            if (transaction == null) {
                return null;
            }

            transaction.withType(AmqpConstants.TRANSACTION_MESSAGING_TYPE)
                .withName(AmqpConstants.SPRING_AMQP_TRANSACTION_PREFIX)
                .appendToName(" RECEIVE from ")
                .appendToName(AbstractBaseInstrumentation.normalizeExchangeName(exchange));

            transaction.setFrameworkName(AmqpConstants.FRAMEWORK_NAME);

            long timestamp = AbstractBaseInstrumentation.getTimestamp(messageProperties.getTimestamp());
            co.elastic.apm.agent.impl.context.Message internalMessage = AbstractBaseInstrumentation.captureMessage(exchange, timestamp, transaction);
            // only capture incoming messages headers for now (consistent with other messaging plugins)
            AbstractBaseInstrumentation.captureHeaders(messageProperties.getHeaders(), internalMessage);
            transaction.activate();
        } catch (Throwable throwable) {
            logger.error("Error in transaction creation based on Spring AMQP batch message", throwable);
        }
        return message;
    }

    @Override
    public void remove() {
        delegate.remove();
    }
}
