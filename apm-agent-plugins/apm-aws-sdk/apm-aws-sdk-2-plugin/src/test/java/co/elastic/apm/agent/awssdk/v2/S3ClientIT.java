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
package co.elastic.apm.agent.awssdk.v2;

import co.elastic.apm.agent.impl.transaction.TransactionImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.localstack.LocalStackContainer;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import javax.annotation.Nullable;

import static org.assertj.core.api.Assertions.assertThat;


public class S3ClientIT extends AbstractAws2ClientIT {
    private S3Client s3;
    private S3AsyncClient s3Async;

    @BeforeEach
    public void setupClient() {
        s3 = S3Client.builder().endpointOverride(localstack.getEndpointOverride(LocalStackContainer.Service.S3))
            .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(
                localstack.getAccessKey(), localstack.getSecretKey()
            )))
            .region(Region.of(localstack.getRegion())).build();

        s3Async = S3AsyncClient.builder().endpointOverride(localstack.getEndpointOverride(LocalStackContainer.Service.S3))
            .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(
                localstack.getAccessKey(), localstack.getSecretKey()
            )))
            .region(Region.of(localstack.getRegion())).build();

        // Introduced with changes between AWS SDK 2.32.13 and 2.37.3:
        // newer AWS SDK implementation trigger some non-recycled spans, however the impact is expected to be minimal
        // as it only makes the instrumentation produce slightly more GC overhead without affecting tracing data correctness
        disableRecyclingValidation();
    }

    @Test
    public void testS3Client() {
        TransactionImpl transaction = startTestRootTransaction("s3-test");
        newTest(() -> s3.createBucket(CreateBucketRequest.builder().bucket(BUCKET_NAME).build()))
            .operationName("CreateBucket")
            .entityName(BUCKET_NAME)
            .otelAttribute("aws.s3.bucket", BUCKET_NAME)
            .execute();

        newTest(() -> s3.createBucket(CreateBucketRequest.builder().bucket(NEW_BUCKET_NAME).build()))
            .operationName("CreateBucket")
            .entityName(NEW_BUCKET_NAME)
            .otelAttribute("aws.s3.bucket", NEW_BUCKET_NAME)
            .execute();

        newTest(() -> s3.listBuckets())
            .operationName("ListBuckets")
            .execute();

        newTest(() -> s3.putObject(PutObjectRequest.builder().bucket(BUCKET_NAME).key(OBJECT_KEY).build(), RequestBody.fromString("This is some Object content")))
            .operationName("PutObject")
            .entityName(BUCKET_NAME)
            .otelAttribute("aws.s3.bucket", BUCKET_NAME)
            .otelAttribute("aws.s3.key", OBJECT_KEY)
            .execute();

        newTest(() -> s3.listObjects(ListObjectsRequest.builder().bucket(BUCKET_NAME).build()))
            .operationName("ListObjects")
            .entityName(BUCKET_NAME)
            .otelAttribute("aws.s3.bucket", BUCKET_NAME)
            .execute();

        newTest(() -> s3.getObject(GetObjectRequest.builder().bucket(BUCKET_NAME).key(OBJECT_KEY).build()))
            .operationName("GetObject")
            .entityName(BUCKET_NAME)
            .otelAttribute("aws.s3.bucket", BUCKET_NAME)
            .otelAttribute("aws.s3.key", OBJECT_KEY)
            .execute();

        newTest(() -> s3.copyObject(CopyObjectRequest.builder()
            .copySource(BUCKET_NAME + "/" + OBJECT_KEY)
            .destinationBucket(NEW_BUCKET_NAME)
            .destinationKey(NEW_OBJECT_KEY).build()))
            .operationName("CopyObject")
            .entityName(NEW_BUCKET_NAME)
            .otelAttribute("aws.s3.bucket", NEW_BUCKET_NAME)
            .otelAttribute("aws.s3.key", NEW_OBJECT_KEY)
            .otelAttribute("aws.s3.copy_source", BUCKET_NAME + "/" + OBJECT_KEY)
            .execute();

        newTest(() -> s3.deleteObject(DeleteObjectRequest.builder().bucket(BUCKET_NAME).key(OBJECT_KEY).build()))
            .operationName("DeleteObject")
            .entityName(BUCKET_NAME)
            .otelAttribute("aws.s3.bucket", BUCKET_NAME)
            .otelAttribute("aws.s3.key", OBJECT_KEY)
            .execute();

        newTest(() -> s3.deleteBucket(DeleteBucketRequest.builder().bucket(BUCKET_NAME).build()))
            .operationName("DeleteBucket")
            .entityName(BUCKET_NAME)
            .otelAttribute("aws.s3.bucket", BUCKET_NAME)
            .execute();

        assertThat(reporter.getSpans()).hasSize(9);

        transaction.deactivate().end();
    }

    @Test
    public void testAsyncS3Client() {
        TransactionImpl transaction = startTestRootTransaction("s3-test");

        newTest(() -> s3Async.createBucket(CreateBucketRequest.builder().bucket(BUCKET_NAME).build()))
            .operationName("CreateBucket")
            .entityName(BUCKET_NAME)
            .otelAttribute("aws.s3.bucket", BUCKET_NAME)
            .async()
            .execute();

        newTest(() -> s3Async.createBucket(CreateBucketRequest.builder().bucket(NEW_BUCKET_NAME).build()))
            .operationName("CreateBucket")
            .entityName(NEW_BUCKET_NAME)
            .async()
            .execute();

        newTest(() -> s3Async.listBuckets())
            .operationName("ListBuckets")
            .async()
            .execute();

        newTest(() -> s3Async.putObject(PutObjectRequest.builder()
                .bucket(BUCKET_NAME)
                .key(OBJECT_KEY).build(),
            AsyncRequestBody.fromString("This is some Object content")))
            .operationName("PutObject")
            .entityName(BUCKET_NAME)
            .otelAttribute("aws.s3.bucket", BUCKET_NAME)
            .otelAttribute("aws.s3.key", OBJECT_KEY)
            .async()
            .execute();

        newTest(() -> s3Async.listObjects(ListObjectsRequest.builder().bucket(BUCKET_NAME).build()))
            .operationName("ListObjects")
            .entityName(BUCKET_NAME)
            .async()
            .execute();

        newTest(() -> s3Async.getObject(GetObjectRequest.builder().bucket(BUCKET_NAME).key(OBJECT_KEY).build(), AsyncResponseTransformer.toBytes()))
            .operationName("GetObject")
            .entityName(BUCKET_NAME)
            .otelAttribute("aws.s3.bucket", BUCKET_NAME)
            .otelAttribute("aws.s3.key", OBJECT_KEY)
            .async()
            .execute();

        newTest(() -> s3Async.copyObject(CopyObjectRequest.builder()
            .copySource(BUCKET_NAME + "/" + OBJECT_KEY)
            .destinationBucket(NEW_BUCKET_NAME)
            .destinationKey(NEW_OBJECT_KEY).build()))
            .operationName("CopyObject")
            .entityName(NEW_BUCKET_NAME)
            .otelAttribute("aws.s3.bucket", NEW_BUCKET_NAME)
            .otelAttribute("aws.s3.key", NEW_OBJECT_KEY)
            .otelAttribute("aws.s3.copy_source", BUCKET_NAME + "/" + OBJECT_KEY)
            .async()
            .execute();

        newTest(() -> s3Async.deleteObject(DeleteObjectRequest.builder().bucket(BUCKET_NAME).key(OBJECT_KEY).build()))
            .operationName("DeleteObject")
            .entityName(BUCKET_NAME)
            .otelAttribute("aws.s3.bucket", BUCKET_NAME)
            .otelAttribute("aws.s3.key", OBJECT_KEY)
            .async()
            .execute();

        newTest(() -> s3Async.deleteBucket(DeleteBucketRequest.builder().bucket(BUCKET_NAME).build()))
            .operationName("DeleteBucket")
            .entityName(BUCKET_NAME)
            .otelAttribute("aws.s3.bucket", BUCKET_NAME)
            .async()
            .execute();

        assertThat(reporter.getSpans()).hasSize(9);

        transaction.deactivate().end();
    }

    @Override
    protected String awsService() {
        return "S3";
    }

    @Override
    protected String type() {
        return "storage";
    }

    @Override
    protected String subtype() {
        return "s3";
    }

    @Nullable
    @Override
    protected String expectedTargetName(@Nullable String entityName) {
        return entityName; //entityName is bucket name
    }

    @Override
    protected LocalStackContainer.Service localstackService() {
        return LocalStackContainer.Service.S3;
    }
}
