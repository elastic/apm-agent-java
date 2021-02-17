package co.elastic.apm.agent.rabbitmq.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableRabbit
public class FanoutConfiguration extends BaseConfiguration {

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(new Jackson2JsonMessageConverter());
        return factory;
    }

    @Bean
    public FanoutExchange foobarExchange() {
        return new FanoutExchange("foobar");
    }

    @Bean
    public Binding binding1() {
        return BindingBuilder.bind(queueFoo()).to(foobarExchange());
    }

    @Bean
    public Binding binding2() {
        return BindingBuilder.bind(queueBar()).to(foobarExchange());
    }

    @Bean
    Queue queueBar() {
        return new Queue("bar", false);
    }

    @Bean
    Queue queueFoo() {
        return new Queue("foo", false);
    }

    @RabbitListener(queues = "foo")
    public void processFooMessage(String message) {
        System.out.println("foo process");
        testSpan();
    }

    @RabbitListener(queues = "bar")
    public void processBarMessage(String message) {
        System.out.println("bar process");
        testSpan();
    }

}
