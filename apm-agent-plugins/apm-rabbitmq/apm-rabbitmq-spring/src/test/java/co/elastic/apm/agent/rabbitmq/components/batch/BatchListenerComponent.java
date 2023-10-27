package co.elastic.apm.agent.rabbitmq.components.batch;

import co.elastic.apm.agent.rabbitmq.TestConstants;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;
import co.elastic.apm.api.CaptureSpan;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.List;

import static co.elastic.apm.agent.rabbitmq.TestConstants.QUEUE_NAME;

@Component
public class BatchListenerComponent {

    public static final Logger logger = LoggerFactory.getLogger(BatchListenerComponent.class);

    @RabbitListener(
        queues = TestConstants.QUEUE_NAME,
        containerFactory = "simpleRabbitListenerContainerFactory"
    )
    public void receiveWorkingBatch(List<Message> batchMessages) {
        logger.info("Received batch of size {} from '{}'", batchMessages.size(), QUEUE_NAME);
        batchMessages.forEach(message -> {
            logger.info("Message in 'spring-boot' batch: {}", message.getBody());
            testSpan();
        });
    }

    @CaptureSpan(value = "testSpan", type = "custom", subtype = "anything", action = "test")
    public void testSpan() {
    }
}
