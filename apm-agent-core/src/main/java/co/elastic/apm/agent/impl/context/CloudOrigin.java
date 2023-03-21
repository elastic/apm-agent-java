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

public class CloudOrigin implements Recyclable, co.elastic.apm.agent.tracer.metadata.CloudOrigin {

    @Nullable
    protected String accountId;

    @Nullable
    protected String provider;

    @Nullable
    protected String region;

    @Nullable
    protected String serviceName;

    @Nullable
    public String getAccountId() {
        return accountId;
    }

    @Override
    public CloudOrigin withAccountId(@Nullable String accountId) {
        this.accountId = accountId;
        return this;
    }

    @Nullable
    public String getProvider() {
        return provider;
    }

    @Override
    public CloudOrigin withProvider(@Nullable String provider) {
        this.provider = provider;
        return this;
    }

    @Nullable
    public String getRegion() {
        return region;
    }

    @Override
    public CloudOrigin withRegion(@Nullable String region) {
        this.region = region;
        return this;
    }

    @Nullable
    public String getServiceName() {
        return serviceName;
    }

    @Override
    public CloudOrigin withServiceName(@Nullable String serviceName) {
        this.serviceName = serviceName;
        return this;
    }

    @Override
    public void resetState() {
        accountId = null;
        provider = null;
        region = null;
        serviceName = null;
    }

    public boolean hasContent() {
        return accountId != null ||
                provider != null ||
                region != null ||
                serviceName != null;
    }

    public void copyFrom(CloudOrigin other) {
        accountId = other.accountId;
        provider = other.provider;
        region = other.region;
        serviceName = other.serviceName;
    }

}
