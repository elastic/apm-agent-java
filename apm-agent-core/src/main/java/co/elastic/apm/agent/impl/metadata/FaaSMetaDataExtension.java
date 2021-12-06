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
package co.elastic.apm.agent.impl.metadata;

import javax.annotation.Nullable;

/**
 * An extension for the general metadata.
 * In FaaS environments, there are some metadata that may be discovered only very late. In such cases, the agent will
 * need to block any serialization actions until the metadata is completed. For example, in AWS Lambda, these metadata
 * will only be completed at the first function invocation.
 */
public class FaaSMetaDataExtension {
    /**
     * Name and version of the web framework used
     */
    @Nullable
    private final Framework framework;

    /**
     * ID of the service emitting this event
     */
    @Nullable
    private final String serviceId;

    /**
     * Details of the cloud account owning the current service
     */
    @Nullable
    private final NameAndIdField account;

    /**
     * Cloud region
     */
    @Nullable
    private final String region;

    public FaaSMetaDataExtension(@Nullable Framework framework, @Nullable String serviceId, @Nullable NameAndIdField account, @Nullable String region) {
        this.framework = framework;
        this.serviceId = serviceId;
        this.account = account;
        this.region = region;
    }

    @Nullable
    public Framework getFramework() {
        return framework;
    }

    @Nullable
    public String getServiceId() {
        return serviceId;
    }

    @Nullable
    public NameAndIdField getAccount() {
        return account;
    }

    @Nullable
    public String getRegion() {
        return region;
    }
}
