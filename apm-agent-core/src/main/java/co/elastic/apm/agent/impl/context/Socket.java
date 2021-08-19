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

public class Socket implements Recyclable {

    /**
     * Indicates whether request was sent as SSL/HTTPS request.
     */
    private boolean encrypted;
    @Nullable
    private String remoteAddress;

    /**
     * Indicates whether request was sent as SSL/HTTPS request.
     */
    public boolean isEncrypted() {
        return encrypted;
    }

    /**
     * Indicates whether request was sent as SSL/HTTPS request.
     */
    public Socket withEncrypted(boolean encrypted) {
        this.encrypted = encrypted;
        return this;
    }

    @Nullable
    public String getRemoteAddress() {
        return remoteAddress;
    }

    public Socket withRemoteAddress(@Nullable String remoteAddress) {
        this.remoteAddress = remoteAddress;
        return this;
    }

    @Override
    public void resetState() {
        encrypted = false;
        remoteAddress = null;
    }

    public void copyFrom(Socket other) {
        this.encrypted = other.encrypted;
        this.remoteAddress = other.remoteAddress;
    }

    public boolean hasContent() {
        return remoteAddress != null;
    }
}
