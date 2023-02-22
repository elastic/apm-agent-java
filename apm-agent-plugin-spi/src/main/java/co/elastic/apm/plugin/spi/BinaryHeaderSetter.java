package co.elastic.apm.plugin.spi;

import javax.annotation.Nullable;

public interface BinaryHeaderSetter<C> extends HeaderSetter<byte[], C> {

    @Nullable
    byte[] getFixedLengthByteArray(String headerName, int length);
}
