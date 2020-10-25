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
            AwsMetadata awsMetadata = deserialize(metadata, AwsMetadata.class);
            return awsMetadata.convert();
        } catch (Exception e) {
            logger.warn("Exception during fetching aws metadata", e);
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

    @Nullable
    public static CloudProviderInfo getGcpMetadata() {

        return null;
    }

    @Nullable
    public static CloudProviderInfo getAzureMetadata() {

        return null;
    }

    private static class AwsMetadata {
        private String accountId;
        private String architecture;
        private String availabilityZone;
        private String instanceId;
        private String instanceType;
        private String region;
        private String version;

        public CloudProviderInfo convert() {
            CloudProviderInfo cloudProviderInfo = new CloudProviderInfo();
            cloudProviderInfo.setAccount(accountId);
            cloudProviderInfo.setInstance(instanceId);
            cloudProviderInfo.setMachine(instanceType);
            cloudProviderInfo.setAvailabilityZone(availabilityZone);
            cloudProviderInfo.setRegion(region);
            cloudProviderInfo.setProvider("aws");
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
}
