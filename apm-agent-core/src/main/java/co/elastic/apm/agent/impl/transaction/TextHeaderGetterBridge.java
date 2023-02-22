package co.elastic.apm.agent.impl.transaction;

import javax.annotation.Nullable;

public class TextHeaderGetterBridge<C> implements TextHeaderGetter<C> {

    private final co.elastic.apm.plugin.spi.TextHeaderGetter<C> textHeadersGetter;

    public TextHeaderGetterBridge(co.elastic.apm.plugin.spi.TextHeaderGetter<C> textHeadersGetter) {
        this.textHeadersGetter = textHeadersGetter;
    }

    @Nullable
    @Override
    public String getFirstHeader(String headerName, C carrier) {
        return textHeadersGetter.getFirstHeader(headerName, carrier);
    }

    @Override
    public <S> void forEach(String headerName, C carrier, S state, HeaderGetter.HeaderConsumer<String, S> consumer) {
        textHeadersGetter.forEach(headerName, carrier, state, consumer);
    }

    @Override
    public <S> void forEach(String headerName, C carrier, S state, co.elastic.apm.plugin.spi.HeaderGetter.HeaderConsumer<String, S> consumer) {
        textHeadersGetter.forEach(headerName, carrier, state, consumer);
    }
}
