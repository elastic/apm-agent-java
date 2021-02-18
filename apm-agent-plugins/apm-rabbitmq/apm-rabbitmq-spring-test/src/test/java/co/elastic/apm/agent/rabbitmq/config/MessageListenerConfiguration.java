package co.elastic.apm.agent.rabbitmq.config;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MessageListenerConfiguration extends DefaultBindingSpringConfiguration {

    @Bean
    public MessageListener messageListener() {
        return new MessageListener() {
            @Override
            public void onMessage(Message message) {
                testSpan();
            }
        };
    }
}
