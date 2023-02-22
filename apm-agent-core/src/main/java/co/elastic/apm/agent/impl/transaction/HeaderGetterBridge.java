package co.elastic.apm.agent.impl.transaction;

import javax.annotation.Nullable;

public class HeaderGetterBridge<T, C> implements HeaderGetter<T, C> {

    private final co.elastic.apm.plugin.spi.HeaderGetter<T, C> headerGetter;

    public HeaderGetterBridge(co.elastic.apm.plugin.spi.HeaderGetter<T, C> headerGetter) {
        this.headerGetter = headerGetter;
    }

    @Nullable
    @Override
    public T getFirstHeader(String headerName, C carrier) {
        return headerGetter.getFirstHeader(headerName, carrier);
    }

    @Override
    public <S> void forEach(String headerName, C carrier, S state, HeaderConsumer<T, S> consumer) {
        headerGetter.forEach(headerName, carrier, state, consumer);
    }

    @Override
    public <S> void forEach(String headerName, C carrier, S state, co.elastic.apm.plugin.spi.HeaderGetter.HeaderConsumer<T, S> consumer) {
        headerGetter.forEach(headerName, carrier, state, new HeaderConsumerBridge<>(consumer));
    }
}
