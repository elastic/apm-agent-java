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

/**
 * Represents a target service
 */
public class ServiceTarget implements Recyclable {

    @Nullable
    private String type;

    private final StringBuilder name;

    private final StringBuilder destinationResource;

    private boolean destinationResourceSetByUser;

    ServiceTarget() {
        this.name = new StringBuilder();
        this.destinationResource = new StringBuilder();
    }

    public ServiceTarget withType(@Nullable String type) {
        this.type = type;
        if (!destinationResourceSetByUser) {
            destinationResource.setLength(0);
        }
        return this;
    }

    @Nullable
    public String getType() {
        return type;
    }

    public ServiceTarget withName(@Nullable CharSequence name) {
        this.name.setLength(0);
        if (name != null) {
            this.name.append(name);
        }
        if (!destinationResourceSetByUser) {
            destinationResource.setLength(0);
        }
        return this;
    }

    @Nullable
    public CharSequence getName() {
        return name.length() == 0 ? null : name;
    }

    /**
     * @return internal destination resource
     */
    public StringBuilder getRawDestinationResource(){
        return destinationResource;
    }

    // TODO test for null input
    public ServiceTarget withUserDestinationResource(@Nullable CharSequence userDestinationResource) {
        if (userDestinationResource != null && userDestinationResource.length() > 0) {
            destinationResource.setLength(0);
            destinationResource.append(userDestinationResource);
            destinationResourceSetByUser = true;
        }
        return this;
    }

    public ServiceTarget withHostAndPortDestinationResource(@Nullable CharSequence host, int port) {
        if (host == null || host.length() == 0) {
            return this;
        }
        destinationResource.setLength(0);
        destinationResource.append(host);

        if (port > 0) {
            destinationResource.append(":").append(port);
        }

        return this;
    }

    @Override
    public void resetState() {
        type = null;
        name.setLength(0);
        destinationResource.setLength(0);
        destinationResourceSetByUser = false;
    }

    public boolean hasContent() {
        return type != null
            || name.length() > 0
            || destinationResource.length() > 0;
    }

    /**
     * @return destination resource: provided by user or inferred from type and name, {@literal null} if no such exists
     */
    @Nullable
    public CharSequence getDestinationResource() {
        if (destinationResource.length() > 0) {
            return destinationResource;
        }

        if (type == null || type.isEmpty()) {
            return null;
        }

        if (name.length() == 0) {
            return type;
        }

        destinationResource.append(type);
        destinationResource.append("/");
        destinationResource.append(name);
        return destinationResource;
    }

    public boolean isDestinationResourceSetByUser(){
        return destinationResourceSetByUser;
    }

    /**
     * Parses an existing destination resource as service target for compatibility
     *
     * @param destinationResource destination resource string
     */
    public void copyFromDestinationResource(String destinationResource) {
        int slashIndex = destinationResource.indexOf('/');
        if (slashIndex <= 0 || slashIndex == destinationResource.length() - 1) {
            this.type = destinationResource;
        } else {
            this.type = destinationResource.substring(0, slashIndex);
            this.name.append(destinationResource, slashIndex + 1, destinationResource.length());
        }
    }

    public void copyFrom(ServiceTarget other) {
        this.withType(other.type);
        this.withName(other.name);
        this.destinationResource.setLength(0);
        this.destinationResource.append(other.destinationResource);
        this.destinationResourceSetByUser = other.destinationResourceSetByUser;
    }

}
