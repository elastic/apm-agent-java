package co.elastic.apm.plugin.spi;

import javax.annotation.Nullable;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

public class EmptyDestination implements Destination {

    public static final Destination INSTANCE = new EmptyDestination();

    private EmptyDestination() {
    }

    @Override
    public Destination withAddress(@Nullable CharSequence address) {
        return this;
    }

    @Override
    public Destination withInetAddress(InetAddress address) {
        return this;
    }

    @Override
    public Destination withPort(int port) {
        return this;
    }

    @Override
    public Destination withAddressPort(@Nullable String addressPort) {
        return this;
    }

    @Override
    public Destination withInetSocketAddress(InetSocketAddress remoteAddress) {
        return this;
    }

    @Override
    public Destination withSocketAddress(SocketAddress socketAddress) {
        return this;
    }

    @Override
    public Cloud getCloud() {
        return EmptyCloud.INSTANCE;
    }
}
