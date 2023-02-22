package co.elastic.apm.plugin.spi;

import javax.annotation.Nullable;

public interface CloudOrigin {

    CloudOrigin withAccountId(@Nullable String accountId);

    CloudOrigin withProvider(@Nullable String provider);

    CloudOrigin withRegion(@Nullable String region);

    CloudOrigin withServiceName(@Nullable String serviceName);
}
