/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2021 Elastic and contributors
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
import co.elastic.apm.agent.configuration.SpyConfiguration;
import co.elastic.apm.agent.impl.payload.CloudProviderInfo;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.stagemonitor.configuration.ConfigurationRegistry;

import javax.annotation.Nullable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static co.elastic.apm.agent.configuration.CoreConfiguration.CloudProvider.AUTO;
import static co.elastic.apm.agent.configuration.CoreConfiguration.CloudProvider.AWS;
import static co.elastic.apm.agent.configuration.CoreConfiguration.CloudProvider.GCP;
import static co.elastic.apm.agent.configuration.CoreConfiguration.CloudProvider.NONE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class MetaDataTest {

    private static ConfigurationRegistry config;
    private static CoreConfiguration coreConfiguration;
    @Nullable
    private static CoreConfiguration.CloudProvider currentCloudProvider;

    @BeforeAll
    static void setup() {
        config = SpyConfiguration.createSpyConfig();
        coreConfiguration = config.getConfig(CoreConfiguration.class);
        CloudProviderInfo cloudProviderInfo = CloudMetadataProvider.fetchAndParseCloudProviderInfo(AUTO, 1000);
        if (cloudProviderInfo != null) {
            currentCloudProvider = CoreConfiguration.CloudProvider.valueOf(cloudProviderInfo.getProvider().toUpperCase());
        }
    }

    @BeforeEach
    void reset() {
        SpyConfiguration.reset(config);
    }

    @Test
    void testCloudProvider_NONE() throws InterruptedException, ExecutionException, TimeoutException {
        // The default configuration for cloud_provide is NONE
        Future<MetaData> metaDataFuture = MetaData.create(config, null);
        assertThat(metaDataFuture).isInstanceOf(MetaData.NoWaitFuture.class);
        MetaData metaData = metaDataFuture.get(0, TimeUnit.MILLISECONDS);
        verifyMetaData(metaData, NONE);
    }

    @ParameterizedTest
    @EnumSource(value = CoreConfiguration.CloudProvider.class, names = {"AWS", "GCP", "AZURE"})
    void testCloudProvider_SingleProvider(CoreConfiguration.CloudProvider provider) throws InterruptedException, ExecutionException, TimeoutException {
        when(coreConfiguration.getCloudProvider()).thenReturn(provider);
        Future<MetaData> metaDataFuture = MetaData.create(config, null);
        assertThat(metaDataFuture).isNotInstanceOf(MetaData.NoWaitFuture.class);
        Exception timeoutException = null;
        try {
            metaDataFuture.get(0, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            timeoutException = e;
        }
        if (provider != GCP && provider != currentCloudProvider) {
            // The GCP fails quickly on DNS lookup
            assertThat(timeoutException).isInstanceOf(TimeoutException.class);
        }
        // In AWS we may need two timeouts - one for the API token and one for the metadata itself
        long timeout = (long) (coreConfiguration.geCloudMetadataDiscoveryTimeoutMs() * ((provider == AWS) ? 2.5 : 1.5));
        MetaData metaData = metaDataFuture.get(timeout, TimeUnit.MILLISECONDS);
        verifyMetaData(metaData, provider);
    }

    @Test
    void testTimeoutConfiguration() throws InterruptedException, ExecutionException, TimeoutException {
        when(coreConfiguration.getCloudProvider()).thenReturn(AUTO);
        when(coreConfiguration.geCloudMetadataDiscoveryTimeoutMs()).thenReturn(200L);
        Future<MetaData> metaDataFuture = MetaData.create(config, null);
        Exception timeoutException = null;
        try {
            metaDataFuture.get(0, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            timeoutException = e;
        }
        assertThat(timeoutException).isInstanceOf(TimeoutException.class);
        // Should not time out
        metaDataFuture.get(500, TimeUnit.MILLISECONDS);
    }

    @Test
    void testCloudProvider_AUTO() throws InterruptedException, ExecutionException, TimeoutException {
        when(coreConfiguration.getCloudProvider()).thenReturn(AUTO);
        Future<MetaData> metaDataFuture = MetaData.create(config, null);
        assertThat(metaDataFuture).isNotInstanceOf(MetaData.NoWaitFuture.class);
        Exception timeoutException = null;
        MetaData metaData;
        try {
            metaDataFuture.get(0, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            timeoutException = e;
        }
        assertThat(timeoutException).isInstanceOf(TimeoutException.class);
        // verifying discovery occurs concurrently - should take less than twice the configured timeout (AWS and Azure are timing out)
        long timeout = (long) (coreConfiguration.geCloudMetadataDiscoveryTimeoutMs() * 2.5);
        metaData = metaDataFuture.get(timeout, TimeUnit.MILLISECONDS);
        verifyMetaData(metaData, AUTO);
    }

    private void verifyMetaData(MetaData metaData, CoreConfiguration.CloudProvider cloudProvider) {
        assertThat(metaData.getService()).isNotNull();
        assertThat(metaData.getProcess()).isNotNull();
        assertThat(metaData.getSystem()).isNotNull();
        assertThat(metaData.getGlobalLabelKeys()).isEmpty();
        if (cloudProvider == currentCloudProvider || (cloudProvider == AUTO && currentCloudProvider != null)) {
            assertThat(metaData.getCloudProviderInfo()).isNotNull();
        } else {
            assertThat(metaData.getCloudProviderInfo()).isNull();
        }
    }
}
