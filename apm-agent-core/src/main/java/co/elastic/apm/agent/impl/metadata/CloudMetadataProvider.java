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
import co.elastic.apm.agent.util.ExecutorUtils;
import co.elastic.apm.agent.util.UrlConnectionUtils;
import com.dslplatform.json.DslJson;
import com.dslplatform.json.JsonReader;
import com.dslplatform.json.ObjectConverter;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static co.elastic.apm.agent.configuration.CoreConfiguration.CloudProvider.AWS;
import static co.elastic.apm.agent.configuration.CoreConfiguration.CloudProvider.AZURE;
import static co.elastic.apm.agent.configuration.CoreConfiguration.CloudProvider.GCP;
import static co.elastic.apm.agent.configuration.CoreConfiguration.CloudProvider.NONE;
import static java.nio.charset.StandardCharsets.UTF_8;

public class CloudMetadataProvider {
    private static final Logger logger = LoggerFactory.getLogger(CloudMetadataProvider.class);

    private static final DslJson<Object> dslJson = new DslJson<>(new DslJson.Settings<>());

    /**
     * This method may block on multiple HTTP calls. See {@link #fetchAndParseCloudProviderInfo(CoreConfiguration.CloudProvider, int)}
     * for details.
     * @param cloudProvider the configured cloud provided - used as an optimization to lookup specific APIs instead of trial-and-error
     * @param queryTimeoutMs a configured limitation for the maximum duration of each metadata discovery task
     * @return cloud provide metadata, or {@code null} if requested not to do any lookup by using
     *          {@link co.elastic.apm.agent.configuration.CoreConfiguration.CloudProvider#NONE}
     */
    @Nullable
    static CloudProviderInfo getCloudInfoProvider(final CoreConfiguration.CloudProvider cloudProvider, final int queryTimeoutMs,
                                                  ServerlessConfiguration serverlessConfiguration) {

        if (serverlessConfiguration.runsOnAwsLambda()) {
            CloudProviderInfo awsLambdaInfo = new CloudProviderInfo("aws");
            awsLambdaInfo.setRegion(System.getenv("AWS_REGION"));
            awsLambdaInfo.setService(new CloudProviderInfo.Service("lambda"));
            return awsLambdaInfo;
        }

        if (cloudProvider == NONE) {
            return null;
        }

        return CloudMetadataProvider.fetchAndParseCloudProviderInfo(cloudProvider, queryTimeoutMs);
    }

    /**
     * Automatic discovery of cloud provider and related metadata. This method is fetching data from public APIs
     * exposed by known cloud provider services through HTTP.
     * When a specific {@link co.elastic.apm.agent.configuration.CoreConfiguration.CloudProvider} is specified, only
     * the relevant API will be queried.
     * However, when called with {@link co.elastic.apm.agent.configuration.CoreConfiguration.CloudProvider#AUTO}, all
     * known endpoints are queried concurrently. In such cases, blocking is expected to be long, bounded by the
     * HTTP requests timing out.
     *
     * @param cloudProvider   the expected {@link CoreConfiguration.CloudProvider}
     * @param queryTimeoutMs  timeout in milliseconds to limit the discovery duration
     * @return Automatically discovered {@link CloudProviderInfo}, or {@code null} if none found.
     */
    @Nullable
    static CloudProviderInfo fetchAndParseCloudProviderInfo(final CoreConfiguration.CloudProvider cloudProvider, final int queryTimeoutMs) {

        Throwable unexpectedError = null;
        CloudProviderInfo cloudProviderInfo = null;
        try {
            switch (cloudProvider) {
                case AWS: {
                    try {
                        cloudProviderInfo = getAwsMetadata(queryTimeoutMs, cloudProvider);
                    } catch (Exception e) {
                        unexpectedError = e;
                    }
                    break;
                }
                case GCP: {
                    try {
                        cloudProviderInfo = getGcpMetadata(queryTimeoutMs);
                    } catch (Exception e) {
                        unexpectedError = e;
                    }
                    break;
                }
                case AZURE: {
                    try {
                        cloudProviderInfo = getAzureMetadata(queryTimeoutMs);
                    } catch (Exception e) {
                        unexpectedError = e;
                    }
                    break;
                }
                case AUTO: {
                    cloudProviderInfo = tryAllCloudProviders(cloudProvider, queryTimeoutMs);
                }
            }
        } catch (Throwable throwable) {
            unexpectedError = throwable;
        }
        logSummary(cloudProvider, cloudProviderInfo, unexpectedError);
        return cloudProviderInfo;
    }

    @Nullable
    private static CloudProviderInfo tryAllCloudProviders(final CoreConfiguration.CloudProvider cloudProvider, final int queryTimeoutMs)
        throws InterruptedException, ExecutionException, TimeoutException {

        ExecutorService executor = ExecutorUtils.createThreadDaemonPool("cloud-metadata", 2, 2);
        CloudProviderInfo cloudProviderInfo = null;
        Future<CloudProviderInfo> awsMetadata;
        Future<CloudProviderInfo> gcpMetadata;

        try {
            awsMetadata = executor.submit(new Callable<CloudProviderInfo>() {
                @Nullable
                @Override
                public CloudProviderInfo call() {
                    CloudProviderInfo awsInfo = null;
                    try {
                        awsInfo = getAwsMetadata(queryTimeoutMs, cloudProvider);
                    } catch (Exception e) {
                        // Expected - trial and error method
                    }
                    return awsInfo;
                }
            });

            gcpMetadata = executor.submit(new Callable<CloudProviderInfo>() {
                @Nullable
                @Override
                public CloudProviderInfo call() {
                    CloudProviderInfo gcpInfo = null;
                    try {
                        gcpInfo = getGcpMetadata(queryTimeoutMs);
                    } catch (Exception e) {
                        // Expected - trial and error method
                    }
                    return gcpInfo;
                }
            });
        } finally {
            executor.shutdown();
        }

        long futureTimeout = queryTimeoutMs + 200;
        try {
            cloudProviderInfo = getAzureMetadata(queryTimeoutMs);
        } catch (Exception e) {
            // Expected - trial and error method
        }
        if (cloudProviderInfo == null) {
            cloudProviderInfo = awsMetadata.get(futureTimeout, TimeUnit.MILLISECONDS);
        }
        if (cloudProviderInfo == null) {
            cloudProviderInfo = gcpMetadata.get(futureTimeout, TimeUnit.MILLISECONDS);
        }
        return cloudProviderInfo;
    }

    private static void logSummary(CoreConfiguration.CloudProvider cloudProvider,
                                   @Nullable CloudProviderInfo cloudProviderInfo, @Nullable Throwable unexpectedError) {
        if (cloudProviderInfo == null) {
            if (cloudProvider == AWS || cloudProvider == AZURE || cloudProvider == GCP) {
                String msg = cloudProvider.name() + " is defined as the cloud_provider, but no metadata was found where expected";
                if (unexpectedError != null) {
                    logger.warn(msg, unexpectedError);
                } else {
                    logger.warn(msg);
                }
            } else if (unexpectedError != null) {
                logger.warn("Unexpected error during automatic discovery process for cloud provider", unexpectedError);
            } else {
                logger.debug("cloud_provider configured {}, no cloud metadata discovered", cloudProvider);
            }
        } else {
            logger.debug("Cloud metadata discovered: {}", cloudProviderInfo);
        }
    }

    @Nullable
    private static CloudProviderInfo getAwsMetadata(int queryTimeoutMs, CoreConfiguration.CloudProvider configuredProvider) throws IOException {
        String awsTokenUrl = "http://169.254.169.254/latest/api/token";
        Map<String, String> headers = new HashMap<>(1);
        headers.put("X-aws-ec2-metadata-token-ttl-seconds", "300");
        String token = null;
        try {
            token = executeRequest(awsTokenUrl, "PUT", headers, queryTimeoutMs);
            logger.debug("Got aws token with a length of {} characters", token.length());
        } catch (Exception e) {
            if (configuredProvider == AWS) {
                // This is expected when the token request is made from within a Docker container as described in https://github.com/elastic/apm-agent-python/pull/884
                logger.info("Unable to obtain API token, probably because running within a Docker container. This means that AWS metadata may not be available.");
            }
        }
        String awsMetadataUrl = "http://169.254.169.254/latest/dynamic/instance-identity/document";
        Map<String, String> documentHeaders = null;
        if (token != null) {
            documentHeaders = new HashMap<>(1);
            documentHeaders.put("X-aws-ec2-metadata-token", token);
        }
        String metadata = executeRequest(awsMetadataUrl, "GET", documentHeaders, queryTimeoutMs);
        logger.debug("AWS metadata retrieved");
        return deserializeAwsMetadata(metadata);
    }

    /**
     * AWS metadata example:
     * {
     * "accountId": "946960629917",
     * "architecture": "x86_64",
     * "availabilityZone": "us-east-2a",
     * "billingProducts": null,
     * "devpayProductCodes": null,
     * "marketplaceProductCodes": null,
     * "imageId": "ami-07c1207a9d40bc3bd",
     * "instanceId": "i-0ae894a7c1c4f2a75",
     * "instanceType": "t2.medium",
     * "kernelId": null,
     * "pendingTime": "2020-06-12T17:46:09Z",
     * "privateIp": "172.31.0.212",
     * "ramdiskId": null,
     * "region": "us-east-2",
     * "version": "2017-09-30"
     * }
     */
    @Nullable
    static CloudProviderInfo deserializeAwsMetadata(@Nullable String rawMetadata) throws IOException {
        if (rawMetadata == null) {
            return null;
        }
        Map<String, Object> map = deserialize(rawMetadata);

        Object accountIdValue = map.get("accountId");
        String accountId = accountIdValue instanceof String ? (String) accountIdValue : null;
        Object instanceIdValue = map.get("instanceId");
        String instanceId = instanceIdValue instanceof String ? (String) instanceIdValue : null;
        Object instanceTypeValue = map.get("instanceType");
        String instanceType = instanceTypeValue instanceof String ? (String) instanceTypeValue : null;
        Object availabilityZoneValue = map.get("availabilityZone");
        String availabilityZone = availabilityZoneValue instanceof String ? (String) availabilityZoneValue : null;
        Object regionValue = map.get("region");
        String region = regionValue instanceof String ? (String) regionValue : null;

        CloudProviderInfo cloudProviderInfo = new CloudProviderInfo("aws");
        if (instanceType != null) {
            cloudProviderInfo.setMachine(new CloudProviderInfo.ProviderMachine(instanceType));
        }
        cloudProviderInfo.setInstance(new NameAndIdField(null, instanceId));
        cloudProviderInfo.setAvailabilityZone(availabilityZone);
        cloudProviderInfo.setAccount(new CloudProviderInfo.ProviderAccount(accountId));
        cloudProviderInfo.setRegion(region);
        cloudProviderInfo.setService(new CloudProviderInfo.Service("ec2"));
        return cloudProviderInfo;
    }

    @Nullable
    private static CloudProviderInfo getGcpMetadata(int queryTimeoutMs) throws IOException {
        String gcpUrl = "http://metadata.google.internal/computeMetadata/v1/?recursive=true";
        Map<String, String> headers = new HashMap<>(1);
        headers.put("Metadata-Flavor", "Google");
        String metadata = executeRequest(gcpUrl, "GET", headers, queryTimeoutMs);
        logger.debug("GCP metadata retrieved");
        return deserializeGcpMetadata(metadata);
    }

    /**
     * GCP metadata example:
     * {
     * "instance": {
     * "id": 4306570268266786072,
     * "machineType": "projects/513326162531/machineTypes/n1-standard-1",
     * "name": "basepi-test",
     * "zone": "projects/513326162531/zones/us-west3-a"
     * },
     * "project": {"numericProjectId": 513326162531, "projectId": "elastic-apm"}
     * }
     */
    @Nullable
    static CloudProviderInfo deserializeGcpMetadata(@Nullable String rawMetadata) throws IOException {
        if (rawMetadata == null) {
            return null;
        }
        Map<String, Object> map = deserialize(rawMetadata);
        CloudProviderInfo cloudProviderInfo = new CloudProviderInfo("gcp");
        Object instanceData = map.get("instance");
        if (instanceData instanceof Map) {
            @SuppressWarnings("unchecked") Map<String, Object> instanceMap = (Map<String, Object>) instanceData;
            Long instanceId = (instanceMap.get("id") instanceof Long) ? (Long) instanceMap.get("id") : null;
            String instanceName = (instanceMap.get("name") instanceof String) ? (String) instanceMap.get("name") : null;
            String zone = instanceMap.get("zone") instanceof String ? (String) instanceMap.get("zone") : null;
            cloudProviderInfo.setInstance(new NameAndIdField(instanceName, instanceId));
            String machineType = instanceMap.get("machineType") instanceof String ? (String) instanceMap.get("machineType") : null;
            if (machineType != null) {
                String[] machinePathParts = machineType.split("/");
                cloudProviderInfo.setMachine(new CloudProviderInfo.ProviderMachine(machinePathParts[machinePathParts.length - 1]));
            }
            if (zone != null) {
                String[] zoneParts = zone.split("/");
                String availabilityZone = zoneParts[zoneParts.length - 1];
                cloudProviderInfo.setAvailabilityZone(availabilityZone);
                int hyphenLastIndex = availabilityZone.lastIndexOf("-");
                cloudProviderInfo.setRegion(hyphenLastIndex != -1 ? availabilityZone.substring(0, hyphenLastIndex) : null);
            }
        } else {
            logger.warn("Error while parsing GCP metadata - expecting the value of the 'instance' entry to be a map but it is not");
        }
        Object projectData = map.get("project");
        if (projectData instanceof Map) {
            @SuppressWarnings("unchecked") Map<String, Object> projectMap = (Map<String, Object>) projectData;
            String projectId = projectMap.get("projectId") instanceof String ? (String) projectMap.get("projectId") : null;
            Long numericProjectId = projectMap.get("numericProjectId") instanceof Long ? (Long) projectMap.get("numericProjectId") : null;
            cloudProviderInfo.setProject(new NameAndIdField(projectId, numericProjectId));
        } else {
            logger.warn("Error while parsing GCP metadata - expecting the value of the 'project' entry to be a map but it is not");
        }
        return cloudProviderInfo;
    }

    @Nullable
    private static CloudProviderInfo getAzureMetadata(int queryTimeoutMs) throws IOException {
        String azureUrl = "http://169.254.169.254/metadata/instance/compute?api-version=2019-08-15";
        Map<String, String> headers = new HashMap<>(1);
        headers.put("Metadata", "true");
        String metadata = executeRequest(azureUrl, "GET", headers, queryTimeoutMs);
        logger.debug("Azure metadata retrieved");
        return deserializeAzureMetadata(metadata);
    }

    /**
     * Azure metadata example:
     * {
     * "location": "westus2",
     * "name": "basepi-test",
     * "resourceGroupName": "basepi-testing",
     * "subscriptionId": "7657426d-c4c3-44ac-88a2-3b2cd59e6dba",
     * "vmId": "e11ebedc-019d-427f-84dd-56cd4388d3a8",
     * "vmScaleSetName": "",
     * "vmSize": "Standard_D2s_v3",
     * "zone": ""
     * }
     */
    @Nullable
    static CloudProviderInfo deserializeAzureMetadata(@Nullable String rawMetadata) throws IOException {
        if (rawMetadata == null) {
            return null;
        }
        Map<String, Object> map = deserialize(rawMetadata);

        Object subscriptionIdValue = map.get("subscriptionId");
        String subscriptionId = subscriptionIdValue instanceof String ? (String) subscriptionIdValue : null;
        Object vmIdValue = map.get("vmId");
        String vmId = vmIdValue instanceof String ? (String) vmIdValue : null;
        Object nameValue = map.get("name");
        String vmName = nameValue instanceof String ? (String) nameValue : null;
        Object resourceGroupNameValue = map.get("resourceGroupName");
        String resourceGroupName = resourceGroupNameValue instanceof String ? (String) resourceGroupNameValue : null;
        Object zoneValue = map.get("zone");
        String zone = zoneValue instanceof String ? (String) zoneValue : null;
        Object vmSizeValue = map.get("vmSize");
        String vmSize = vmSizeValue instanceof String ? (String) vmSizeValue : null;
        Object locationValue = map.get("location");
        String location = locationValue instanceof String ? (String) locationValue : null;

        CloudProviderInfo cloudProviderInfo = new CloudProviderInfo("azure");
        cloudProviderInfo.setAccount(new CloudProviderInfo.ProviderAccount(subscriptionId));
        cloudProviderInfo.setInstance(new NameAndIdField(vmName, vmId));
        cloudProviderInfo.setProject(new NameAndIdField(resourceGroupName));
        cloudProviderInfo.setAvailabilityZone(zone);
        if (vmSize != null) {
            cloudProviderInfo.setMachine(new CloudProviderInfo.ProviderMachine(vmSize));
        }
        cloudProviderInfo.setRegion(location);
        return cloudProviderInfo;
    }

    private static Map<String, Object> deserialize(String input) throws IOException {
        JsonReader<Object> reader = dslJson.newReader(input.getBytes(UTF_8));
        reader.startObject();
        // noinspection ConstantConditions,unchecked,unchecked
        return (Map<String, Object>) ObjectConverter.deserializeObject(reader);
    }

    private static String executeRequest(String url, String method, @Nullable Map<String, String> headers, int queryTimeoutMs) throws IOException {
        HttpURLConnection urlConnection = (HttpURLConnection) UrlConnectionUtils.openUrlConnectionThreadSafely(new URL(url));
        if (headers != null) {
            for (String header : headers.keySet()) {
                urlConnection.setRequestProperty(header, headers.get(header));
            }
        }
        urlConnection.setRequestMethod(method);
        urlConnection.setReadTimeout(queryTimeoutMs);
        urlConnection.setConnectTimeout(queryTimeoutMs);
        String response;
        try (BufferedReader in = new BufferedReader(new InputStreamReader(urlConnection.getInputStream()))) {
            String inputLine;
            StringBuilder content = new StringBuilder();
            while ((inputLine = in.readLine()) != null) {
                content.append(inputLine);
            }
            response = content.toString();
        } finally {
            urlConnection.disconnect();
        }
        return response;
    }
}
