package co.elastic.apm.agent.rabbitmq.spring.components;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageListener;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;

import static co.elastic.apm.agent.rabbitmq.spring.TestConstants.QUEUE_NAME;
import static co.elastic.apm.agent.rabbitmq.spring.TestConstants.TOPIC_EXCHANGE_NAME;

@Configuration
public class CommonRabbitmqSpringConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(SimpleMessageListenerContainerConfiguration.class);

    @Autowired
    private Environment environment;

    @Bean
    ConnectionFactory connectionFactory() throws java.io.IOException {
        CachingConnectionFactory connectionFactory = new CachingConnectionFactory(environment.getProperty("spring.rabbitmq.host"));
        connectionFactory.setPort(Integer.valueOf(environment.getProperty("spring.rabbitmq.port")));
        connectionFactory.setUsername(environment.getProperty("spring.rabbitmq.username"));
        connectionFactory.setPassword(environment.getProperty("spring.rabbitmq.password"));
        return connectionFactory;
    }

    @Bean
    public AmqpAdmin amqpAdmin(ConnectionFactory connectionFactory) throws IOException {
        return new RabbitAdmin(connectionFactory);
    }

    @Bean
    Queue queue() {
        return new Queue(QUEUE_NAME, false);
    }

    @Bean
    TopicExchange exchange() {
        return new TopicExchange(TOPIC_EXCHANGE_NAME);
    }

    @Bean
    RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Bean
    Binding binding(Queue queue, TopicExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with("foo.bar.#");
    }

    @Bean
    public MessageListener messageListener() {
        return new MessageListener() {
            public void onMessage(Message message) {
                logger.info("Received message with MessageListener");
                restTemplate().getForObject("http://localhost/", String.class);
            }
        };
    }

    @Bean
    public RabbitTemplate rabbitTemplate(final ConnectionFactory connectionFactory) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(new Jackson2JsonMessageConverter());
        return rabbitTemplate;
    }
}
