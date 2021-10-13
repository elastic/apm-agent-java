package co.elastic.apm.agent.impl.context;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MessageTest {

    @Test
    void testResetState() {
        Message message = createMessage(100L, "test-body", "test-q", "test-*", Map.of("test-header", "test-value"));

        message.resetState();

        assertThat(message.getAge()).isEqualTo(-1L);
        assertThat(message.getQueueName()).isNull();
        assertThat(message.getRoutingKey()).isNull();
        assertThat(message.getHeaders()).isEmpty();
        assertThat(message.getBodyForRead()).isNull();
    }

    @Test
    void testCopyFrom() {
        Message firstMessage = createMessage(100L, "test-body", "test-q", "test-*", Map.of("test-header", "test-value"));
        Message secondMessage = createMessage(999L, "updated-body", "updated-test-q", "updated-test-*", Map.of("updated-test-header", "updated-test-value"));

        firstMessage.copyFrom(secondMessage);

        assertThat(firstMessage.getAge()).isEqualTo(999L);
        assertThat(firstMessage.getQueueName()).isEqualTo("updated-test-q");
        assertThat(firstMessage.getRoutingKey()).isEqualTo("updated-test-*");
        Headers firstMessageHeaders = firstMessage.getHeaders();
        assertThat(firstMessageHeaders.size()).isEqualTo(1);
        Headers.Header firstMessageHeader = firstMessageHeaders.iterator().next();
        assertThat(firstMessageHeader.getKey()).isEqualTo("updated-test-header");
        assertThat(firstMessageHeader.getValue()).isEqualTo("updated-test-value");
        StringBuilder firstMessageBody = firstMessage.getBodyForRead();
        assertThat(firstMessageBody).isNotNull();
        assertThat(firstMessageBody.toString()).isEqualTo("updated-body");
    }

    private Message createMessage(long age, String body, String queue, String routingKey, Map<String, String> headers) {
        Message message = new Message()
            .withAge(age)
            .withBody(body)
            .withQueue(queue)
            .withRoutingKey(routingKey);
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            message.addHeader(entry.getKey(), entry.getValue());
        }
        return message;
    }
}
