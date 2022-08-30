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
package co.elastic.apm.agent.awssdk.v1;

import co.elastic.apm.agent.awssdk.common.AbstractAwsClientIT;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.Transaction;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.AmazonS3Exception;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.localstack.LocalStackContainer;

import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.testcontainers.containers.localstack.LocalStackContainer.Service.S3;


public class S3ClientIT extends AbstractAwsClientIT {

    private AmazonS3 s3;

    private final Consumer<Span> dbAssert = span -> assertThat(span.getContext().getDb().getInstance()).isEqualTo(localstack.getRegion());

    @BeforeEach
    public void setupClient() {
        s3 = AmazonS3Client.builder()
            .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(localstack.getEndpointOverride(LocalStackContainer.Service.S3).toString(), localstack.getRegion()))
            .withCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials(localstack.getAccessKey(), localstack.getSecretKey())))
            .build();
    }

    @Test
    public void testS3Client() {
        Transaction transaction = startTestRootTransaction("s3-test");

        executeTest("CreateBucket", BUCKET_NAME, () -> s3.createBucket(BUCKET_NAME), dbAssert);
        executeTest("CreateBucket", NEW_BUCKET_NAME, () -> s3.createBucket(NEW_BUCKET_NAME), dbAssert);
        executeTest("ListBuckets", null, () -> s3.listBuckets(), dbAssert);
        executeTest("PutObject", BUCKET_NAME, () -> s3.putObject(BUCKET_NAME, OBJECT_KEY, "This is some Object content"), dbAssert);
        executeTest("ListObjects", BUCKET_NAME, () -> s3.listObjects(BUCKET_NAME), dbAssert);
        executeTest("GetObject", BUCKET_NAME, () -> s3.getObject(BUCKET_NAME, OBJECT_KEY), dbAssert);
        executeTest("CopyObject", NEW_BUCKET_NAME, () -> s3.copyObject(BUCKET_NAME, OBJECT_KEY, NEW_BUCKET_NAME, "new-key"), dbAssert);
        executeTest("DeleteObject", BUCKET_NAME, () -> {
            s3.deleteObject(BUCKET_NAME, OBJECT_KEY);
            return null;
        });
        executeTest("DeleteBucket", BUCKET_NAME, () -> {
            s3.deleteBucket(BUCKET_NAME);
            return null;
        }, dbAssert);
        executeTestWithException(AmazonS3Exception.class, "PutObject", BUCKET_NAME + "-exception", () -> s3.putObject(BUCKET_NAME + "-exception", OBJECT_KEY, "This is some Object content"), dbAssert);

        assertThat(reporter.getSpans().size()).isEqualTo(10);
        assertThat(reporter.getSpans()).allMatch(AbstractSpan::isSync);
        transaction.deactivate().end();

        assertThat(reporter.getNumReportedErrors()).isEqualTo(1);
        assertThat(reporter.getFirstError().getException()).isInstanceOf(AmazonS3Exception.class);
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
        return S3;
    }
}
