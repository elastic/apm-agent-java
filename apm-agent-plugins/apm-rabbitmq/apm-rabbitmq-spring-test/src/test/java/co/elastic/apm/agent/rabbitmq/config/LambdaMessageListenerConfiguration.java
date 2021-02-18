package co.elastic.apm.agent.rabbitmq.config;

import org.springframework.amqp.core.MessageListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LambdaMessageListenerConfiguration extends CommonRabbitmqSpringConfiguration {

    @Bean
    public MessageListener messageListener() {
        return message -> testSpan();
    }
}
