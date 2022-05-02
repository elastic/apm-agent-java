package co.elastic.apm.agent.awssdk.v2.helper;

import co.elastic.apm.agent.awssdk.common.AbstractDynamoDBInstrumentationHelper;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.GlobalTracer;
import software.amazon.awssdk.core.SdkRequest;
import software.amazon.awssdk.core.http.ExecutionContext;

import javax.annotation.Nullable;

public class DynamoDbHelper extends AbstractDynamoDBInstrumentationHelper<SdkRequest, ExecutionContext> {

    @Nullable
    private static DynamoDbHelper INSTANCE;

    public static DynamoDbHelper getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new DynamoDbHelper(GlobalTracer.requireTracerImpl());
        }
        return INSTANCE;
    }

    public DynamoDbHelper(ElasticApmTracer tracer) {
        super(tracer, SdkV2DataSource.getInstance());
    }

}
