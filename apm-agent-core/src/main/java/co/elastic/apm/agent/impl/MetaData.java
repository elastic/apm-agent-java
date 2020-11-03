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
package co.elastic.apm.agent.impl;

import co.elastic.apm.agent.configuration.CoreConfiguration;
import co.elastic.apm.agent.impl.payload.CloudProviderInfo;
import co.elastic.apm.agent.impl.payload.ProcessFactory;
import co.elastic.apm.agent.impl.payload.ProcessInfo;
import co.elastic.apm.agent.impl.payload.Service;
import co.elastic.apm.agent.impl.payload.ServiceFactory;
import co.elastic.apm.agent.impl.payload.SystemInfo;
import co.elastic.apm.agent.report.ReporterConfiguration;
import org.stagemonitor.configuration.ConfigurationRegistry;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;

public class MetaData {

    /**
     * Service
     * (Required)
     */
    private final Service service;
    /**
     * Process
     */
    private final ProcessInfo process;
    /**
     * System
     */
    private final SystemInfo system;

    /**
     * Cloud provider metadata
     */
    private final CloudProviderInfo cloudProvider;

    private final ArrayList<String> globalLabelKeys;
    private final ArrayList<String> globalLabelValues;

    public MetaData(ProcessInfo process, Service service, SystemInfo system, CloudProviderInfo cloudProvider, Map<String, String> globalLabels) {
        this.process = process;
        this.service = service;
        this.system = system;
        this.cloudProvider = cloudProvider;
        globalLabelKeys = new ArrayList<>(globalLabels.keySet());
        globalLabelValues = new ArrayList<>(globalLabels.values());
    }

    public static MetaData create(ConfigurationRegistry configurationRegistry, @Nullable String ephemeralId) {
        if (ephemeralId == null) {
            ephemeralId = UUID.randomUUID().toString();
        }
        final CoreConfiguration coreConfiguration = configurationRegistry.getConfig(CoreConfiguration.class);
        final Service service = new ServiceFactory().createService(coreConfiguration, ephemeralId);
        final ProcessInfo processInformation = ProcessFactory.ForCurrentVM.INSTANCE.getProcessInformation();
        if (!configurationRegistry.getConfig(ReporterConfiguration.class).isIncludeProcessArguments()) {
            processInformation.getArgv().clear();
        }
        return new MetaData(processInformation, service, SystemInfo.create(coreConfiguration.getHostname()), CloudProviderInfo.create(coreConfiguration.getCloudProvider()), coreConfiguration.getGlobalLabels());
    }

    /**
     * Service
     * (Required)
     *
     * @return the service name
     */
    public Service getService() {
        return service;
    }

    /**
     * Process
     *
     * @return the process name
     */
    public ProcessInfo getProcess() {
        return process;
    }

    /**
     * System
     *
     * @return the system name
     */
    public SystemInfo getSystem() {
        return system;
    }

    public ArrayList<String> getGlobalLabelKeys() {
        return globalLabelKeys;
    }

    public ArrayList<String> getGlobalLabelValues() {
        return globalLabelValues;
    }

    @Nullable
    public CloudProviderInfo getCloudProvider() {
        return cloudProvider;
    }
}
