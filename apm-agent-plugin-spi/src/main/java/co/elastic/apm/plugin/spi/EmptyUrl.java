package co.elastic.apm.plugin.spi;

import javax.annotation.Nullable;
import java.net.URI;

public class EmptyUrl implements Url {

    public static final Url INSTANCE = new EmptyUrl();

    private EmptyUrl() {
    }

    @Override
    public void fillFrom(URI uri) {
    }

    @Override
    public void fillFrom(@Nullable String scheme, @Nullable String serverName, int serverPort, @Nullable String requestURI, @Nullable String queryString) {
    }

    @Override
    public Url withProtocol(@Nullable String protocol) {
        return this;
    }

    @Override
    public Url withHostname(@Nullable String hostname) {
        return this;
    }

    @Override
    public Url withPort(int port) {
        return this;
    }

    @Override
    public Url withPathname(@Nullable String pathname) {
        return this;
    }

    @Override
    public Url withSearch(@Nullable String search) {
        return this;
    }
}
