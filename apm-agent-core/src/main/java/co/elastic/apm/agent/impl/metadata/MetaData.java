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

import co.elastic.apm.agent.configuration.CoreConfiguration;
import co.elastic.apm.agent.configuration.ServerlessConfiguration;
import co.elastic.apm.agent.report.ReporterConfiguration;
import co.elastic.apm.agent.util.CompletableFuture;
import co.elastic.apm.agent.util.ExecutorUtils;
import org.stagemonitor.configuration.ConfigurationRegistry;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;

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
    @Nullable
    private final CloudProviderInfo cloudProviderInfo;

    private final ArrayList<String> globalLabelKeys;
    private final ArrayList<String> globalLabelValues;

    MetaData(ProcessInfo process, Service service, SystemInfo system, @Nullable CloudProviderInfo cloudProviderInfo,
             Map<String, String> globalLabels, @Nullable FaaSMetaDataExtension faasMetaDataExtension) {
        this.process = process;
        this.service = service;
        this.system = system;
        this.cloudProviderInfo = cloudProviderInfo;
        globalLabelKeys = new ArrayList<>(globalLabels.keySet());
        globalLabelValues = new ArrayList<>(globalLabels.values());

        if (faasMetaDataExtension != null) {
            if (service.getFramework() == null && faasMetaDataExtension.getFramework() != null) {
                service.withFramework(faasMetaDataExtension.getFramework());
            }
            if (cloudProviderInfo != null) {
                if (cloudProviderInfo.getAccount() == null) {
                    cloudProviderInfo.setAccount(faasMetaDataExtension.getAccount());
                }
                if (cloudProviderInfo.getRegion() == null) {
                    cloudProviderInfo.setRegion(faasMetaDataExtension.getRegion());
                }
            }
        }
    }

    /**
     * Creates a metadata to be used with all events sent by the agent.
     * <p>
     * NOTE: This method is blocking, possibly for several seconds, on outgoing HTTP requests, fetching for cloud
     * metadata, unless the {@link CoreConfiguration#getCloudProvider() cloud_provider} config option is set to
     * {@link CoreConfiguration.CloudProvider#NONE NONE}.
     * </p>
     *
     * @param configurationRegistry agent config
     * @param ephemeralId           unique ID generated once per agent bootstrap
     * @return metadata about the current environment
     */
    public static MetaDataFuture create(ConfigurationRegistry configurationRegistry, @Nullable String ephemeralId) {
        if (ephemeralId == null) {
            ephemeralId = UUID.randomUUID().toString();
        }

        final CoreConfiguration coreConfiguration = configurationRegistry.getConfig(CoreConfiguration.class);

        final ServerlessConfiguration serverlessConfiguration = configurationRegistry.getConfig(ServerlessConfiguration.class);
        final ServiceFactory serviceFactory = new ServiceFactory();
        final Service service = serviceFactory.createService(coreConfiguration, ephemeralId, serverlessConfiguration);
        final ProcessInfo processInformation = ProcessFactory.ForCurrentVM.INSTANCE.getProcessInformation();
        if (!configurationRegistry.getConfig(ReporterConfiguration.class).isIncludeProcessArguments()) {
            processInformation.getArgv().clear();
        }

        final ThreadPoolExecutor executor = ExecutorUtils.createThreadDaemonPool("metadata", 2, 3);
        final int metadataDiscoveryTimeoutMs = (int) coreConfiguration.getMetadataDiscoveryTimeoutMs();

        try {
            // System info creation executes external processes for hostname discovery and reads files for container/k8s metadata discovery
            final Future<SystemInfo> systemInfoFuture = executor.submit(new Callable<SystemInfo>() {
                @Override
                public SystemInfo call() {
                    return SystemInfo.create(coreConfiguration.getHostname(), metadataDiscoveryTimeoutMs, serverlessConfiguration);
                }
            });

            // Cloud provider metadata discovery relies on HTTP calls to metadata APIs, possibly in a trial-and-error fashion
            final Future<CloudProviderInfo> cloudProviderInfoFuture = executor.submit(new Callable<CloudProviderInfo>() {
                @Override
                @Nullable
                public CloudProviderInfo call() {
                    return CloudMetadataProvider.getCloudInfoProvider(coreConfiguration.getCloudProvider(), metadataDiscoveryTimeoutMs, serverlessConfiguration);
                }
            });

            final CompletableFuture<FaaSMetaDataExtension> faaSMetaDataExtensionFuture = new CompletableFuture<>();
            if (!serverlessConfiguration.runsOnAwsLambda()) {
                faaSMetaDataExtensionFuture.complete(null);
            }

            // may get to queue and not direct execution, but this task must wait for the former two to complete anyway
            Future<MetaData> metaDataFuture = executor.submit(new Callable<MetaData>() {
                @Override
                public MetaData call() throws Exception {
                    return new MetaData(
                        processInformation,
                        service,
                        systemInfoFuture.get(),
                        cloudProviderInfoFuture.get(),
                        coreConfiguration.getGlobalLabels(),
                        faaSMetaDataExtensionFuture.get()
                    );
                }
            });

            return new MetaDataFuture(metaDataFuture, faaSMetaDataExtensionFuture);

        } finally {
            executor.shutdown();
        }
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
    public CloudProviderInfo getCloudProviderInfo() {
        return cloudProviderInfo;
    }
}
