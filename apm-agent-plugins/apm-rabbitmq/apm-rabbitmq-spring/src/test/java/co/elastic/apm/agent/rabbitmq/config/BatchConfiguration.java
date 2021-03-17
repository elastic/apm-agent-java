package co.elastic.apm.agent.rabbitmq.config;

import co.elastic.apm.agent.rabbitmq.TestConstants;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.concurrent.CountDownLatch;

@EnableRabbit
@Configuration
public class BatchConfiguration extends BaseConfiguration {

    @Bean
    public SimpleRabbitListenerContainerFactory simpleRabbitListenerContainerFactory(SimpleRabbitListenerContainerFactoryConfigurer configurer, ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(new Jackson2JsonMessageConverter());
        factory.setBatchListener(true);
        factory.setConsumerBatchEnabled(true);
        factory.setDeBatchingEnabled(true);
        factory.setBatchSize(2);
        factory.setReceiveTimeout(1000L);
        return factory;
    }

    @Bean
    public Listener listener() {
        return new Listener();
    }

    public static class Listener {

        private final CountDownLatch latch = new CountDownLatch(2);

        @RabbitListener(
            queues = TestConstants.QUEUE_NAME,
            containerFactory = "simpleRabbitListenerContainerFactory"
        )
        public void receiveWorkingBatch(List<String> batchMessages) {
            System.out.println("Received batch of size " + batchMessages.size() + " from 'workingBatchQueue'");
            batchMessages.forEach(message -> {
                System.out.println("Message in 'spring-boot' batch: " + message);
                latch.countDown();
            });
        }
    }

}
