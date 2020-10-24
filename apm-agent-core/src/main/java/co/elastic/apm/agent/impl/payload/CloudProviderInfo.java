package co.elastic.apm.agent.impl.payload;

import co.elastic.apm.agent.impl.Cloud;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.stagemonitor.util.StringUtils;

import javax.annotation.Nullable;

public class CloudProviderInfo {
    private static final Logger logger = LoggerFactory.getLogger(CloudProviderInfo.class);

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
