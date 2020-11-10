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
import java.util.LinkedHashMap;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;

public class Cloud {
    private static final Logger logger = LoggerFactory.getLogger(Cloud.class);
    private final DslJson<Object> dslJson = new DslJson<>(new DslJson.Settings<>());

    @Nullable
    public CloudProviderInfo getAwsMetadata() {
        try {
            String tokenUrl = "http://169.254.169.254/latest/api/token";
            Map<String, String> headers = Map.of("X-aws-ec2-metadata-token-ttl-seconds", "300");
            String token = callRequest(tokenUrl, "PUT", headers);
            String metadataUrl = "http://169.254.169.254/latest/dynamic/instance-identity/document";
            Map<String, String> documentHeaders = Map.of("X-aws-ec2-metadata-token", token);
            String metadata = callRequest(metadataUrl, "GET", documentHeaders);
            logger.debug("Got aws metadata = {}", metadata);
            AwsMetadata awsMetadata = null;
            return awsMetadata.convert();
        } catch (Exception e) {
            logger.debug("Exception during fetching aws metadata", e);
            return null;
        }
    }

    @Nullable
    public CloudProviderInfo getGcpMetadata() {
        try {
            String gcpUrl = "http://metadata.google.internal/computeMetadata/v1/?recursive=true";
            Map<String, String> headers = Map.of("Metadata-Flavor", "Google");
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
            Map<String, String> headers = Map.of("Metadata", "true");
            String metadata = callRequest(azureUrl, "GET", headers);
            logger.debug("Got azure metadata = {}", metadata);
            AzureMetadata azureMetadata = null;
            return azureMetadata.convert();
        } catch (Exception e) {
            logger.debug("Got exception during fetching azure metadata", e);
            return null;
        }
    }

    protected LinkedHashMap deserialize(String input) throws IOException {
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
    private static class AwsMetadata {
        private String accountId;
        private String architecture;
        private String availabilityZone;
        private String instanceId;
        private String instanceType;
        private String region;
        private String version;

        public CloudProviderInfo convert() {
            CloudProviderInfo cloudProviderInfo = new CloudProviderInfo("aws");
            cloudProviderInfo.setAccount(new CloudProviderInfo.ProviderAccount(accountId));
            cloudProviderInfo.setInstance(new CloudProviderInfo.ProviderInstance(instanceId, null));
            cloudProviderInfo.setMachine(new CloudProviderInfo.ProviderMachine(instanceType));
            cloudProviderInfo.setAvailabilityZone(availabilityZone);
            cloudProviderInfo.setRegion(region);
            return cloudProviderInfo;
        }

        public String getAccountId() {
            return accountId;
        }

        public void setAccountId(String accountId) {
            this.accountId = accountId;
        }

        public String getArchitecture() {
            return architecture;
        }

        public void setArchitecture(String architecture) {
            this.architecture = architecture;
        }

        public String getAvailabilityZone() {
            return availabilityZone;
        }

        public void setAvailabilityZone(String availabilityZone) {
            this.availabilityZone = availabilityZone;
        }

        public String getInstanceId() {
            return instanceId;
        }

        public void setInstanceId(String instanceId) {
            this.instanceId = instanceId;
        }

        public String getInstanceType() {
            return instanceType;
        }

        public void setInstanceType(String instanceType) {
            this.instanceType = instanceType;
        }

        public String getRegion() {
            return region;
        }

        public void setRegion(String region) {
            this.region = region;
        }

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
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
    private static class AzureMetadata {
        private String location;
        private String name;
        private String resourceGroupName;
        private String subscriptionId;
        private String vmId;
        private String vmScaleSetName;
        private String vmSize;
        private String zone;

        public CloudProviderInfo convert() {
            CloudProviderInfo cloudProviderInfo = new CloudProviderInfo("azure");
            cloudProviderInfo.setAccount(new CloudProviderInfo.ProviderAccount(subscriptionId));
            cloudProviderInfo.setInstance(new CloudProviderInfo.ProviderInstance(vmId, name));
            cloudProviderInfo.setProject(new CloudProviderInfo.ProviderProject(resourceGroupName));
            cloudProviderInfo.setAvailabilityZone(zone);
            cloudProviderInfo.setMachine(new CloudProviderInfo.ProviderMachine(vmSize));
            cloudProviderInfo.setRegion(location);
            return cloudProviderInfo;
        }

        public String getLocation() {
            return location;
        }

        public void setLocation(String location) {
            this.location = location;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getResourceGroupName() {
            return resourceGroupName;
        }

        public void setResourceGroupName(String resourceGroupName) {
            this.resourceGroupName = resourceGroupName;
        }

        public String getSubscriptionId() {
            return subscriptionId;
        }

        public void setSubscriptionId(String subscriptionId) {
            this.subscriptionId = subscriptionId;
        }

        public String getVmId() {
            return vmId;
        }

        public void setVmId(String vmId) {
            this.vmId = vmId;
        }

        public String getVmScaleSetName() {
            return vmScaleSetName;
        }

        public void setVmScaleSetName(String vmScaleSetName) {
            this.vmScaleSetName = vmScaleSetName;
        }

        public String getVmSize() {
            return vmSize;
        }

        public void setVmSize(String vmSize) {
            this.vmSize = vmSize;
        }

        public String getZone() {
            return zone;
        }

        public void setZone(String zone) {
            this.zone = zone;
        }
    }
}
