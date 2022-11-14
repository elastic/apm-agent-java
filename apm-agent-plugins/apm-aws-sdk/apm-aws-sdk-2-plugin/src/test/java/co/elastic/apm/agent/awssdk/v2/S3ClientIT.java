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

import co.elastic.apm.agent.awssdk.common.AbstractAwsClientIT;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Transaction;
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

import static org.assertj.core.api.Assertions.assertThat;


public class S3ClientIT extends AbstractAwsClientIT {
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
    }

    @Test
    public void testS3Client() {
        Transaction transaction = startTestRootTransaction("s3-test");
        executeTest("CreateBucket", BUCKET_NAME, () -> s3.createBucket(CreateBucketRequest.builder().bucket(BUCKET_NAME).build()));
        executeTest("CreateBucket", NEW_BUCKET_NAME, () -> s3.createBucket(CreateBucketRequest.builder().bucket(NEW_BUCKET_NAME).build()));
        executeTest("ListBuckets", null, () -> s3.listBuckets());
        executeTest("PutObject", BUCKET_NAME, () -> s3.putObject(PutObjectRequest.builder().bucket(BUCKET_NAME).key(OBJECT_KEY).build(), RequestBody.fromString("This is some Object content")));
        executeTest("ListObjects", BUCKET_NAME, () -> s3.listObjects(ListObjectsRequest.builder().bucket(BUCKET_NAME).build()));
        executeTest("GetObject", BUCKET_NAME, () -> s3.getObject(GetObjectRequest.builder().bucket(BUCKET_NAME).key(OBJECT_KEY).build()));
        executeTest("CopyObject", NEW_BUCKET_NAME, () -> s3.copyObject(CopyObjectRequest.builder()
            .copySource(BUCKET_NAME + "/" + OBJECT_KEY)
            .destinationBucket(NEW_BUCKET_NAME)
            .destinationKey("new-key").build()));
        executeTest("DeleteObject", BUCKET_NAME, () -> s3.deleteObject(DeleteObjectRequest.builder().bucket(BUCKET_NAME).key(OBJECT_KEY).build()));
        executeTest("DeleteBucket", BUCKET_NAME, () -> s3.deleteBucket(DeleteBucketRequest.builder().bucket(BUCKET_NAME).build()));
        assertThat(reporter.getSpans().size()).isEqualTo(9);
        assertThat(reporter.getSpans()).allMatch(AbstractSpan::isSync);
        transaction.deactivate().end();
    }

    @Test
    public void testAsyncS3Client() {
        Transaction transaction = startTestRootTransaction("s3-test");
        executeTest("CreateBucket", BUCKET_NAME, () -> s3Async.createBucket(CreateBucketRequest.builder().bucket(BUCKET_NAME).build()));
        executeTest("CreateBucket", NEW_BUCKET_NAME, () -> s3Async.createBucket(CreateBucketRequest.builder().bucket(NEW_BUCKET_NAME).build()));
        executeTest("ListBuckets", null, () -> s3Async.listBuckets());
        executeTest("PutObject", BUCKET_NAME, () -> s3Async.putObject(PutObjectRequest.builder().bucket(BUCKET_NAME)
                .key(OBJECT_KEY).build(),
            AsyncRequestBody.fromString("This is some Object content")));
        executeTest("ListObjects", BUCKET_NAME, () -> s3Async.listObjects(ListObjectsRequest.builder().bucket(BUCKET_NAME).build()));
        executeTest("GetObject", BUCKET_NAME, () -> s3Async.getObject(GetObjectRequest.builder().bucket(BUCKET_NAME).key(OBJECT_KEY).build(), AsyncResponseTransformer.toBytes()));
        executeTest("CopyObject", NEW_BUCKET_NAME, () -> s3Async.copyObject(CopyObjectRequest.builder()
                .copySource(BUCKET_NAME + "/" + OBJECT_KEY)
                .destinationBucket(NEW_BUCKET_NAME)
                .destinationKey("new-key").build()));
        executeTest("DeleteObject", BUCKET_NAME, () -> s3Async.deleteObject(DeleteObjectRequest.builder().bucket(BUCKET_NAME).key(OBJECT_KEY).build()));
        executeTest("DeleteBucket", BUCKET_NAME, () -> s3Async.deleteBucket(DeleteBucketRequest.builder().bucket(BUCKET_NAME).build()));
        assertThat(reporter.getSpans().size()).isEqualTo(9);
        assertThat(reporter.getSpans()).allMatch(span -> !span.isSync());
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
    protected LocalStackContainer.Service localstackService() {
        return LocalStackContainer.Service.S3;
    }
}
