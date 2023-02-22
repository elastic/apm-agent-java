package co.elastic.apm.plugin.spi;

import javax.annotation.Nullable;
import java.net.URI;

public interface Url {

    void fillFrom(URI uri);

    void fillFrom(@Nullable String scheme, @Nullable String serverName, int serverPort, @Nullable String requestURI, @Nullable String queryString);

    Url withProtocol(@Nullable String protocol);

    Url withHostname(@Nullable String hostname);

    Url withPort(int port);

    Url withPathname(@Nullable String pathname);

    Url withSearch(@Nullable String search);
}
