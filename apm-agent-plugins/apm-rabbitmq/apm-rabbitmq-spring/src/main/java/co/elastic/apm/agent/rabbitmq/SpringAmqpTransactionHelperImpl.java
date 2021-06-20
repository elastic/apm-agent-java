package co.elastic.apm.agent.rabbitmq;

import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.rabbitmq.header.SpringRabbitMQTextHeaderGetter;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class SpringAmqpTransactionHelperImpl {

    private final ElasticApmTracer tracer;

    public SpringAmqpTransactionHelperImpl(ElasticApmTracer tracer) {
        this.tracer = tracer;
    }

    @Nullable
    public Transaction createTransaction(@Nonnull Message message, @Nullable MessageProperties messageProperties, @Nonnull String transactionNamePrefix) {
        String exchange = null;
        if (messageProperties != null) {
            exchange = messageProperties.getReceivedExchange();
        }
        if (exchange != null && AbstractBaseInstrumentation.isIgnored(exchange)) {
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

        transaction.withType("messaging")
            .withName(transactionNamePrefix)
            .appendToName(" RECEIVE from ")
            .appendToName(AbstractBaseInstrumentation.normalizeExchangeName(exchange));

        transaction.setFrameworkName("Spring AMQP");

        if (messageProperties != null) {
            long timestamp = AbstractBaseInstrumentation.getTimestamp(messageProperties.getTimestamp());
            transaction.getContext().getMessage().withAge(timestamp);
        }
        if (exchange != null) {
            transaction.getContext().getMessage().withQueue(exchange);
        }
        // only capture incoming messages headers for now (consistent with other messaging plugins)
        AbstractBaseInstrumentation.captureHeaders(messageProperties != null ? messageProperties.getHeaders() : null, transaction.getContext().getMessage());
        return transaction.activate();
    }
}
