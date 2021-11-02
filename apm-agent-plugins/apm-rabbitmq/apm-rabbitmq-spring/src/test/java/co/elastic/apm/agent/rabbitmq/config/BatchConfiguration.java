package co.elastic.apm.agent.rabbitmq.config;


import co.elastic.apm.agent.rabbitmq.TestConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.batch.SimpleBatchingStrategy;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.BatchingRabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.List;

import static co.elastic.apm.agent.rabbitmq.TestConstants.QUEUE_NAME;

@EnableRabbit
@Configuration
public class BatchConfiguration extends BaseConfiguration {

    public static final Logger logger = LoggerFactory.getLogger(BatchConfiguration.class);

    @Bean
    public SimpleRabbitListenerContainerFactory simpleRabbitListenerContainerFactory(ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(new Jackson2JsonMessageConverter());
        factory.setBatchListener(true);
        factory.setConsumerBatchEnabled(true);
        factory.setDeBatchingEnabled(true);
        factory.setBatchSize(2);
        factory.setConcurrentConsumers(2);
        factory.setReceiveTimeout(1000L);
        return factory;
    }

    @Bean
    public BatchingRabbitTemplate template(MessageConverter messageConverter, TaskScheduler taskScheduler) {
        BatchingRabbitTemplate batchTemplate = new BatchingRabbitTemplate(connectionFactory(),
            new SimpleBatchingStrategy(2, 10_000, 10_000L), taskScheduler);
        batchTemplate.setMessageConverter(messageConverter);
        return batchTemplate;
    }

    @Bean
    public TaskScheduler scheduler() {
        return new ThreadPoolTaskScheduler();
    }

    @Bean
    public Jackson2JsonMessageConverter converter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public Queue queue() {
        return new Queue(QUEUE_NAME, false);
    }

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
}
