package co.elastic.apm.agent.impl;

import co.elastic.apm.agent.impl.payload.CloudProviderInfo;
import com.dslplatform.json.DslJson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;

public class Cloud {
    private static final Logger logger = LoggerFactory.getLogger(Cloud.class);

    @Nullable
    public static CloudProviderInfo getAwsMetadata() {
        try {
            String tokenUrl = "http://169.254.169.254/latest/api/token";
            Map<String, String> headers = Map.of("X-aws-ec2-metadata-token-ttl-seconds", "300");
            String token = callRequest(tokenUrl, "PUT", headers);
            String metadataUrl = "http://169.254.169.254/latest/dynamic/instance-identity/document";
            Map<String, String> documentHeaders = Map.of("X-aws-ec2-metadata-token", token);
            String metadata = callRequest(metadataUrl, "GET", documentHeaders);
            logger.debug("Got aws metadata = {}", metadata);
            AwsMetadata awsMetadata = deserialize(metadata, AwsMetadata.class);
            return awsMetadata.convert();
        } catch (Exception e) {
            logger.debug("Exception during fetching aws metadata", e);
            return null;
        }
    }

    @Nullable
    public static CloudProviderInfo getGcpMetadata() {
        try {
            String gcpUrl = "http://metadata.google.internal/computeMetadata/v1/?recursive=true";
            Map<String, String> headers = Map.of("Metadata-Flavor", "Google");
            String metadata = callRequest(gcpUrl, "GET", headers);
            logger.debug("Got gcp metadata = {}", metadata);
            GcpMetadata gcpMetadata = deserialize(metadata, GcpMetadata.class);
            return gcpMetadata.convert();
        } catch (Exception e) {
            logger.debug("Got exception during fetching gcp metadata", e);
            return null;
        }
    }

    @Nullable
    public static CloudProviderInfo getAzureMetadata() {
        try {
            String azureUrl = "http://169.254.169.254/metadata/instance/compute?api-version=2019-08-15";
            Map<String, String> headers = Map.of("Metadata", "true");
            String metadata = callRequest(azureUrl, "GET", headers);
            logger.debug("Got azure metadata = {}", metadata);
            AzureMetadata azureMetadata = deserialize(metadata, AzureMetadata.class);
            return azureMetadata.convert();
        } catch (Exception e) {
            logger.debug("Got exception during fetching azure metadata", e);
            return null;
        }
    }

    private static <T> T deserialize(String input, Class<T> clazz) throws IOException {
        DslJson<Object> json = new DslJson<>();
        byte[] bytes = input.getBytes("UTF-8");
        return json.deserialize(clazz, bytes, bytes.length);
    }

    private static String callRequest(@Nonnull String url, @Nonnull String method, @Nonnull Map<String, String> headers) throws IOException {
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
     *     "accountId": "946960629917",
     *     "architecture": "x86_64",
     *     "availabilityZone": "us-east-2a",
     *     "billingProducts": null,
     *     "devpayProductCodes": null,
     *     "marketplaceProductCodes": null,
     *     "imageId": "ami-07c1207a9d40bc3bd",
     *     "instanceId": "i-0ae894a7c1c4f2a75",
     *     "instanceType": "t2.medium",
     *     "kernelId": null,
     *     "pendingTime": "2020-06-12T17:46:09Z",
     *     "privateIp": "172.31.0.212",
     *     "ramdiskId": null,
     *     "region": "us-east-2",
     *     "version": "2017-09-30"
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
     *     "instance": {
     *         "id": 4306570268266786072,
     *         "machineType": "projects/513326162531/machineTypes/n1-standard-1",
     *         "name": "basepi-test",
     *         "zone": "projects/513326162531/zones/us-west3-a"
     *     },
     *     "project": {"numericProjectId": 513326162531, "projectId": "elastic-apm"}
     * }
      */
    private static class GcpMetadata{
        private GcpInstance instance;
        private GcpProject project;

        public CloudProviderInfo convert() {
            CloudProviderInfo cloudProviderInfo = new CloudProviderInfo("gcp");
            if (instance != null) {
                cloudProviderInfo.setInstance(new CloudProviderInfo.ProviderInstance(instance.getId(), instance.getName()));
                cloudProviderInfo.setMachine(new CloudProviderInfo.ProviderMachine(instance.getMachineType()));
                String zone = instance.getZone();
                if (zone != null) {
                    int indexSlash = zone.lastIndexOf("/");
                    String availabilityZone = indexSlash != -1 ? zone.substring(indexSlash  + 1) : zone;
                    cloudProviderInfo.setAvailabilityZone(availabilityZone);
                    int defisLastIndex = availabilityZone.lastIndexOf("-");
                    cloudProviderInfo.setRegion(defisLastIndex != -1 ? availabilityZone.substring(0, defisLastIndex) : null);
                }
            }
            if (project != null) {
                cloudProviderInfo.setProject(new CloudProviderInfo.ProviderProject(project.getProjectId(), project.getNumericProjectId()));
            }
            return cloudProviderInfo;
        }

        public GcpInstance getInstance() {
            return instance;
        }

        public void setInstance(GcpInstance instance) {
            this.instance = instance;
        }

        public GcpProject getProject() {
            return project;
        }

        public void setProject(GcpProject project) {
            this.project = project;
        }
    }

    private static class GcpInstance {
        private Long id;
        private String name;
        private String machineType;
        private String zone;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getMachineType() {
            return machineType;
        }

        public void setMachineType(String machineType) {
            this.machineType = machineType;
        }

        public String getZone() {
            return zone;
        }

        public void setZone(String zone) {
            this.zone = zone;
        }
    }

    private static class GcpProject {
        private Long numericProjectId;
        private String projectId;

        public Long getNumericProjectId() {
            return numericProjectId;
        }

        public void setNumericProjectId(Long numericProjectId) {
            this.numericProjectId = numericProjectId;
        }

        public String getProjectId() {
            return projectId;
        }

        public void setProjectId(String projectId) {
            this.projectId = projectId;
        }
    }

    /**
     * {
     *     "location": "westus2",
     *     "name": "basepi-test",
     *     "resourceGroupName": "basepi-testing",
     *     "subscriptionId": "7657426d-c4c3-44ac-88a2-3b2cd59e6dba",
     *     "vmId": "e11ebedc-019d-427f-84dd-56cd4388d3a8",
     *     "vmScaleSetName": "",
     *     "vmSize": "Standard_D2s_v3",
     *     "zone": ""
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
