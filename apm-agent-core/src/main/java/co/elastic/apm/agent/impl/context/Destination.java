/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2020 Elastic and contributors
 * %%
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
 * #L%
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

    /**
     * The destination's port used within this context.
     */
    private int port;

    public Destination withAddress(@Nullable CharSequence address) {
        if (address != null) {
            return withAddress(address, 0, address.length());
        }
        return this;
    }

    public StringBuilder getAddress() {
        return address;
    }

    public Destination withPort(int port) {
        this.port = port;
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
                    return withPort(port)
                        .withAddress(addressPort, 0, separator);
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

    /**
     * @param address address buffer
     * @param start   address start (inclusive)
     * @param end     address end (exclusive)
     * @return destination with updated address
     */
    private Destination withAddress(CharSequence address, int start, int end) {
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
        return this;
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
        port = 0;
        service.resetState();
    }

    public void withSocketAddress(SocketAddress socketAddress) {
        if (socketAddress instanceof InetSocketAddress) {
            InetSocketAddress inetSocketAddress = (InetSocketAddress) socketAddress;
            InetAddress inetAddress = inetSocketAddress.getAddress();
            if (inetAddress != null) {
                withInetAddress(inetAddress);
            } else {
                withAddress(inetSocketAddress.getHostString());
            }
            withPort(inetSocketAddress.getPort());
        }
    }

    public void withInetAddress(InetAddress inetAddress) {
        withAddress(inetAddress.getHostAddress());
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

        /**
         * Used for detecting “sameness” of services and then the display name of a service in the Service Map.
         * In other words, the {@link Service#resource} is used to query data for ALL destinations. However,
         * some `resources` may be nodes of the same cluster, in which case we also want to be aware.
         * Eventually, we may decide to actively fetch a cluster name or similar and we could use that to detect "sameness".
         * For now, for HTTP we use scheme, host, and non-default port. For anything else, we use {@code span.subtype}
         * (for example- postgresql, elasticsearch).
         */
        private final StringBuilder name = new StringBuilder();

        /**
         * For displaying icons or similar. Currently, this should be equal to the {@code span.type}.
         */
        @Nullable
        private String type;

        public Service withResource(String resource) {
            this.resource.append(resource);
            return this;
        }

        public StringBuilder getResource() {
            return resource;
        }

        public Service withName(String name) {
            this.name.append(name);
            return this;
        }

        public StringBuilder getName() {
            return name;
        }

        public Service withType(String type) {
            this.type = type;
            return this;
        }

        @Nullable
        public String getType() {
            return type;
        }

        public boolean hasContent() {
            return resource.length() > 0 || name.length() > 0 || type != null;
        }

        @Override
        public void resetState() {
            resource.setLength(0);
            name.setLength(0);
            type = null;
        }
    }
}
