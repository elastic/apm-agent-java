package co.elastic.apm.agent.rabbitmq;

import co.elastic.apm.agent.impl.transaction.Transaction;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public interface SpringAmqpTransactionHelper {

    @Nullable
    Transaction createTransaction(@Nonnull Message message, @Nonnull MessageProperties messageProperties,  @Nonnull String transactionNamePrefix);
}
