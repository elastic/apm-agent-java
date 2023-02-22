package co.elastic.apm.plugin.spi;

import javax.annotation.Nullable;

public class EmptySocket implements Socket {

    public static final Socket INSTANCE = new EmptySocket();

    private EmptySocket() {
    }

    @Override
    public Socket withRemoteAddress(@Nullable String remoteAddress) {
        return this;
    }
}
