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

@SuppressWarnings("StringBufferReplaceableByString")
public class CloudProviderInfo {

    private final String provider;

    @Nullable
    private String availabilityZone;

    @Nullable
    private String region;

    @Nullable
    private NameAndIdField instance;

    @Nullable
    private NameAndIdField account;

    @Nullable
    private NameAndIdField project;

    @Nullable
    private ProviderMachine machine;

    @Nullable
    private Service service;

    public CloudProviderInfo(String provider) {
        this.provider = provider;
    }

    public String getProvider() {
        return provider;
    }

    @Nullable
    public String getAvailabilityZone() {
        return availabilityZone;
    }

    public void setAvailabilityZone(@Nullable String availabilityZone) {
        this.availabilityZone = availabilityZone;
    }

    @Nullable
    public String getRegion() {
        return region;
    }

    public void setRegion(@Nullable String region) {
        this.region = region;
    }

    @Nullable
    public NameAndIdField getInstance() {
        return instance;
    }

    public void setInstance(@Nullable NameAndIdField instance) {
        this.instance = instance;
    }

    @Nullable
    public NameAndIdField getAccount() {
        return account;
    }

    public void setAccount(@Nullable NameAndIdField account) {
        this.account = account;
    }

    @Nullable
    public NameAndIdField getProject() {
        return project;
    }

    public void setProject(@Nullable NameAndIdField project) {
        this.project = project;
    }

    @Nullable
    public ProviderMachine getMachine() {
        return machine;
    }

    public void setMachine(@Nullable ProviderMachine machine) {
        this.machine = machine;
    }

    @Nullable
    public Service getService() {
        return service;
    }

    public void setService(@Nullable Service service) {
        this.service = service;
    }

    /**
     * Currently, we only fill the {@code account.id} field, however the intake API supports the {@code account.name}
     * as well and we may wont to use it in the future. This is a convenience type for the time being that can be
     * removed if we get to that.
     */
    public static class ProviderAccount extends NameAndIdField {

        public ProviderAccount(@Nullable String id) {
            super(null, id);
        }

        @Override
        public String toString() {
            return String.valueOf(id);
        }
    }

    public static class ProviderMachine {

        private final String type;

        public ProviderMachine(String type) {
            this.type = type;
        }

        public String getType() {
            return type;
        }

        @Override
        public String toString() {
            return type;
        }
    }

    public static class Service {

        private final String name;

        public Service(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("CloudProviderInfo{");
        sb.append("provider='").append(provider).append('\'');
        sb.append(", availabilityZone='").append(availabilityZone).append('\'');
        sb.append(", region='").append(region).append('\'');
        sb.append(", instance=").append(instance);
        sb.append(", account=").append(account);
        sb.append(", project=").append(project);
        sb.append(", machine=").append(machine);
        sb.append(", service=").append(service);
        sb.append('}');
        return sb.toString();
    }
}
