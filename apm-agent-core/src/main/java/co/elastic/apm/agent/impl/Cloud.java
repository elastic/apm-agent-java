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

import co.elastic.apm.agent.impl.payload.CloudProviderInfo;
import com.dslplatform.json.DslJson;
import com.dslplatform.json.JsonReader;
import com.dslplatform.json.ObjectConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;

public class Cloud {
    private static final Logger logger = LoggerFactory.getLogger(Cloud.class);

    private final DslJson<Object> dslJson = new DslJson<>(new DslJson.Settings<>());

    @Nullable
    public CloudProviderInfo getAwsMetadata() {
        try {
            String awsTokenUrl = "http://localhost:8080/latest/api/token";
            Map<String, String> headers = new HashMap<>(1);
            headers.put("X-aws-ec2-metadata-token-ttl-seconds", "300");
            String token = callRequest(awsTokenUrl, "PUT", headers);
            String awsMetadataUrl = "http://localhost:8080/latest/dynamic/instance-identity/document";
            Map<String, String> documentHeaders = new HashMap<>(1);
            documentHeaders.put("X-aws-ec2-metadata-token", token);
            String metadata = callRequest(awsMetadataUrl, "GET", documentHeaders);
            logger.debug("Got aws metadata = {}", metadata);
            return convertAwsMetadata(metadata);
        } catch (Exception e) {
            logger.debug("Exception during fetching aws metadata", e);
            return null;
        }
    }

    /**
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
    protected CloudProviderInfo convertAwsMetadata(@Nullable String data) throws IOException {
        if (data == null) {
            return null;
        }
        LinkedHashMap<String, Object> map = deserialize(data);

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
    public CloudProviderInfo getGcpMetadata() {
        try {
            String gcpUrl = "http://metadata.google.internal/computeMetadata/v1/?recursive=true";
            Map<String, String> headers = new HashMap<>(1);
            headers.put("Metadata-Flavor", "Google");
            String metadata = callRequest(gcpUrl, "GET", headers);
            logger.debug("Got gcp metadata = {}", metadata);
            return convertGcpMetadata(metadata);
        } catch (Exception e) {
            logger.debug("Got exception during fetching gcp metadata", e);
            return null;
        }
    }

    /**
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
    protected CloudProviderInfo convertGcpMetadata(@Nullable String data) throws IOException {
        if (data == null) {
            return null;
        }
        LinkedHashMap<String, Object> map = deserialize(data);
        CloudProviderInfo cloudProviderInfo = new CloudProviderInfo("gcp");
        Object instanceData = map.get("instance");
        if (instanceData instanceof LinkedHashMap) {
            LinkedHashMap<String, Object> instanceMap = (LinkedHashMap) instanceData;
            Long instanceId = (instanceMap.get("id") instanceof Long) ? (Long) instanceMap.get("id") : null;
            String instanceName = (instanceMap.get("name") instanceof String) ? (String) instanceMap.get("name") : null;
            cloudProviderInfo.setInstance(new CloudProviderInfo.ProviderInstance(instanceId, instanceName));
            String machineType = instanceMap.get("machineType") instanceof String ? (String) instanceMap.get("machineType") : null;
            cloudProviderInfo.setMachine(new CloudProviderInfo.ProviderMachine(machineType));
            String zone = instanceMap.get("zone") instanceof String ? (String) instanceMap.get("zone") : null;
            if (zone != null) {
                int indexSlash = zone.lastIndexOf("/");
                String availabilityZone = indexSlash != -1 ? zone.substring(indexSlash + 1) : zone;
                cloudProviderInfo.setAvailabilityZone(availabilityZone);
                int hyphenLastIndex = availabilityZone.lastIndexOf("-");
                cloudProviderInfo.setRegion(hyphenLastIndex != -1 ? availabilityZone.substring(0, hyphenLastIndex) : null);
            }
        }
        Object projectData = map.get("project");
        if (projectData instanceof LinkedHashMap) {
            LinkedHashMap<String, Object> projectMap = (LinkedHashMap) projectData;
            String projectId = projectMap.get("projectId") instanceof String ? (String) projectMap.get("projectId") : null;
            Long numericProjectId = projectMap.get("numericProjectId") instanceof Long ? (Long) projectMap.get("numericProjectId") : null;
            cloudProviderInfo.setProject(new CloudProviderInfo.ProviderProject(projectId, numericProjectId));
        }
        return cloudProviderInfo;
    }

    @Nullable
    public CloudProviderInfo getAzureMetadata() {
        try {
            String azureUrl = "http://169.254.169.254/metadata/instance/compute?api-version=2019-08-15";
            Map<String, String> headers = new HashMap<>(1);
            headers.put("Metadata", "true");
            String metadata = callRequest(azureUrl, "GET", headers);
            logger.debug("Got azure metadata = {}", metadata);
            return convertAzureMetadata(metadata);
        } catch (Exception e) {
            logger.debug("Got exception during fetching azure metadata", e);
            return null;
        }
    }

    /**
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
    protected CloudProviderInfo convertAzureMetadata(@Nullable String data) throws IOException {
        if (data == null) {
            return null;
        }
        LinkedHashMap<String, Object> map = deserialize(data);

        Object subscriptionIdValue = map.get("subscriptionId");
        String subscriptionId = subscriptionIdValue instanceof String ? (String) subscriptionIdValue : null;
        Object vmIdValue = map.get("vmId");
        String vmId = vmIdValue instanceof String ? (String) vmIdValue : null;
        Object nameValue = map.get("name");
        String name = nameValue instanceof String ? (String) nameValue : null;
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
        cloudProviderInfo.setInstance(new CloudProviderInfo.ProviderInstance(vmId, name));
        cloudProviderInfo.setProject(new CloudProviderInfo.ProviderProject(resourceGroupName));
        cloudProviderInfo.setAvailabilityZone(zone);
        cloudProviderInfo.setMachine(new CloudProviderInfo.ProviderMachine(vmSize));
        cloudProviderInfo.setRegion(location);
        return cloudProviderInfo;
    }

    private LinkedHashMap deserialize(String input) throws IOException {
        JsonReader<Object> reader = dslJson.newReader(input.getBytes(UTF_8));
        reader.startObject();
        return (LinkedHashMap) ObjectConverter.deserializeObject(reader);
    }

    private String callRequest(@Nonnull String url, @Nonnull String method, @Nonnull Map<String, String> headers) throws IOException {
        URL myURL = new URL(url);
        HttpURLConnection urlConnection = (HttpURLConnection) myURL.openConnection();
        for (String header : headers.keySet()) {
            urlConnection.setRequestProperty(header, headers.get(header));
        }
        urlConnection.setRequestMethod(method);
        urlConnection.setReadTimeout(3000);
        urlConnection.setConnectTimeout(3000);
        String response = null;
        try (BufferedReader in = new BufferedReader(new InputStreamReader(urlConnection.getInputStream()))) {
            String inputLine;
            StringBuffer content = new StringBuffer();
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
