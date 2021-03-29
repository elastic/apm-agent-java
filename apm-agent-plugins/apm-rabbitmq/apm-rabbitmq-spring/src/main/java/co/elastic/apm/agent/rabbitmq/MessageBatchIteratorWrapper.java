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
    private final SpringAmqpTransactionHelper transactionHelper;

    public MessageBatchIteratorWrapper(Iterator<Message> delegate, ElasticApmTracer tracer, SpringAmqpTransactionHelper transactionHelper) {
        this.delegate = delegate;
        this.tracer = tracer;
        this.transactionHelper = transactionHelper;
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
            transactionHelper.createTransaction(message, messageProperties, AmqpConstants.SPRING_AMQP_TRANSACTION_PREFIX);
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
