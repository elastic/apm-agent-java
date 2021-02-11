package co.elastic.apm.agent.rabbitmq.spring;

public final class TestConstants {

    private TestConstants() {}

    public static final String DOCKER_TESTCONTAINER_RABBITMQ_IMAGE = "rabbitmq:3.7-management-alpine";

    public static final String QUEUE_NAME = "spring-boot";

    public static final String TOPIC_EXCHANGE_NAME = "spring-boot-exchange";

    public static final String ROUTING_KEY = "foo.bar.baz";

}
