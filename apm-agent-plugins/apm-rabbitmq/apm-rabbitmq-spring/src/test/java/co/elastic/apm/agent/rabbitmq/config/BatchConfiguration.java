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
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;
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
