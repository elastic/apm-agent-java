package co.elastic.apm.agent.awssdk.v1.helper;

import co.elastic.apm.agent.awssdk.common.IAwsSdkDataSource;
import com.amazonaws.Request;
import com.amazonaws.handlers.HandlerContextKey;
import com.amazonaws.http.ExecutionContext;
import com.amazonaws.services.dynamodbv2.model.QueryRequest;

import javax.annotation.Nullable;

public class SdkV1DataSource implements IAwsSdkDataSource<Request<?>, ExecutionContext> {
    @Nullable
    private static SdkV1DataSource INSTANCE;

    public static SdkV1DataSource getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new SdkV1DataSource();
        }
        return INSTANCE;
    }

    @Override
    @Nullable
    public String getOperationName(Request<?> request, ExecutionContext context) {
        return request.getHandlerContext(HandlerContextKey.OPERATION_NAME);
    }

    @Override
    @Nullable
    public String getRegion(Request<?> request, ExecutionContext context) {
        return request.getHandlerContext(HandlerContextKey.SIGNING_REGION);
    }

    @Override
    @Nullable
    public String getFieldValue(String fieldName, Request<?> request, ExecutionContext context) {
        if (IAwsSdkDataSource.BUCKET_NAME_FIELD.equals(fieldName)) {
            String resourcePath = request.getResourcePath();

            if (resourcePath == null || resourcePath.isEmpty()) {
                return null;
            }

            if (resourcePath.startsWith("/")) {
                resourcePath = resourcePath.substring(1);
            }
            int idx = resourcePath.indexOf('/');
            return idx < 0 ? resourcePath : resourcePath.substring(0, idx);
        } else if (IAwsSdkDataSource.TABLE_NAME_FIELD.equals(fieldName)) {
            return DynamoDbHelper.getInstance().getTableName(request.getOriginalRequest());
        } else if (IAwsSdkDataSource.KEY_CONDITION_EXPRESSION_FIELD.equals(fieldName)
            && request.getOriginalRequest() instanceof QueryRequest) {
            return ((QueryRequest) request.getOriginalRequest()).getKeyConditionExpression();
        }

        return null;
    }
}
