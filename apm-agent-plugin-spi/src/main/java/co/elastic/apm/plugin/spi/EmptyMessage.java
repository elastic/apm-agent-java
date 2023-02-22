package co.elastic.apm.plugin.spi;

import javax.annotation.Nullable;

public class EmptyMessage implements Message {

    public static final Message INSTANCE = new EmptyMessage();

    private EmptyMessage() {
    }

    @Override
    public Message withAge(long age) {
        return this;
    }

    @Override
    public Message withBody(@Nullable String body) {
        return this;
    }

    @Override
    public Message withRoutingKey(String routingKey) {
        return this;
    }

    @Override
    public Message withQueue(@Nullable String queueName) {
        return this;
    }

    @Override
    public Message addHeader(@Nullable String key, @Nullable String value) {
        return this;
    }

    @Override
    public Message addHeader(@Nullable String key, @Nullable byte[] value) {
        return this;
    }

    @Override
    public Message appendToBody(CharSequence bodyContent) {
        return this;
    }
}
