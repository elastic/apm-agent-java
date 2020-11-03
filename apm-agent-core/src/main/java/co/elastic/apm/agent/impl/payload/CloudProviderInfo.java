package co.elastic.apm.agent.impl.payload;

import co.elastic.apm.agent.impl.Cloud;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.stagemonitor.util.StringUtils;

import javax.annotation.Nullable;

public class CloudProviderInfo {
    private static final Logger logger = LoggerFactory.getLogger(CloudProviderInfo.class);

    private String provider;

    private String availabilityZone;

    private String region;

    private ProviderInstance instance;

    // aws - azure
    private ProviderAccount account;

    // gcp - azure
    private ProviderProject project;

    private ProviderMachine machine;

    public CloudProviderInfo(String provider) {
        this.provider = provider;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getAvailabilityZone() {
        return availabilityZone;
    }

    public void setAvailabilityZone(String availabilityZone) {
        this.availabilityZone = availabilityZone;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(@Nullable String region) {
        this.region = region;
    }

    public ProviderInstance getInstance() {
        return instance;
    }

    public void setInstance(ProviderInstance instance) {
        this.instance = instance;
    }

    public ProviderAccount getAccount() {
        return account;
    }

    public void setAccount(ProviderAccount account) {
        this.account = account;
    }

    public ProviderProject getProject() {
        return project;
    }

    public void setProject(ProviderProject project) {
        this.project = project;
    }

    public ProviderMachine getMachine() {
        return machine;
    }

    public void setMachine(ProviderMachine machine) {
        this.machine = machine;
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

    public static class ProviderAccount {
        private String id;

        public ProviderAccount(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }
    }

    public static class ProviderInstance {
        private String id;
        private String name;

        public ProviderInstance(@Nullable Long id, @Nullable String name) {
            this.id = id != null ? id.toString() : null;
            this.name = name;
        }
        public ProviderInstance(@Nullable String id, @Nullable String name) {
            this.id = id;
            this.name = name;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }


    public static class ProviderProject {
        private String id;
        private String name;

        public ProviderProject(@Nullable String name) {
            this.name = name;
        }

        public ProviderProject(@Nullable String name, @Nullable Long id) {
            this.name = name;
            this.id = id != null ? id.toString() : null;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    public static class ProviderMachine {
        private String type;

        public ProviderMachine(String type) {
            this.type = type;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }
    }
}
