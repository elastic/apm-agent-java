package co.elastic.apm.agent.rabbitmq.config;

import org.springframework.amqp.core.MessageListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LambdaMessageListenerConfiguration extends DefaultBindingSpringConfiguration {

    @Bean
    public MessageListener messageListener() {
        return message -> testSpan();
    }
}
