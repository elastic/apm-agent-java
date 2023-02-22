package co.elastic.apm.agent.impl.transaction;

import co.elastic.apm.plugin.spi.HeaderGetter;

import javax.annotation.Nullable;

public class BinaryHeaderGetterBridge<C> implements BinaryHeaderGetter<C> {

    private final co.elastic.apm.plugin.spi.BinaryHeaderGetter<C> binaryHeaderGetter;

    public BinaryHeaderGetterBridge(co.elastic.apm.plugin.spi.BinaryHeaderGetter<C> binaryHeaderGetter) {
        this.binaryHeaderGetter = binaryHeaderGetter;
    }

    @Nullable
    @Override
    public byte[] getFirstHeader(String headerName, C carrier) {
        return binaryHeaderGetter.getFirstHeader(headerName, carrier);
    }

    @Override
    public <S> void forEach(String headerName, C carrier, S state, co.elastic.apm.agent.impl.transaction.HeaderGetter.HeaderConsumer<byte[], S> consumer) {
        binaryHeaderGetter.forEach(headerName, carrier, state, consumer);
    }

    @Override
    public <S> void forEach(String headerName, C carrier, S state, HeaderGetter.HeaderConsumer<byte[], S> consumer) {
        binaryHeaderGetter.forEach(headerName, carrier, state, consumer);
    }
}
