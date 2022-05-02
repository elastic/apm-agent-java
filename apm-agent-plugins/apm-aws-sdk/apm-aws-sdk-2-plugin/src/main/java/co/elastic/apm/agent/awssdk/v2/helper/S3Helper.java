package co.elastic.apm.agent.awssdk.v2.helper;

import co.elastic.apm.agent.awssdk.common.AbstractS3InstrumentationHelper;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.GlobalTracer;
import software.amazon.awssdk.core.SdkRequest;
import software.amazon.awssdk.core.http.ExecutionContext;

import javax.annotation.Nullable;

public class S3Helper extends AbstractS3InstrumentationHelper<SdkRequest, ExecutionContext> {

    @Nullable
    private static S3Helper INSTANCE;

    public static S3Helper getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new S3Helper(GlobalTracer.requireTracerImpl());
        }
        return INSTANCE;
    }

    public S3Helper(ElasticApmTracer tracer) {
        super(tracer, SdkV2DataSource.getInstance());
    }
}
