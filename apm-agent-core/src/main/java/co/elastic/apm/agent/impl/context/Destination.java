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
     * A custom address. If set, takes precedence over the automatically discovered one.
     */
    private final StringBuilder userAddress = new StringBuilder();

    private boolean ignoreAddress;

    /**
     * The destination's port used within this context.
     */
    private int port;

    /**
     * A custom port. If set, takes precedence over the automatically discovered one.
     */
    private int userPort;

    private boolean ignorePort;

    public Destination withAddress(@Nullable CharSequence address) {
        if (address != null) {
            appendAddress(address, 0, address.length(), this.address);
        }
        return this;
    }

    public Destination withUserAddress(@Nullable CharSequence address) {
        if (address == null || address.length() == 0) {
            ignoreAddress = true;
            userAddress.setLength(0);
        } else {
            appendAddress(address, 0, address.length(), this.userAddress);
        }
        return this;
    }

    public StringBuilder getAddress() {
        if (userAddress.length() > 0 || ignoreAddress) {
            return userAddress;
        }
        return address;
    }

    public Destination withPort(int port) {
        this.port = port;
        return this;
    }

    public Destination withUserPort(int port) {
        if (port > 0) {
            userPort = port;
        } else {
            ignorePort = true;
            userPort = 0;
        }
        return this;
    }

    public int getPort() {
        if (userPort > 0 || ignorePort) {
            return userPort;
        }
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
                    appendAddress(addressPort, 0, separator, this.address);
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

    private void appendAddress(CharSequence address, int start, int end, StringBuilder addressField) {
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
                if (addressField.length() > 0) {
                    // buffer reset required if it has already been used
                    addressField.delete(0, addressField.length());
                }
                addressField.append(address, startIndex, endIndex + 1);
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
        return userAddress.length() > 0 || address.length() > 0 || userPort > 0 || port > 0 || service.hasContent();
    }

    @Override
    public void resetState() {
        address.setLength(0);
        userAddress.setLength(0);
        ignoreAddress = false;
        port = 0;
        userPort = 0;
        ignorePort = false;
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

        /**
         * Same as {@link #resource} but used for custom/manual setting. If set, it should always take precedence
         * over {@link #resource}.
         */
        private final StringBuilder userResource = new StringBuilder();

        private boolean ignoreResource;

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
                ignoreResource = true;
                userResource.setLength(0);
            } else {
                setNewValue(this.userResource, resource);
            }
            return this;
        }

        public Service withResource(String resource) {
            setNewValue(this.resource, resource);
            return this;
        }

        private void setNewValue(StringBuilder resource, String newValue) {
            if (resource.length() > 0) {
                resource.setLength(0);
            }
            resource.append(newValue);
        }

        public StringBuilder getResource() {
            if (userResource.length() > 0 || ignoreResource) {
                return userResource;
            }
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
            return resource.length() > 0 || userResource.length() > 0;
        }

        @Override
        public void resetState() {
            resource.setLength(0);
            userResource.setLength(0);
            ignoreResource = false;
            name.setLength(0);
            type = null;
        }
    }
}
