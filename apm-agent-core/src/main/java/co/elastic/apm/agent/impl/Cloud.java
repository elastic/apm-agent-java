package co.elastic.apm.agent.impl;

import co.elastic.apm.agent.impl.payload.CloudProviderInfo;

import javax.annotation.Nullable;

public class Cloud {

    @Nullable
    public static CloudProviderInfo getAwsMetadata() {

        return null;
    }

    @Nullable
    public static CloudProviderInfo getGcpMetadata() {

        return null;
    }

    @Nullable
    public static CloudProviderInfo getAzureMetadata() {

        return null;
    }
}
