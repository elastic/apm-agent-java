package co.elastic.apm.agent.rabbitmq;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.AsyncRabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.util.concurrent.ListenableFuture;

import java.util.concurrent.ExecutionException;

import static co.elastic.apm.agent.rabbitmq.TestConstants.TOPIC_EXCHANGE_NAME;

public abstract class AbstractAsyncRabbitMqTest extends RabbitMqTestBase {

    private static final Logger logger = LoggerFactory.getLogger(AbstractAsyncRabbitMqTest.class);

    private static final String MESSAGE = "foo-bar";

    @Autowired
    @Qualifier("asyncRabbitTemplateWithDefaultListener")
    private AsyncRabbitTemplate asyncRabbitTemplate;

    @Test
    public void verifyThatTransactionWithSpanCreated() {
        logger.info("Trying to send to async rabbit template");
        ListenableFuture<String> future = asyncRabbitTemplate.convertSendAndReceive(TOPIC_EXCHANGE_NAME, TestConstants.ROUTING_KEY, MESSAGE);
        try {
            String response = future.get();
            logger.info("Got response = {}", response);
        } catch (ExecutionException | InterruptedException e) {
            logger.error("Got exception", e);
        }

        reporter.awaitTransactionCount(2);
    }
}
