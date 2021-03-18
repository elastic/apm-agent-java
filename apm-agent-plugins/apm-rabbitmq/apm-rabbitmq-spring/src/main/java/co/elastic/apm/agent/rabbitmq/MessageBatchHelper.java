package co.elastic.apm.agent.rabbitmq;

import java.util.List;

public interface MessageBatchHelper<MSG> {

    List<MSG> wrapMessageBatchList(List<MSG> messageBatchList);
}
