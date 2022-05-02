package co.elastic.apm.agent.awssdk.common;

import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Span;

import javax.annotation.Nullable;
import java.net.URI;

public abstract class AbstractDynamoDBInstrumentationHelper<R, C> {
    private static final String DYNAMO_DB_TYPE = "dynamodb";
    private final IAwsSdkDataSource<R, C> awsSdkDataSource;

    protected AbstractDynamoDBInstrumentationHelper(ElasticApmTracer tracer, IAwsSdkDataSource<R, C> awsSdkDataSource) {
        this.tracer = tracer;
        this.awsSdkDataSource = awsSdkDataSource;
    }

    private final ElasticApmTracer tracer;

    public void enrichSpan(Span span, R sdkRequest, URI httpURI, C context) {
        String operationName = awsSdkDataSource.getOperationName(sdkRequest, context);
        String region = awsSdkDataSource.getRegion(sdkRequest, context);
        String tableName = awsSdkDataSource.getFieldValue(IAwsSdkDataSource.TABLE_NAME_FIELD, sdkRequest, context);

        span.withType("db")
            .withSubtype(DYNAMO_DB_TYPE)
            .withAction("query");

        span.getContext().getDb().withInstance(region).withType(DYNAMO_DB_TYPE);


        if (operationName.equals("Query")) {
            span.getContext().getDb().withStatement(awsSdkDataSource.getFieldValue(IAwsSdkDataSource.KEY_CONDITION_EXPRESSION_FIELD, sdkRequest, context));
        }


        StringBuilder name = span.getAndOverrideName(AbstractSpan.PRIO_DEFAULT);
        if (name != null) {
            name.append("DynamoDB ").append(operationName);

            if (tableName != null) {
                name.append(" ").append(tableName);
            }
        }

        span.getContext()
            .getDestination()
            .getService()
            .withResource(DYNAMO_DB_TYPE);

        span.getContext().getDestination()
            .withAddress(httpURI.getHost())
            .withPort(httpURI.getPort());
    }

    @Nullable
    public Span startSpan(R sdkRequest, URI httpURI, C context) {
        Span span = tracer.createExitChildSpan();
        if (span == null) {
            return null;
        }

        enrichSpan(span, sdkRequest, httpURI, context);


        return span;
    }
}
