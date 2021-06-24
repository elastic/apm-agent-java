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

    /**
     * Information about the service related to this destination.
     */
    private final Service service = new Service();

    public Service getService() {
        return service;
    }

    public boolean hasContent() {
        return address.length() > 0 || port > 0 || service.hasContent();
    }

    @Override
    public void resetState() {
        address.setLength(0);
        addressSetByUser = false;
        port = 0;
        portSetByUser = false;
        service.resetState();
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

    /**
     * Context information required for service maps.
     */
    public static class Service implements Recyclable {
        /**
         * Used for detecting unique destinations from each service.
         * For HTTP, this is the address, with the port (even when it's the default port), without any scheme.
         * For other types of connections, it's just the {@code span.subtype} (kafka, elasticsearch etc.).
         * For messaging, we additionally add the queue name (eg jms/myQueue).
         */
        private final StringBuilder resource = new StringBuilder();

        private boolean resourceSetByUser;

        /**
         * Used for detecting “sameness” of services and then the display name of a service in the Service Map.
         * In other words, the {@link Service#resource} is used to query data for ALL destinations. However,
         * some `resources` may be nodes of the same cluster, in which case we also want to be aware.
         * Eventually, we may decide to actively fetch a cluster name or similar and we could use that to detect "sameness".
         * For now, for HTTP we use scheme, host, and non-default port. For anything else, we use {@code span.subtype}
         * (for example- postgresql, elasticsearch).
         *
         * @deprecated will be removed
         */
        private final StringBuilder name = new StringBuilder();

        /**
         * For displaying icons or similar. Currently, this should be equal to the {@code span.type}.
         *
         * @deprecated will be removed
         */
        @Nullable
        private String type;

        public Service withUserResource(@Nullable String resource) {
            if (resource == null || resource.isEmpty()) {
                this.resource.setLength(0);
            } else {
                setResourceValue(resource);
            }
            resourceSetByUser = true;
            return this;
        }

        public Service withResource(String resource) {
            if (!resourceSetByUser) {
                setResourceValue(resource);
            }
            return this;
        }

        private void setResourceValue(String newValue) {
            resource.setLength(0);
            resource.append(newValue);
        }

        public StringBuilder getResource() {
            return resource;
        }

        @Deprecated
        public Service withName(String name) {
            return this;
        }

        @Deprecated
        public StringBuilder getName() {
            return name;
        }

        @Deprecated
        public Service withType(@Nullable String type) {
            return this;
        }

        @Nullable
        @Deprecated
        public String getType() {
            return type;
        }

        public boolean hasContent() {
            return resource.length() > 0;
        }

        @Override
        public void resetState() {
            resource.setLength(0);
            resourceSetByUser = false;
            name.setLength(0);
            type = null;
        }
    }
}
