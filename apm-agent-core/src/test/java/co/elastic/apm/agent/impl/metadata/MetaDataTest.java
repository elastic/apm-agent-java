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
import co.elastic.apm.agent.configuration.SpyConfiguration;
import co.elastic.apm.agent.util.CustomEnvVariables;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.stagemonitor.configuration.ConfigurationRegistry;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static co.elastic.apm.agent.configuration.CoreConfiguration.CloudProvider.AUTO;
import static co.elastic.apm.agent.configuration.CoreConfiguration.CloudProvider.AWS;
import static co.elastic.apm.agent.configuration.CoreConfiguration.CloudProvider.NONE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.when;

class MetaDataTest extends CustomEnvVariables {

    private static ConfigurationRegistry config;
    private static CoreConfiguration coreConfiguration;
    private static ServerlessConfiguration serverlessConfiguration;
    @Nullable
    private static CoreConfiguration.CloudProvider currentCloudProvider;

    @BeforeAll
    static void setup() {
        config = SpyConfiguration.createSpyConfig();
        coreConfiguration = config.getConfig(CoreConfiguration.class);
        serverlessConfiguration = config.getConfig(ServerlessConfiguration.class);
        // calling the blocking method directly, so we can start tests only after proper discovery
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
    void testCloudProvider_NONE_and_configured_hostname() throws InterruptedException, ExecutionException, TimeoutException {
        when(coreConfiguration.getHostname()).thenReturn("hostname");
        // The default configuration for cloud_provide is NONE
        Future<MetaData> metaDataFuture = MetaData.create(config, null);
        MetaData metaData = metaDataFuture.get(50, TimeUnit.MILLISECONDS);
        verifyMetaData(metaData, NONE, "hostname");
    }

    @Test
    void testCloudProvider_ForAWSLambda_fromEnvVariables() throws Exception {
        when(coreConfiguration.getServiceName()).thenReturn("");
        MetaData awsLambdaMetaData = createAwsLambdaMetaData();

        Service service = awsLambdaMetaData.getService();
        assertThat(service.getName()).isEqualTo("function-name");
        assertThat(service.getId()).isEqualTo("service-id");
        assertThat(service.getVersion()).isEqualTo("function-version");
        assertThat(Objects.requireNonNull(service.getRuntime()).getName()).isEqualTo("lambda-execution");
        assertThat(Objects.requireNonNull(service.getNode()).getName()).isEqualTo("lambda-log-stream");
        Framework framework = service.getFramework();
        assertThat(framework).isNotNull();
        assertThat(framework.getName()).isEqualTo("Lambda_Java_framework");
        assertThat(framework.getVersion()).isEqualTo("1.4.3");

        CloudProviderInfo cloudProviderInfo = awsLambdaMetaData.getCloudProviderInfo();
        assertThat(cloudProviderInfo).isNotNull();
        assertThat(cloudProviderInfo.getProvider()).isEqualTo("aws");
        assertThat(cloudProviderInfo.getRegion()).isEqualTo("discovered-region");
        assertThat(Objects.requireNonNull(cloudProviderInfo.getService()).getName()).isEqualTo("lambda");
        assertThat(Objects.requireNonNull(cloudProviderInfo.getAccount()).getId()).isEqualTo("accountId");
    }

    @Test
    void testCloudProvider_ForAWSLambda_fromConfiguration() throws Exception {
        when(coreConfiguration.getServiceName()).thenReturn("test-service");
        when(coreConfiguration.getServiceNodeName()).thenReturn("test-service-node-name");
        when(coreConfiguration.getServiceVersion()).thenReturn("test-service-version");
        MetaData awsLambdaMetaData = createAwsLambdaMetaData();

        Service service = awsLambdaMetaData.getService();
        assertThat(service.getName()).isEqualTo("test-service");
        assertThat(service.getVersion()).isEqualTo("test-service-version");
        assertThat(Objects.requireNonNull(service.getNode()).getName()).isEqualTo("test-service-node-name");
    }

    private MetaData createAwsLambdaMetaData() throws Exception {
        when(serverlessConfiguration.runsOnAwsLambda()).thenReturn(true);
        when(coreConfiguration.getHostname()).thenReturn("hostname");

        final Map<String, String> awsLambdaEnvVariables = new HashMap<>();
        awsLambdaEnvVariables.put("AWS_LAMBDA_FUNCTION_NAME", "function-name");
        awsLambdaEnvVariables.put("AWS_LAMBDA_FUNCTION_VERSION", "function-version");
        awsLambdaEnvVariables.put("AWS_EXECUTION_ENV", "lambda-execution");
        awsLambdaEnvVariables.put("AWS_LAMBDA_LOG_STREAM_NAME", "lambda-log-stream");

        final ConfigurationRegistry finalConfig = MetaDataTest.config;
        MetaDataFuture metaDataFuture = callWithCustomEnvVariables(awsLambdaEnvVariables, () -> MetaData.create(finalConfig, null));
        assertThatExceptionOfType(TimeoutException.class).isThrownBy(() -> metaDataFuture.get(50, TimeUnit.MILLISECONDS));

        metaDataFuture.getFaaSMetaDataExtensionFuture().complete(new FaaSMetaDataExtension(
            new Framework("Lambda_Java_framework", "1.4.3"),
            "service-id",
            new NameAndIdField(null, "accountId"),
            "discovered-region"
        ));
        return metaDataFuture.get(50, TimeUnit.MILLISECONDS);
    }

    @ParameterizedTest
    @EnumSource(value = CoreConfiguration.CloudProvider.class, names = {"AWS", "GCP", "AZURE"})
    void testCloudProvider_SingleProvider(CoreConfiguration.CloudProvider provider) throws InterruptedException, ExecutionException, TimeoutException {
        when(coreConfiguration.getCloudProvider()).thenReturn(provider);
        Future<MetaData> metaDataFuture = MetaData.create(config, null);
        // In AWS we may need two timeouts - one for the API token and one for the metadata itself
        long timeout = (long) (coreConfiguration.geMetadataDiscoveryTimeoutMs() * ((provider == AWS) ? 2.5 : 1.5));
        MetaData metaData = metaDataFuture.get(timeout, TimeUnit.MILLISECONDS);
        verifyMetaData(metaData, provider);
    }

    @Test
    void testTimeoutConfiguration() throws InterruptedException, ExecutionException, TimeoutException {
        when(coreConfiguration.getCloudProvider()).thenReturn(AUTO);
        when(coreConfiguration.geMetadataDiscoveryTimeoutMs()).thenReturn(200L);
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
        Exception timeoutException = null;
        MetaData metaData;
        try {
            metaDataFuture.get(0, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            timeoutException = e;
        }
        assertThat(timeoutException).isInstanceOf(TimeoutException.class);
        // verifying discovery occurs concurrently - should take less than twice the configured timeout (AWS and Azure are timing out)
        long timeout = (long) (coreConfiguration.geMetadataDiscoveryTimeoutMs() * 2.5);
        metaData = metaDataFuture.get(timeout, TimeUnit.MILLISECONDS);
        verifyMetaData(metaData, AUTO);
    }

    private void verifyMetaData(MetaData metaData, CoreConfiguration.CloudProvider cloudProvider) {
        verifyMetaData(metaData, cloudProvider, null);
    }

    private void verifyMetaData(MetaData metaData, CoreConfiguration.CloudProvider cloudProvider, @Nullable String configuredHostname) {
        assertThat(metaData.getService()).isNotNull();
        assertThat(metaData.getProcess()).isNotNull();
        SystemInfo system = metaData.getSystem();
        assertThat(system).isNotNull();
        if (configuredHostname == null) {
            assertThat(system.getDetectedHostname()).isNotNull();
            assertThat(system.getConfiguredHostname()).isNull();
            assertThat(system.getHostname()).isEqualTo(system.getDetectedHostname());
        } else {
            assertThat(system.getDetectedHostname()).isNull();
            assertThat(system.getConfiguredHostname()).isEqualTo(configuredHostname);
            assertThat(system.getHostname()).isEqualTo(system.getConfiguredHostname());
        }
        assertThat(metaData.getGlobalLabelKeys()).isEmpty();
        if (cloudProvider == currentCloudProvider || (cloudProvider == AUTO && currentCloudProvider != null)) {
            assertThat(metaData.getCloudProviderInfo()).isNotNull();
        } else {
            assertThat(metaData.getCloudProviderInfo()).isNull();
        }
    }
}
