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
import co.elastic.apm.agent.util.ExecutorUtils;
import co.elastic.apm.agent.util.UrlConnectionUtils;
import com.dslplatform.json.DslJson;
import com.dslplatform.json.JsonReader;
import com.dslplatform.json.ObjectConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static co.elastic.apm.agent.configuration.CoreConfiguration.CloudProvider.AWS;
import static co.elastic.apm.agent.configuration.CoreConfiguration.CloudProvider.AZURE;
import static co.elastic.apm.agent.configuration.CoreConfiguration.CloudProvider.GCP;
import static java.nio.charset.StandardCharsets.UTF_8;

public class CloudMetadataProvider {
    private static final Logger logger = LoggerFactory.getLogger(CloudMetadataProvider.class);

    private static final DslJson<Object> dslJson = new DslJson<>(new DslJson.Settings<>());

    @Nullable
    static CloudProviderInfo fetchAndParseCloudProviderInfo(CoreConfiguration.CloudProvider cloudProvider, final int queryTimeoutMs) {

        Throwable unexpectedError = null;
        CloudProviderInfo cloudProviderInfo = null;
        try {
            switch (cloudProvider) {
                case AWS: {
                    try {
                        cloudProviderInfo = getAwsMetadata(queryTimeoutMs);
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
                    ExecutorService executor = ExecutorUtils.createThreadDaemonPool("apm-cloud-metadata", 2, 2);
                    Future<CloudProviderInfo> awsMetadata;
                    Future<CloudProviderInfo> gcpMetadata;
                    try {
                        awsMetadata = executor.submit(new Callable<CloudProviderInfo>() {
                            @Override
                            public CloudProviderInfo call() {
                                CloudProviderInfo awsInfo = null;
                                try {
                                    awsInfo = getAwsMetadata(queryTimeoutMs);
                                } catch (Exception e) {
                                    // Expected - trial and error method
                                }
                                return awsInfo;
                            }
                        });

                        gcpMetadata = executor.submit(new Callable<CloudProviderInfo>() {
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
                    break;
                }
            }
        } catch (Throwable throwable) {
            unexpectedError = throwable;
        }
        logSummary(cloudProvider, cloudProviderInfo, unexpectedError);
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
    private static CloudProviderInfo getAwsMetadata(int queryTimeoutMs) throws IOException {
        String awsTokenUrl = "http://169.254.169.254/latest/api/token";
        Map<String, String> headers = new HashMap<>(1);
        headers.put("X-aws-ec2-metadata-token-ttl-seconds", "300");
        String token = executeRequest(awsTokenUrl, "PUT", headers, queryTimeoutMs);
        logger.debug("Got aws token with a length of {} characters", token.length());
        String awsMetadataUrl = "http://169.254.169.254/latest/dynamic/instance-identity/document";
        Map<String, String> documentHeaders = new HashMap<>(1);
        documentHeaders.put("X-aws-ec2-metadata-token", token);
        String metadata = executeRequest(awsMetadataUrl, "GET", documentHeaders, queryTimeoutMs);
        logger.debug("Got aws metadata = {}", metadata);
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
        cloudProviderInfo.setMachine(new CloudProviderInfo.ProviderMachine(instanceType));
        cloudProviderInfo.setInstance(new CloudProviderInfo.ProviderInstance(instanceId, null));
        cloudProviderInfo.setAvailabilityZone(availabilityZone);
        cloudProviderInfo.setAccount(new CloudProviderInfo.ProviderAccount(accountId));
        cloudProviderInfo.setRegion(region);
        return cloudProviderInfo;
    }

    @Nullable
    private static CloudProviderInfo getGcpMetadata(int queryTimeoutMs) throws IOException {
        String gcpUrl = "http://metadata.google.internal/computeMetadata/v1/?recursive=true";
        Map<String, String> headers = new HashMap<>(1);
        headers.put("Metadata-Flavor", "Google");
        String metadata = executeRequest(gcpUrl, "GET", headers, queryTimeoutMs);
        logger.debug("Got gcp metadata = {}", metadata);
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
            cloudProviderInfo.setInstance(new CloudProviderInfo.ProviderInstance(instanceId, instanceName));
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
            cloudProviderInfo.setProject(new CloudProviderInfo.ProviderProject(projectId, numericProjectId));
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
        logger.debug("Got azure metadata = {}", metadata);
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
        cloudProviderInfo.setInstance(new CloudProviderInfo.ProviderInstance(vmId, vmName));
        cloudProviderInfo.setProject(new CloudProviderInfo.ProviderProject(resourceGroupName));
        cloudProviderInfo.setAvailabilityZone(zone);
        cloudProviderInfo.setMachine(new CloudProviderInfo.ProviderMachine(vmSize));
        cloudProviderInfo.setRegion(location);
        return cloudProviderInfo;
    }

    private static Map<String, Object> deserialize(String input) throws IOException {
        JsonReader<Object> reader = dslJson.newReader(input.getBytes(UTF_8));
        reader.startObject();
        // noinspection ConstantConditions,unchecked,unchecked
        return (Map<String, Object>) ObjectConverter.deserializeObject(reader);
    }

    private static String executeRequest(String url, String method, Map<String, String> headers, int queryTimeoutMs) throws IOException {
        HttpURLConnection urlConnection = (HttpURLConnection) UrlConnectionUtils.openUrlConnectionThreadSafely(new URL(url));
        for (String header : headers.keySet()) {
            urlConnection.setRequestProperty(header, headers.get(header));
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
