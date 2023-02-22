package co.elastic.apm.agent.impl.transaction;

import javax.annotation.Nullable;

public class HeaderConsumerBridge<T, C> implements HeaderGetter.HeaderConsumer<T, C> {

    private final co.elastic.apm.plugin.spi.HeaderGetter.HeaderConsumer<T, C> headerConsumer;

    public HeaderConsumerBridge(co.elastic.apm.plugin.spi.HeaderGetter.HeaderConsumer<T, C> headerConsumer) {
        this.headerConsumer = headerConsumer;
    }

    @Override
    public void accept(@Nullable T headerValue, C state) {
        headerConsumer.accept(headerValue, state);
    }
}
