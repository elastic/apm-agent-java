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

    // only used as a buffer to avoid re-writing
    private final StringBuilder destinationResource;

    private boolean setByUser;

    private boolean onlyNameInResource = false;

    public ServiceTarget() {
        this.name = new StringBuilder();
        this.destinationResource = new StringBuilder();
        resetState();
    }

    public ServiceTarget withType(@Nullable String type) {
        if (setByUser) {
            return this;
        }
        this.type = type;
        this.destinationResource.setLength(0); // invalidate cached value
        return this;
    }

    @Nullable
    public String getType() {
        return type;
    }

    public ServiceTarget withName(@Nullable CharSequence name) {
        if (name == null || name.length() == 0 || setByUser) {
            return this;
        }
        this.destinationResource.setLength(0); // invalidate cached value
        this.name.setLength(0);
        this.name.append(name);
        return this;
    }

    @Nullable
    public CharSequence getName() {
        return name.length() == 0 ? null : name;
    }

    /**
     * Sets the name from host and port, overwriting any prior existing resource value and removing type from resource value
     *
     * @param host host name or IP
     * @param port network port
     * @return this
     */
    public ServiceTarget withHostAndPortDestinationResource(@Nullable CharSequence host, int port) {
        if (host == null || host.length() == 0) {
            return this;
        }
        onlyNameInResource = true;

        destinationResource.setLength(0);
        name.setLength(0);
        name.append(host);

        if (port > 0) {
            name.append(":").append(port);
        }
        return this;
    }

    /**
     * Sets the user-provided service name, overwriting any prior existing resource value and removing type from resource value
     *
     * @param name user-provided destination resource
     * @return this
     */
    public ServiceTarget withUserDestinationResource(@Nullable CharSequence name) {
        if (name == null || name.length() == 0) {
            resetState();
        } else {
            this.name.setLength(0);
            this.name.append(name);
        }
        onlyNameInResource = true;
        setByUser = true;
        return this;
    }

    @Override
    public void resetState() {
        type = null;
        name.setLength(0);
        destinationResource.setLength(0);
        setByUser = false;
        onlyNameInResource = false;
    }

    public boolean hasContent() {
        return type != null || name.length() > 0;
    }

    /**
     * @return destination resource: provided by user or inferred from type and name, {@literal null} if no such exists.
     * Should only be used to ensure compatibility with features that rely on 'span.destination.service.resource'.
     */
    @Nullable
    public CharSequence getDestinationResource() {
        if (name.length() == 0) {
            return type;
        }

        if (onlyNameInResource) {
            return name;
        }

        if (destinationResource.length() == 0) {
            destinationResource.append(type);
            destinationResource.append("/");
            destinationResource.append(name);
        }
        return destinationResource;

    }

    /**
     * @return {@literal true} if it has been set by user, {@literal false} otherwise.
     */
    public boolean isSetByUser() {
        return setByUser;
    }

    public void copyFrom(ServiceTarget other) {
        this.withType(other.type);
        this.withName(other.name);
        this.destinationResource.setLength(0);
        this.destinationResource.append(other.destinationResource);
        this.setByUser = other.setByUser;
        this.onlyNameInResource = other.onlyNameInResource;
    }

}
