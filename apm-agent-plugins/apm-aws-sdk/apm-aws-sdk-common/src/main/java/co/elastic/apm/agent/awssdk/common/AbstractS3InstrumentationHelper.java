package co.elastic.apm.agent.awssdk.common;

import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Span;

import javax.annotation.Nullable;
import java.net.URI;
import java.util.Objects;

public abstract class AbstractS3InstrumentationHelper<R, C> {
    private static final String S3_TYPE = "s3";
    private final IAwsSdkDataSource<R, C> awsSdkDataSource;

    protected AbstractS3InstrumentationHelper(ElasticApmTracer tracer, IAwsSdkDataSource<R, C> awsSdkDataSource) {
        this.tracer = tracer;
        this.awsSdkDataSource = awsSdkDataSource;
    }

    private final ElasticApmTracer tracer;

    @Nullable
    public Span startSpan(R request, URI httpURI, C context) {
        Span span = tracer.createExitChildSpan();
        if (span == null) {
            return null;
        }
        String operationName = awsSdkDataSource.getOperationName(request, context);
        String region = awsSdkDataSource.getRegion(request, context);
        String bucketName = awsSdkDataSource.getFieldValue(IAwsSdkDataSource.BUCKET_NAME_FIELD, request, context);

        span.withType("storage")
            .withSubtype(S3_TYPE)
            .withAction(operationName);
        span.getContext().getDb().withInstance(region).withType(S3_TYPE);
        StringBuilder name = span.getAndOverrideName(AbstractSpan.PRIO_DEFAULT);
        if (operationName != null && name != null) {
            name.append("S3 ").append(operationName);
            if (bucketName != null && !bucketName.isEmpty()) {
                name.append(" ").append(bucketName);
            }
        }
        span.withName("S3", AbstractSpan.PRIO_DEFAULT - 1);

        span.getContext()
            .getDestination()
            .getService()
            .withResource(Objects.requireNonNullElse(bucketName, S3_TYPE));

        span.getContext().getDestination()
            .withAddress(httpURI.getHost())
            .withPort(httpURI.getPort());
        return span;
    }
}
