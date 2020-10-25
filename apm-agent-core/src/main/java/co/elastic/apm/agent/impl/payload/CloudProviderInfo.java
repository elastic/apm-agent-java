package co.elastic.apm.agent.impl.payload;

import co.elastic.apm.agent.impl.Cloud;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.stagemonitor.util.StringUtils;

import javax.annotation.Nullable;

public class CloudProviderInfo {
    private static final Logger logger = LoggerFactory.getLogger(CloudProviderInfo.class);

    private String provider;

    private String instance;

    private String availabilityZone;

    private String machine;

    private String region;

    // aws - azure
    private String account;
    // gcp - azure
    private String project;

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getInstance() {
        return instance;
    }

    public void setInstance(String instance) {
        this.instance = instance;
    }

    public String getAvailabilityZone() {
        return availabilityZone;
    }

    public void setAvailabilityZone(String availabilityZone) {
        this.availabilityZone = availabilityZone;
    }

    public String getMachine() {
        return machine;
    }

    public void setMachine(String machine) {
        this.machine = machine;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getAccount() {
        return account;
    }

    public void setAccount(String account) {
        this.account = account;
    }

    public String getProject() {
        return project;
    }

    public void setProject(String project) {
        this.project = project;
    }

    @Nullable
    public static CloudProviderInfo create(@Nullable String cloudProviderName) {
        cloudProviderName = cloudProviderName != null ? cloudProviderName.toLowerCase() : null;
        if (StringUtils.isEmpty(cloudProviderName) || "false".equals(cloudProviderName)) {
            logger.debug("cloud_provider configuration is null or `FALSE`.");
            return null;
        }
        CloudProviderInfo data = null;
        if ("aws".equals(cloudProviderName)) {
            data = Cloud.getAwsMetadata();
            if (data == null) {
                logger.warn("Cloud provider {} defined, but no metadata was found.", cloudProviderName);
            }
            return data;
        } else if ("gcp".equals(cloudProviderName)) {
            data = Cloud.getGcpMetadata();
            if (data == null) {
                logger.warn("Cloud provider {} defined, but no metadata was found.", cloudProviderName);
            }
            return data;
        } else if ("azure".equals(cloudProviderName)) {
            data = Cloud.getAzureMetadata();
            if (data == null) {
                logger.warn("Cloud provider {} defined, but no metadata was found.", cloudProviderName);
            }
            return data;
        } else {
            data = Cloud.getAwsMetadata();
            if (data != null) {
                logger.debug("Defined aws cloud provider metadata");
                return data;
            }
            data = Cloud.getGcpMetadata();
            if (data != null) {
                logger.debug("Defined gcp cloud provider metadata");
                return data;
            }
            data = Cloud.getAzureMetadata();
            if (data != null) {
                logger.debug("Defined azure cloud provider metadata");
                return data;
            }
        }
        return null;
    }

}
