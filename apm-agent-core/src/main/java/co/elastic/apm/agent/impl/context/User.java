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


public class User implements Recyclable, co.elastic.apm.agent.tracer.metadata.User {

    /**
     * Domain of the logged in user
     */
    @Nullable
    private String domain;

    /**
     * Identifier of the logged in user, e.g. the primary key of the user
     */
    @Nullable
    private String id;
    /**
     * Email of the logged in user
     */
    @Nullable
    private String email;
    /**
     * The username of the logged in user
     */
    @Nullable
    private String username;


    /**
     * Domain of the logged in user
     */
    @Nullable
    public String getDomain() {
        return domain;
    }

    /**
     * Domain of the logged in user
     */
    public User withDomain(@Nullable String domain) {
        this.domain = domain;
        return this;
    }

    /**
     * Identifier of the logged in user, e.g. the primary key of the user
     */
    @Nullable
    public String getId() {
        return id;
    }

    /**
     * Identifier of the logged in user, e.g. the primary key of the user
     */
    public User withId(@Nullable String id) {
        this.id = id;
        return this;
    }

    /**
     * Email of the logged in user
     */
    @Nullable
    public String getEmail() {
        return email;
    }

    /**
     * Email of the logged in user
     */
    public User withEmail(@Nullable String email) {
        this.email = email;
        return this;
    }

    @Override
    @Nullable
    public String getUsername() {
        return username;
    }

    @Override
    public User withUsername(@Nullable String username) {
        this.username = username;
        return this;
    }

    @Override
    public void resetState() {
        domain = null;
        id = null;
        email = null;
        username = null;
    }

    public void copyFrom(User other) {
        this.domain = other.domain;
        this.email = other.email;
        this.id = other.id;
        this.username = other.username;
    }

    public boolean hasContent() {
        return domain != null || id != null || email != null || username != null;
    }
}
