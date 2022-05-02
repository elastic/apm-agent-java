package co.elastic.apm.agent.awssdk.v2.helper;

import co.elastic.apm.agent.awssdk.common.IAwsSdkDataSource;
import software.amazon.awssdk.auth.signer.AwsSignerExecutionAttribute;
import software.amazon.awssdk.core.SdkRequest;
import software.amazon.awssdk.core.http.ExecutionContext;
import software.amazon.awssdk.regions.Region;

import javax.annotation.Nullable;

public class SdkV2DataSource implements IAwsSdkDataSource<SdkRequest, ExecutionContext> {

    @Nullable
    private static SdkV2DataSource INSTANCE;

    public static SdkV2DataSource getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new SdkV2DataSource();
        }
        return INSTANCE;
    }

    @Override
    @Nullable
    public String getOperationName(SdkRequest sdkRequest, ExecutionContext context) {
        return context.executionAttributes().getAttribute(AwsSignerExecutionAttribute.OPERATION_NAME);
    }

    @Override
    @Nullable
    public String getRegion(SdkRequest sdkRequest, ExecutionContext context) {
        Region region = context.executionAttributes().getAttribute(AwsSignerExecutionAttribute.SIGNING_REGION);
        if (region != null) {
            return region.id();
        }

        return null;
    }

    @Override
    @Nullable
    public String getFieldValue(String fieldName, SdkRequest sdkRequest, ExecutionContext context) {
        return sdkRequest.getValueForField(fieldName, String.class).orElse(null);
    }
}
