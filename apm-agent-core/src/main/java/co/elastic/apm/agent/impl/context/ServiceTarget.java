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

import co.elastic.apm.agent.tracer.pooling.Recyclable;

import javax.annotation.Nullable;

public class ServiceTarget implements Recyclable, co.elastic.apm.agent.tracer.ServiceTarget {

    @Nullable
    private String type;

    private boolean typeSetByUser;

    private boolean nameSetByUser;
    private final StringBuilder name;

    private boolean onlyNameInResource = false;

    private final StringBuilder destinationResource;

    public ServiceTarget() {
        this.name = new StringBuilder();
        this.destinationResource = new StringBuilder();
    }

    public ServiceTarget withType(@Nullable String type) {
        if (typeSetByUser) {
            return this;
        }
        this.type = type;
        return this;
    }

    public ServiceTarget withUserType(@Nullable String type) {
        this.type = type;
        this.typeSetByUser = true;
        return this;
    }

    @Nullable
    public String getType() {
        return type;
    }

    public ServiceTarget withName(@Nullable CharSequence name) {
        if (name == null || name.length() == 0 || nameSetByUser) {
            return this;
        }

        this.name.setLength(0);
        this.name.append(name);
        return this;
    }

    public ServiceTarget withUserName(@Nullable CharSequence name) {
        this.name.setLength(0);
        if (name != null) {
            this.name.append(name);
        }
        this.nameSetByUser = true;
        return this;
    }

    @Nullable
    public CharSequence getName() {
        return name.length() == 0 ? null : name;
    }

    /**
     * Makes the legacy destination resource use value of name instead of the {@code "type/name"} format
     *
     * @return this
     */
    public ServiceTarget withNameOnlyDestinationResource() {
        onlyNameInResource = true;
        return this;
    }

    /**
     * Sets the name from host and port, equivalent to calling {@code withName(host+":"+port)}
     *
     * @param host host name or IP
     * @param port network port
     * @return this
     */
    public ServiceTarget withHostPortName(@Nullable CharSequence host, int port) {
        if (host == null || host.length() == 0 || nameSetByUser) {
            return this;
        }

        name.setLength(0);
        name.append(host);

        if (port > 0) {
            name.append(":").append(port);
        }
        return this;
    }

    @Override
    public void resetState() {
        type = null;
        typeSetByUser = false;
        name.setLength(0);
        nameSetByUser = false;
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

        // will allocate a bit, but it's fine as it's only expected to be called once
        // - when the span is dropped for dropped spans stats
        // - when the span is serialized
        destinationResource.setLength(0);
        if (type != null && type.length() > 0) {
            destinationResource.append(type);
            destinationResource.append("/");
        }
        destinationResource.append(name);
        return destinationResource;

    }

    /**
     * @return {@literal true} if name or type has been set by user, {@literal false} otherwise.
     */
    public boolean isSetByUser() {
        return typeSetByUser || nameSetByUser;
    }

    public void copyFrom(ServiceTarget other) {
        this.withType(other.type);
        this.withName(other.name);
        this.typeSetByUser = other.typeSetByUser;
        this.nameSetByUser = other.nameSetByUser;
        this.onlyNameInResource = other.onlyNameInResource;
    }
}
