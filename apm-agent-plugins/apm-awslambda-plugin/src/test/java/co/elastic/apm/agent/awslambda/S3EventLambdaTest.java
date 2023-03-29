/*
 * Licensed to Elasticsearch B.V. under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch B.V. licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package co.elastic.apm.agent.awslambda;

import co.elastic.apm.agent.awslambda.lambdas.AbstractFunction;
import co.elastic.apm.agent.awslambda.lambdas.S3EventLambdaFunction;
import co.elastic.apm.agent.awslambda.lambdas.TestContext;
import co.elastic.apm.agent.impl.transaction.Faas;
import co.elastic.apm.agent.tracer.Outcome;
import co.elastic.apm.agent.impl.transaction.Transaction;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.amazonaws.services.lambda.runtime.events.models.s3.S3EventNotification;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

public class S3EventLambdaTest extends AbstractLambdaTest<S3Event, Void> {

    @BeforeAll
    // Need to overwrite the beforeAll() method from parent,
    // because we need to mock serverlessConfiguration BEFORE instrumentation is initialized!
    public static synchronized void beforeAll() {
        AbstractLambdaTest.initAllButInstrumentation();
        doReturn(S3EventLambdaFunction.class.getName()).when(Objects.requireNonNull(serverlessConfiguration)).getAwsLambdaHandler();
        AbstractLambdaTest.initInstrumentation();
    }

    @Override
    protected AbstractFunction<S3Event, Void> createHandler() {
        return new S3EventLambdaFunction();
    }

    @Override
    protected S3Event createInput() {
        return new S3Event((List.of(createS3NotificationRecord())));
    }

    @Override
    protected boolean supportsContextPropagation() {
        return false;
    }

    @Nonnull
    private S3EventNotification.S3EventNotificationRecord createS3NotificationRecord() {
        S3EventNotification.ResponseElementsEntity responseElements = new S3EventNotification.ResponseElementsEntity("xAmzId2", S3_REQUEST_ID);
        S3EventNotification.S3BucketEntity bucket = new S3EventNotification.S3BucketEntity(S3_BUCKET_NAME, null, S3_BUCKET_ARN);
        S3EventNotification.S3Entity s3 = new S3EventNotification.S3Entity("configId", bucket, null, "3.3");
        return new S3EventNotification.S3EventNotificationRecord(EVENT_SOURCE_REGION, S3_EVENT_NAME, S3_EVENT_SOURCE, null,
            S3_EVENT_VERSION, null, responseElements, s3, null);
    }


    @Test
    public void testBasicCall() {
        getFunction().handleRequest(createInput(), context);
        reporter.awaitTransactionCount(1);
        reporter.awaitSpanCount(1);
        assertThat(reporter.getFirstSpan().getNameAsString()).isEqualTo("child-span");
        assertThat(reporter.getFirstSpan().getTransaction()).isEqualTo(reporter.getFirstTransaction());
        Transaction transaction = reporter.getFirstTransaction();
        assertThat(transaction.getNameAsString()).isEqualTo(S3_EVENT_NAME + " " + S3_BUCKET_NAME);
        assertThat(transaction.getType()).isEqualTo("messaging");
        assertThat(transaction.getResult()).isEqualTo("success");
        assertThat(transaction.getOutcome()).isEqualTo(Outcome.SUCCESS);

        assertThat(transaction.getContext().getServiceOrigin().hasContent()).isTrue();
        assertThat(transaction.getContext().getServiceOrigin().getName().toString()).isEqualTo(S3_BUCKET_NAME);
        assertThat(transaction.getContext().getServiceOrigin().getId()).isEqualTo(S3_BUCKET_ARN);
        assertThat(transaction.getContext().getServiceOrigin().getVersion()).isEqualTo(S3_EVENT_VERSION);

        assertThat(transaction.getContext().getCloudOrigin()).isNotNull();
        assertThat(transaction.getContext().getCloudOrigin().getProvider()).isEqualTo("aws");
        assertThat(transaction.getContext().getCloudOrigin().getServiceName()).isEqualTo("s3");
        assertThat(transaction.getContext().getCloudOrigin().getRegion()).isEqualTo(EVENT_SOURCE_REGION);
        assertThat(transaction.getContext().getCloudOrigin().getAccountId()).isNull();

        Faas faas = transaction.getFaas();
        assertThat(faas.getExecution()).isEqualTo(TestContext.AWS_REQUEST_ID);
        assertThat(faas.getId()).isEqualTo(TestContext.FUNCTION_ARN);
        assertThat(faas.getTrigger().getType()).isEqualTo("datasource");
        assertThat(faas.getTrigger().getRequestId()).isEqualTo(S3_REQUEST_ID);
    }

    @Test
    public void testCallWithNullInput() {
        getFunction().handleRequest(null, context);

        reporter.awaitTransactionCount(1);
        reporter.awaitSpanCount(1);
        assertThat(reporter.getFirstSpan().getNameAsString()).isEqualTo("child-span");
        assertThat(reporter.getFirstSpan().getTransaction()).isEqualTo(reporter.getFirstTransaction());
        Transaction transaction = reporter.getFirstTransaction();
        assertThat(transaction.getNameAsString()).isEqualTo(TestContext.FUNCTION_NAME);
        assertThat(transaction.getType()).isEqualTo("request");
        assertThat(transaction.getResult()).isEqualTo("success");
        assertThat(transaction.getOutcome()).isEqualTo(Outcome.SUCCESS);

        assertThat(transaction.getContext().getCloudOrigin()).isNotNull();
        assertThat(transaction.getContext().getCloudOrigin().getProvider()).isEqualTo("aws");
        assertThat(transaction.getContext().getCloudOrigin().getServiceName()).isNull();
        assertThat(transaction.getContext().getCloudOrigin().getRegion()).isNull();
        assertThat(transaction.getContext().getCloudOrigin().getAccountId()).isNull();

        assertThat(transaction.getContext().getServiceOrigin().hasContent()).isFalse();

        Faas faas = transaction.getFaas();
        assertThat(faas.getExecution()).isEqualTo(TestContext.AWS_REQUEST_ID);

        assertThat(faas.getTrigger().getType()).isEqualTo("other");
        assertThat(faas.getTrigger().getRequestId()).isNull();
    }

    @Test
    public void testCallWithMultipleRecordsPerEvent() {
        S3Event event = new S3Event(List.of(createS3NotificationRecord(), createS3NotificationRecord()));
        getFunction().handleRequest(event, context);

        validateResultsForUnspecifiedRecord();
    }

    @Test
    public void testCallWithEmptyRecord() {
        S3EventNotification.S3EventNotificationRecord record = new S3EventNotification.S3EventNotificationRecord(null, null,
            null, null, null, null, null, null, null);
        getFunction().handleRequest(new S3Event(List.of(record)), context);
        validateResultsForUnspecifiedRecord();
    }

    private void validateResultsForUnspecifiedRecord() {
        reporter.awaitTransactionCount(1);
        reporter.awaitSpanCount(1);
        assertThat(reporter.getFirstSpan().getNameAsString()).isEqualTo("child-span");
        assertThat(reporter.getFirstSpan().getTransaction()).isEqualTo(reporter.getFirstTransaction());
        Transaction transaction = reporter.getFirstTransaction();
        assertThat(transaction.getNameAsString()).isEqualTo(TestContext.FUNCTION_NAME);
        assertThat(transaction.getType()).isEqualTo("messaging");
        assertThat(transaction.getResult()).isEqualTo("success");
        assertThat(transaction.getOutcome()).isEqualTo(Outcome.SUCCESS);

        assertThat(transaction.getContext().getCloudOrigin()).isNotNull();
        assertThat(transaction.getContext().getCloudOrigin().getProvider()).isEqualTo("aws");
        assertThat(transaction.getContext().getCloudOrigin().getServiceName()).isEqualTo("s3");
        assertThat(transaction.getContext().getCloudOrigin().getRegion()).isNull();
        assertThat(transaction.getContext().getCloudOrigin().getAccountId()).isNull();

        assertThat(transaction.getContext().getServiceOrigin().hasContent()).isFalse();

        Faas faas = transaction.getFaas();
        assertThat(faas.getExecution()).isEqualTo(TestContext.AWS_REQUEST_ID);

        assertThat(faas.getTrigger().getType()).isEqualTo("datasource");
        assertThat(faas.getTrigger().getRequestId()).isNull();
    }
}
