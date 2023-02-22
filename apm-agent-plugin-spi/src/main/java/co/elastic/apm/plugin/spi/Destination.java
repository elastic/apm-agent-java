package co.elastic.apm.plugin.spi;

import javax.annotation.Nullable;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

public interface Destination {

    Destination withAddress(@Nullable CharSequence address);

    Destination withInetAddress(InetAddress address);

    Destination withPort(int port);

    Destination withAddressPort(@Nullable String addressPort);

    Destination withInetSocketAddress(InetSocketAddress remoteAddress);

    Destination withSocketAddress(SocketAddress socketAddress);

    Cloud getCloud();
}
