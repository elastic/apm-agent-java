/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2019 Elastic and contributors
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

    public Destination withAddress(CharSequence address) {
        if (address.length() > 0) {
            // remove square brackets for IPv6 addresses
            int startIndex = 0;
            if (address.charAt(0) == '[') {
                startIndex = 1;
            }
            int endIndex = address.length();
            if (address.charAt(endIndex - 1) == ']') {
                endIndex--;
            }
            this.address.append(address, startIndex, endIndex);
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
        port = -1;
        service.resetState();
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
