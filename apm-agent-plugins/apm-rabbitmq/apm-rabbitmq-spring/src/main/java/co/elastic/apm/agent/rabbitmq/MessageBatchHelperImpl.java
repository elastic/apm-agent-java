package co.elastic.apm.agent.rabbitmq;

import co.elastic.apm.agent.impl.ElasticApmTracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;

import java.util.List;

public class MessageBatchHelperImpl implements MessageBatchHelper<Message> {

    public static final Logger logger = LoggerFactory.getLogger(MessageBatchHelperImpl.class);

    private final ElasticApmTracer tracer;

    public MessageBatchHelperImpl(ElasticApmTracer tracer) {
        this.tracer = tracer;
    }

    @Override
    public List<Message> wrapMessageBatchList(List<Message> messageBatchList) {
        try {
            return new MessageBatchListWrapper(messageBatchList, tracer);
        } catch (Throwable throwable) {
            logger.debug("Failed to wrap Spring AMQP MessageListener list", throwable);
            return messageBatchList;
        }
    }
}
