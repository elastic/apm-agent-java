package co.elastic.apm.plugin.spi;

import javax.annotation.Nullable;

public class EmptyCloudOrigin implements CloudOrigin {

    public static final CloudOrigin INSTANCE = new EmptyCloudOrigin();

    private EmptyCloudOrigin() {
    }

    @Override
    public CloudOrigin withAccountId(@Nullable String accountId) {
        return this;
    }

    @Override
    public CloudOrigin withProvider(@Nullable String provider) {
        return this;
    }

    @Override
    public CloudOrigin withRegion(@Nullable String region) {
        return this;
    }

    @Override
    public CloudOrigin withServiceName(@Nullable String serviceName) {
        return this;
    }
}
