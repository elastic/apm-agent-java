package co.elastic.apm.plugin.spi;

import javax.annotation.Nullable;

public interface Socket {

    Socket withRemoteAddress(@Nullable String remoteAddress);
}
