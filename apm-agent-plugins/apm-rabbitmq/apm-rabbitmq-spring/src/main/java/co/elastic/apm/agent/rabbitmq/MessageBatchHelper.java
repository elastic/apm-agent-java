package co.elastic.apm.agent.rabbitmq;

import co.elastic.apm.agent.impl.ElasticApmTracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;

import java.util.List;

public class MessageBatchHelper {

    public static final Logger logger = LoggerFactory.getLogger(MessageBatchHelper.class);

    private final ElasticApmTracer tracer;
    private final SpringAmqpTransactionHelper transactionHelper;

    public MessageBatchHelper(ElasticApmTracer tracer, SpringAmqpTransactionHelper transactionHelper) {
        this.tracer = tracer;
        this.transactionHelper = transactionHelper;
    }

    public List<Message> wrapMessageBatchList(List<Message> messageBatchList) {
        try {
            return new MessageBatchListWrapper(messageBatchList, tracer, transactionHelper);
        } catch (Throwable throwable) {
            logger.debug("Failed to wrap Spring AMQP MessageListener list", throwable);
            return messageBatchList;
        }
    }
}
