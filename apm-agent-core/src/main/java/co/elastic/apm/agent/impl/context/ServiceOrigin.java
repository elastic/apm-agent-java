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

/**
 * Request
 * <p>
 * If a request originated from another service,
 * the service origin interface can be used to collect information about the origin service.
 */
public class ServiceOrigin implements Recyclable {

    @Nullable
    private String id;

    private final StringBuilder name = new StringBuilder();

    @Nullable
    private String version;

    @Nullable
    public String getId() {
        return id;
    }

    public ServiceOrigin withId(@Nullable String id) {
        this.id = id;
        return this;
    }

    public StringBuilder getName() {
        return name;
    }

    public ServiceOrigin withName(@Nullable CharSequence name) {
        this.name.setLength(0);
        if (name != null) {
            this.name.append(name);
        }
        return this;
    }

    public ServiceOrigin appendToName(@Nullable String namePart) {
        if (namePart != null) {
            this.name.append(namePart);
        }
        return this;
    }

    @Nullable
    public String getVersion() {
        return version;
    }

    public ServiceOrigin withVersion(@Nullable String version) {
        this.version = version;
        return this;
    }

    @Override
    public void resetState() {
        id = null;
        name.setLength(0);
        version = null;
    }

    public boolean hasContent() {
        return id != null ||
            name.length() > 0 ||
            version != null;
    }

    public void copyFrom(ServiceOrigin other) {
        id = other.id;
        withName(other.getName());
        version = other.version;
    }

}
