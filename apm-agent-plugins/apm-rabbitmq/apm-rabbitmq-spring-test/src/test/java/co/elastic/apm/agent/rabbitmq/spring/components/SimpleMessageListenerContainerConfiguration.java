package co.elastic.apm.agent.rabbitmq.spring.components;

import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static co.elastic.apm.agent.rabbitmq.spring.TestConstants.QUEUE_NAME;

@Configuration
public class SimpleMessageListenerContainerConfiguration extends CommonRabbitmqSpringConfiguration {

    @Bean
    SimpleMessageListenerContainer container(ConnectionFactory connectionFactory) {
        SimpleMessageListenerContainer container = new SimpleMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.setQueueNames(QUEUE_NAME);
        container.setMessageListener(messageListener());
        return container;
    }
}
