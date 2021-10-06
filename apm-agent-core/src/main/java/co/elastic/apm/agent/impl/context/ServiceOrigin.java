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
 * Request
 * <p>
 * If a request originated from another service,
 * the service origin interface can be used to collect information about the origin service.
 */
public class ServiceOrigin implements Recyclable {

    @Nullable
    protected String id;

    @Nullable
    protected String name;

    @Nullable
    protected String version;

    @Nullable
    public String getId() {
        return id;
    }

    public ServiceOrigin withId(@Nullable String id) {
        this.id = id;
        return this;
    }

    @Nullable
    public String getName() {
        return name;
    }

    public ServiceOrigin withName(@Nullable String name) {
        this.name = name;
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
        name = null;
        version = null;
    }

    public boolean hasContent() {
        return id != null ||
                name != null ||
                version != null;
    }

    public void copyFrom(ServiceOrigin other) {
        id = other.id;
        name = other.name;
        version = other.version;
    }

}
