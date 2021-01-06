/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2020 Elastic and contributors
 * %%
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
 * #L%
 */
package co.elastic.apm.agent.impl.payload;

import javax.annotation.Nullable;

public class CloudProviderInfo {

    private final String provider;

    @Nullable
    private String availabilityZone;

    @Nullable
    private String region;

    @Nullable
    private ProviderInstance instance;

    @Nullable
    private ProviderAccount account;

    @Nullable
    private ProviderProject project;

    @Nullable
    private ProviderMachine machine;

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
    public ProviderInstance getInstance() {
        return instance;
    }

    public void setInstance(ProviderInstance instance) {
        this.instance = instance;
    }

    @Nullable
    public ProviderAccount getAccount() {
        return account;
    }

    public void setAccount(ProviderAccount account) {
        this.account = account;
    }

    @Nullable
    public ProviderProject getProject() {
        return project;
    }

    public void setProject(ProviderProject project) {
        this.project = project;
    }

    @Nullable
    public ProviderMachine getMachine() {
        return machine;
    }

    public void setMachine(ProviderMachine machine) {
        this.machine = machine;
    }

    public static class ProviderAccount {

        @Nullable
        private String id;

        public ProviderAccount(@Nullable String id) {
            this.id = id;
        }

        @Nullable
        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        @Override
        public String toString() {
            return String.valueOf(id);
        }
    }

    public static class ProviderInstance {

        @Nullable
        private String id;
        @Nullable
        private String name;

        public ProviderInstance(@Nullable Long id, @Nullable String name) {
            this.id = id != null ? id.toString() : null;
            this.name = name;
        }

        public ProviderInstance(@Nullable String id, @Nullable String name) {
            this.id = id;
            this.name = name;
        }

        @Nullable
        public String getId() {
            return id;
        }

        @Nullable
        public String getName() {
            return name;
        }

        public void setId(String id) {
            this.id = id;
        }

        public void setName(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("{");
            sb.append("id='").append(id).append('\'');
            sb.append(", name='").append(name).append('\'');
            sb.append("}");
            return sb.toString();
        }
    }

    public static class ProviderProject {
        @Nullable
        private String id;
        @Nullable
        private String name;

        public ProviderProject(@Nullable String name) {
            this.name = name;
        }

        public ProviderProject(@Nullable String name, @Nullable Long id) {
            this.name = name;
            this.id = id != null ? id.toString() : null;
        }

        public ProviderProject(@Nullable String name, @Nullable String id) {
            this.name = name;
            this.id = id;
        }

        @Nullable
        public String getId() {
            return id;
        }

        @Nullable
        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public void setId(String id) {
            this.id = id;
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("{");
            sb.append("id='").append(id).append('\'');
            sb.append(", name='").append(name).append('\'');
            sb.append("}");
            return sb.toString();
        }
    }

    public static class ProviderMachine {

        @Nullable
        private final String type;

        public ProviderMachine(@Nullable String type) {
            this.type = type;
        }

        @Nullable
        public String getType() {
            return type;
        }

        @Override
        public String toString() {
            return String.valueOf(type);
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
        sb.append('}');
        return sb.toString();
    }
}
