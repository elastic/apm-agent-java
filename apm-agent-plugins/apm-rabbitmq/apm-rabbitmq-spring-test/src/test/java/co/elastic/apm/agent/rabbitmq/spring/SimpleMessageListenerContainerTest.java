package co.elastic.apm.agent.rabbitmq.spring;

import co.elastic.apm.agent.rabbitmq.spring.components.SimpleMessageListenerContainerConfiguration;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest
@ContextConfiguration(classes = {SimpleMessageListenerContainerConfiguration.class}, initializers = {AbstractRabbitMqTest.Initializer.class})
public class SimpleMessageListenerContainerTest extends AbstractRabbitMqTest {

}
