/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 Elastic and contributors
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

package co.elastic.apm.impl.context;

import co.elastic.apm.objectpool.Recyclable;

import javax.annotation.Nullable;


/**
 * User
 * <p>
 * Describes the authenticated User for a request.
 */
public class User implements Recyclable {

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

    /**
     * The username of the logged in user
     */
    @Nullable
    public String getUsername() {
        return username;
    }

    /**
     * The username of the logged in user
     */
    public User withUsername(@Nullable String username) {
        this.username = username;
        return this;
    }

    @Override
    public void resetState() {
        id = null;
        email = null;
        username = null;
    }

    public void copyFrom(User other) {
        this.email = other.email;
        this.id = other.id;
        this.username = other.username;
    }

    public boolean hasContent() {
        return id != null || email != null || username != null;
    }
}
