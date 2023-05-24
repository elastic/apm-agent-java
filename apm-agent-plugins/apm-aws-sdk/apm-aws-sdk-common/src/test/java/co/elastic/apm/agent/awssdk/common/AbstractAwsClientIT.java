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
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.tracer.Outcome;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static co.elastic.apm.agent.testutils.assertions.Assertions.assertThat;
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
    protected static final String NEW_OBJECT_KEY = "new-key";
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

    public TestBuilder newTest(Supplier<?> test) {
        return new TestBuilder(test);
    }

    public class TestBuilder {

        private final Supplier<?> test;
        @Nullable
        private String action;

        @Nullable
        private String entityName;
        private String operationName = "unknown";

        private final Map<String, Object> otelAttributes;

        @Nullable
        private Consumer<Span> spanAssertions;
        private boolean asyncSpans;

        private TestBuilder(Supplier<?> test) {
            this.test = test;
            this.otelAttributes = new HashMap<>();
        }

        public TestBuilder entityName(@Nullable String entityName) {
            this.entityName = entityName;
            return this;
        }

        public TestBuilder operationName(String operationName) {
            this.operationName = operationName;
            return this;
        }

        public TestBuilder action(String action) {
            this.action = action;
            return this;
        }

        public TestBuilder otelAttribute(String key, Object value) {
            otelAttributes.put(key, value);
            return this;
        }

        public TestBuilder async() {
            this.asyncSpans = true;
            return this;
        }

        public TestBuilder withSpanAssertions(Consumer<Span> assertions) {
            this.spanAssertions = assertions;
            return this;
        }

        public void execute() {
            doExecute();

            Span span = getSpan();
            commonSpanAssertions(span);

            assertThat(span).hasOutcome(Outcome.SUCCESS);
        }

        public void executeWithException(Class<? extends Exception> exceptionType) {
            assertThatExceptionOfType(exceptionType)
                .isThrownBy(this::doExecute);

            Span span = getSpan();
            commonSpanAssertions(span);

            assertThat(span).hasOutcome(Outcome.FAILURE);
        }

        private void doExecute() {
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
        }

        private Span getSpan() {
            String spanName = awsService() + " " + operationName + (entityName != null ? " " + entityName : "");

            Span span = reporter.getSpanByName(spanName);
            assertThat(span)
                .describedAs("span with name '%s' is expected", spanName)
                .isNotNull();

            return span;
        }

        private void commonSpanAssertions(Span span) {

            assertThat(span)
                .isExit()
                .hasType(type())
                .hasSubType(subtype());

            if (action != null) {
                assertThat(span).hasAction(action);
            }

            ServiceTarget serviceTarget = span.getContext().getServiceTarget();

            String targetName = expectedTargetName(entityName);

            if (targetName == null) {
                assertThat(serviceTarget)
                    .hasType(subtype())
                    .hasNoName()
                    .hasDestinationResource(subtype());
            } else {
                assertThat(serviceTarget)
                    .hasType(subtype())
                    .hasName(targetName)
                    .hasDestinationResource(subtype() + "/" + targetName);
            }
            assertThat(span.getContext().getDestination())
                .hasAddress(localstack.getEndpointOverride(LocalStackContainer.Service.S3).getHost());

            for (Map.Entry<String, Object> entry : otelAttributes.entrySet()) {
                assertThat(span).hasOtelAttribute(entry.getKey(), entry.getValue());
            }

            if (asyncSpans) {
                assertThat(span.isSync())
                    .describedAs("expected asynchronous span")
                    .isFalse();
            } else {
                assertThat(span.isSync())
                    .describedAs("expected synchronous span")
                    .isTrue();
            }

            if (spanAssertions != null) {
                spanAssertions.accept(span);
            }

        }
    }

}
