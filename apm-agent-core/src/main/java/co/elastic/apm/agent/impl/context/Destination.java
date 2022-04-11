/*
 * Licensed to Elasticsearch B.V. under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch B.V. licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package co.elastic.apm.agent.impl.context;

import co.elastic.apm.agent.objectpool.Recyclable;

import javax.annotation.Nullable;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

/**
 * Context information about a destination of outgoing calls.
 */
public class Destination implements Recyclable {

    /**
     * An IP (v4 or v6) or a host/domain name.
     */
    private final StringBuilder address = new StringBuilder();

    private boolean addressSetByUser;

    /**
     * The destination's port used within this context.
     */
    private int port;

    private boolean portSetByUser;

    public Destination withAddress(@Nullable CharSequence address) {
        if (address != null && !addressSetByUser) {
            withAddress(address, 0, address.length());
        }
        return this;
    }

    public Destination withUserAddress(@Nullable CharSequence address) {
        if (address == null || address.length() == 0) {
            this.address.setLength(0);
        } else {
            withAddress(address, 0, address.length());
        }
        addressSetByUser = true;
        return this;
    }

    public StringBuilder getAddress() {
        return address;
    }

    public Destination withPort(int port) {
        if (!portSetByUser) {
            this.port = port;
        }
        return this;
    }

    public Destination withUserPort(int port) {
        withPort(port);
        portSetByUser = true;
        return this;
    }

    public int getPort() {
        return port;
    }

    /**
     * @param addressPort host address and port in the following format 'host:3128'
     * @return destination with updated address and port
     */
    public Destination withAddressPort(@Nullable String addressPort) {
        if (addressPort != null) {
            int separator = addressPort.lastIndexOf(':');
            if (separator > 0) {

                int port = parsePort(addressPort, separator + 1, addressPort.length());

                if (port > 0) {
                    withPort(port);
                    if (!addressSetByUser) {
                        withAddress(addressPort, 0, separator);
                    }
                }
            }
        }
        return this;
    }

    // a bit of manual parsing required because Integer.parseInt(...) from CharSequence is only available on Java9
    private static int parsePort(CharSequence input, int start, int end) {
        int port = 0;
        for (int i = start; i < end; i++) {
            char c = input.charAt(i);
            if (c < '0' || c > '9') {
                return -1;
            }
            port += (c - '0');
            if (i < (end - 1)) {
                port *= 10;
            }
        }
        return port;
    }

    private void withAddress(CharSequence address, int start, int end) {
        if (address.length() > 0 && start < end) {
            int startIndex = start;
            int endIndex = end - 1;
            if (address.charAt(startIndex) == '[') {
                startIndex++;
            }
            if (address.charAt(endIndex) == ']') {
                endIndex--;
            }

            if (startIndex < endIndex) {
                if (this.address.length() > 0) {
                    // buffer reset required if it has already been used
                    this.address.delete(0, this.address.length());
                }
                this.address.append(address, startIndex, endIndex + 1);
            }
        }
    }

    public boolean hasContent() {
        return address.length() > 0 || port > 0;
    }

    @Override
    public void resetState() {
        address.setLength(0);
        addressSetByUser = false;
        port = 0;
        portSetByUser = false;
    }

    public Destination withSocketAddress(SocketAddress socketAddress) {
        if (socketAddress instanceof InetSocketAddress) {
            withInetSocketAddress((InetSocketAddress) socketAddress);
        }
        return this;
    }

    public Destination withInetSocketAddress(InetSocketAddress inetSocketAddress) {
        InetAddress inetAddress = inetSocketAddress.getAddress();
        if (inetAddress != null) {
            withInetAddress(inetAddress);
        } else {
            withAddress(inetSocketAddress.getHostString());
        }
        withPort(inetSocketAddress.getPort());
        return this;
    }

    public Destination withInetAddress(InetAddress inetAddress) {
        withAddress(inetAddress.getHostAddress());
        return this;
    }

}
