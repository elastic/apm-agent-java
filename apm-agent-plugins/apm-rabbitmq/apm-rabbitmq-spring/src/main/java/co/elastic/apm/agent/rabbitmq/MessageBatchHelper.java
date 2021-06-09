package co.elastic.apm.agent.rabbitmq;

import org.springframework.amqp.core.Message;

import java.util.List;

public interface MessageBatchHelper {

    List<Message> wrapMessageBatchList(List<Message> messageBatchList);
}
