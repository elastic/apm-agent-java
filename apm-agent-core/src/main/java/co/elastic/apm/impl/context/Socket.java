/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 the original author or authors
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
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import javax.annotation.Nullable;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class Socket implements Recyclable {

    /**
     * Indicates whether request was sent as SSL/HTTPS request.
     */
    @JsonProperty("encrypted")
    private boolean encrypted;
    @JsonProperty("remote_address")
    @Nullable
    private String remoteAddress;

    /**
     * Indicates whether request was sent as SSL/HTTPS request.
     */
    @JsonProperty("encrypted")
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
    @JsonProperty("remote_address")
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
}
