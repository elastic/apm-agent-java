/*
 * Licensed to Elasticsearch B.V. under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch B.V. licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package co.elastic.apm.agent.rabbitmq.config;

import co.elastic.apm.agent.rabbitmq.TestConstants;
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
        return new FanoutExchange(TestConstants.FANOUT_EXCHANGE);
    }

    @Bean
    public Binding bindingFoo() {
        return BindingBuilder.bind(queueFoo()).to(foobarExchange());
    }

    @Bean
    public Binding bindingBar() {
        return BindingBuilder.bind(queueBar()).to(foobarExchange());
    }

    @Bean
    Queue queueBar() {
        return new Queue(TestConstants.QUEUE_BAR, false);
    }

    @Bean
    Queue queueFoo() {
        return new Queue(TestConstants.QUEUE_FOO, false);
    }

    @RabbitListener(queues = TestConstants.QUEUE_FOO)
    public void processFooMessage(String message) {
        System.out.println("foo process");
        testSpan();
    }

    @RabbitListener(queues = TestConstants.QUEUE_BAR)
    public void processBarMessage(String message) {
        System.out.println("bar process");
        testSpan();
    }

}
