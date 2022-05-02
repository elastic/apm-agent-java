package co.elastic.apm.agent.awssdk.common;

import javax.annotation.Nullable;

public interface IAwsSdkDataSource<R, C> {
    String BUCKET_NAME_FIELD = "Bucket";
    String TABLE_NAME_FIELD = "TableName";
    String KEY_CONDITION_EXPRESSION_FIELD = "KeyConditionExpression";

    String getOperationName(R sdkRequest, C context);

    @Nullable
    String getRegion(R sdkRequest, C context);

    @Nullable
    String getFieldValue(String fieldName, R sdkRequest, C context);
}
