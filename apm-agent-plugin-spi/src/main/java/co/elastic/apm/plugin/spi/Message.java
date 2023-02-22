package co.elastic.apm.plugin.spi;

import javax.annotation.Nullable;

public interface Message {

    Message withAge(long age);

    Message withBody(@Nullable String body);

    Message withRoutingKey(String routingKey);

    Message withQueue(@Nullable String queueName);

    Message addHeader(@Nullable String key, @Nullable String value);

    Message addHeader(@Nullable String key, @Nullable byte[] value);

    Message appendToBody(CharSequence bodyContent);
}
