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
package co.elastic.apm.agent.awssdk.common;

import co.elastic.apm.agent.AbstractInstrumentationTest;
import co.elastic.apm.agent.impl.context.ServiceTarget;
import co.elastic.apm.agent.tracer.Outcome;
import co.elastic.apm.agent.impl.transaction.Span;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import javax.annotation.Nullable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.fail;

@Testcontainers
public abstract class AbstractAwsClientIT extends AbstractInstrumentationTest {
    private static final DockerImageName localstackImage = DockerImageName.parse("localstack/localstack:0.14.2");
    protected static final String BUCKET_NAME = "some-test-bucket";
    protected static final String SQS_QUEUE_NAME = "some-test-sqs-queue";
    protected static final String SQS_IGNORED_QUEUE_NAME = "ignored-queue";
    protected static final String MESSAGE_BODY = "some-test-sqs-message-body";
    protected static final String NEW_BUCKET_NAME = "new-test-bucket";
    protected static final String OBJECT_KEY = "some-object-key";
    protected static final String TABLE_NAME = "some-test-table";
    protected static final String KEY_CONDITION_EXPRESSION = "attributeOne = :one";

    @Container
    protected LocalStackContainer localstack = new LocalStackContainer(localstackImage).withServices(localstackService());

    protected abstract String awsService();

    protected abstract String type();

    protected abstract String subtype();

    @Nullable
    protected abstract String expectedTargetName(@Nullable String entityName);

    protected abstract LocalStackContainer.Service localstackService();

    protected void executeTest(String operationName, @Nullable String entityName, Supplier<?> test) {
        executeTest(operationName, operationName, entityName, test, null);
    }

    protected void executeTest(String operationName, @Nullable String entityName, Supplier<?> test, @Nullable Consumer<Span> assertions) {
        executeTest(operationName, operationName, entityName, test, assertions);
    }

    protected void executeTest(String operationName, String action, @Nullable String entityName, Supplier<?> test) {
        executeTest(operationName, action, entityName, test, null);
    }

    protected void executeTest(String operationName, String action, @Nullable String entityName, Supplier<?> test, @Nullable Consumer<Span> assertions) {
        Object result = test.get();
        if (result instanceof CompletableFuture) {
            ((CompletableFuture<?>) result).join();
        } else if (result instanceof Future) {
            // waiting max 10s for the future to complete
            try {
                ((Future<?>) result).get(10, TimeUnit.SECONDS);
            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                fail(e.getMessage());
            }

        }
        String spanName = awsService() + " " + operationName + (entityName != null ? " " + entityName : "");

        Span span = reporter.getSpanByName(spanName);
        assertThat(span).isNotNull();
        assertThat(span.getType()).isEqualTo(type());
        assertThat(span.getSubtype()).isEqualTo(localstackService().getLocalStackName());
        assertThat(span.getAction()).isEqualTo(action);
        ServiceTarget serviceTarget = span.getContext().getServiceTarget();
        assertThat(serviceTarget.getType()).isEqualTo(subtype());
        String expectedTargetName = expectedTargetName(entityName);
        if (expectedTargetName == null) {
            assertThat(serviceTarget.getName()).isNull();
            assertThat(serviceTarget.getDestinationResource().toString()).isEqualTo(subtype());
        } else {
            assertThat(serviceTarget.getName()).isNotNull();
            assertThat(serviceTarget.getName().toString()).isEqualTo(expectedTargetName);
            assertThat(serviceTarget.getDestinationResource().toString()).isEqualTo(subtype() + "/" + expectedTargetName);
        }
        assertThat(span.getContext().getDestination().getAddress().toString())
            .isEqualTo(localstack.getEndpointOverride(LocalStackContainer.Service.S3).getHost());
        if (assertions != null) {
            assertions.accept(span);
        }
    }

    protected void executeTestWithException(Class<? extends Exception> exceptionType, String operationName, String entityName, Supplier<?> test) {
        executeTestWithException(exceptionType, operationName, entityName, test, null);
    }

    protected void executeTestWithException(Class<? extends Exception> exceptionType, String operationName, String entityName, Supplier<?> test, @Nullable Consumer<Span> assertions) {
        executeTestWithException(exceptionType, operationName, operationName, entityName, test, assertions);
    }

    protected void executeTestWithException(Class<? extends Exception> exceptionType, String operationName, String action, @Nullable String entityName, Supplier<?> test, @Nullable Consumer<Span> assertions) {
        assertThatExceptionOfType(exceptionType).isThrownBy(() -> executeTest(operationName, action, entityName, test));

        String spanName = awsService() + " " + operationName + (entityName != null ? " " + entityName : "");

        Span span = reporter.getSpanByName(spanName);
        assertThat(span.getOutcome()).isEqualTo(Outcome.FAILURE);
        if (assertions != null) {
            assertions.accept(span);
        }
    }

}
