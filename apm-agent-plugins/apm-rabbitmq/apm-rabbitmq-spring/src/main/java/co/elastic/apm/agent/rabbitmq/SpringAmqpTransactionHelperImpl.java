package co.elastic.apm.agent.rabbitmq;

import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.rabbitmq.header.SpringRabbitMQTextHeaderGetter;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class SpringAmqpTransactionHelperImpl implements SpringAmqpTransactionHelper {

    private final ElasticApmTracer tracer;

    public SpringAmqpTransactionHelperImpl(ElasticApmTracer tracer) {
        this.tracer = tracer;
    }

    @Nullable
    @Override
    public Transaction createTransaction(@Nonnull Message message, @Nonnull MessageProperties messageProperties, @Nonnull String transactionNamePrefix) {
        String exchange = messageProperties.getReceivedExchange();
        if (exchange == null || AbstractBaseInstrumentation.isIgnored(exchange)) {
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

        long timestamp = AbstractBaseInstrumentation.getTimestamp(messageProperties.getTimestamp());
        co.elastic.apm.agent.impl.context.Message internalMessage = AbstractBaseInstrumentation.captureMessage(exchange, timestamp, transaction);
        // only capture incoming messages headers for now (consistent with other messaging plugins)
        AbstractBaseInstrumentation.captureHeaders(messageProperties.getHeaders(), internalMessage);
        return transaction.activate();
    }
}
