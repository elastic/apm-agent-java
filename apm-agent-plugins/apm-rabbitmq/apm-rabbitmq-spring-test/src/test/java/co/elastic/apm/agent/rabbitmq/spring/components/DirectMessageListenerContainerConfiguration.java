package co.elastic.apm.agent.rabbitmq.spring.components;

import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.listener.DirectMessageListenerContainer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static co.elastic.apm.agent.rabbitmq.spring.TestConstants.QUEUE_NAME;

@Configuration
public class DirectMessageListenerContainerConfiguration extends CommonRabbitmqSpringConfiguration {

    @Bean
    DirectMessageListenerContainer container(ConnectionFactory connectionFactory) {
        DirectMessageListenerContainer directMessageListenerContainer = new DirectMessageListenerContainer();
        directMessageListenerContainer.setConnectionFactory(connectionFactory);
        directMessageListenerContainer.setQueueNames(QUEUE_NAME);
        directMessageListenerContainer.setMessageListener(messageListener());
        return directMessageListenerContainer;
    }
}
