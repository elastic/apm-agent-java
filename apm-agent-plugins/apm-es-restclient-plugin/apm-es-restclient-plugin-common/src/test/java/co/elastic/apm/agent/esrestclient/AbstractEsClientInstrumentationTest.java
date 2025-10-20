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
package co.elastic.apm.agent.esrestclient;

import co.elastic.apm.agent.AbstractInstrumentationTest;
import co.elastic.apm.agent.impl.context.DbImpl;
import co.elastic.apm.agent.impl.context.DestinationImpl;
import co.elastic.apm.agent.impl.context.HttpImpl;
import co.elastic.apm.agent.impl.error.ErrorCaptureImpl;
import co.elastic.apm.agent.impl.transaction.SpanImpl;
import co.elastic.apm.agent.impl.transaction.TransactionImpl;
import co.elastic.apm.agent.testutils.TestContainersUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.runners.Parameterized;
import org.testcontainers.elasticsearch.ElasticsearchContainer;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static co.elastic.apm.agent.esrestclient.ElasticsearchRestClientInstrumentationHelper.ELASTICSEARCH;
import static co.elastic.apm.agent.esrestclient.ElasticsearchRestClientInstrumentationHelper.SPAN_ACTION;
import static co.elastic.apm.agent.esrestclient.ElasticsearchRestClientInstrumentationHelper.SPAN_TYPE;
import static co.elastic.apm.agent.testutils.assertions.Assertions.assertThat;

public abstract class AbstractEsClientInstrumentationTest extends AbstractInstrumentationTest {

    @Nullable
    protected static ElasticsearchContainer container;

    protected static final String INDEX = "my-index";
    protected static final String SECOND_INDEX = "my-second-index";
    protected static final String DOC_ID = "38uhjds8s4g";
    protected static final String DOC_TYPE = "_doc";
    protected static final String FOO = "foo";
    protected static final String BAR = "bar";
    protected static final String BAZ = "baz";

    protected boolean async;

    @Parameterized.Parameters(name = "Async={0}")
    public static Iterable<Object[]> data() {
        return Arrays.asList(new Object[][]{{Boolean.FALSE}, {Boolean.TRUE}});
    }

    protected static void startContainer(String image) {
        container = new ElasticsearchContainer(image)
            .withEnv("ES_JAVA_OPTS", "-XX:-UseContainerSupport")
            .withCreateContainerCmdModifier(TestContainersUtils.withMemoryLimit(4096));
        container.start();
    }

    @AfterClass
    public static void stopContainer() {
        if (container != null) {
            container.stop();
        }
    }

    @Before
    public void startTransaction() {
        startTestRootTransaction("ES Transaction");
    }

    @After
    public void endTransaction() {
        TransactionImpl currentTransaction = tracer.currentTransaction();
        if (currentTransaction != null) {
            currentTransaction.deactivate().end();
        }
    }

    public void assertThatErrorsExistWhenDeleteNonExistingIndex() {
        List<ErrorCaptureImpl> errorCaptures = reporter.getErrors();
        assertThat(errorCaptures).hasSize(1);
        ErrorCaptureImpl errorCapture = errorCaptures.get(0);
        assertThat(errorCapture.getException()).isNotNull();
    }

    protected EsSpanValidationBuilder validateSpan(SpanImpl spanToValidate) {
        return new EsSpanValidationBuilder(spanToValidate, async);
    }

    protected EsSpanValidationBuilder validateSpan() {
        List<SpanImpl> spans = reporter.getSpans();
        assertThat(spans).hasSize(1);
        return validateSpan(spans.get(0));
    }

    protected static class EsSpanValidationBuilder {

        private static final ObjectMapper jackson = new ObjectMapper();

        private final SpanImpl span;

        private boolean statementExpectedNonNull = false;

        @Nullable
        private JsonNode expectedStatement;

        @Nullable
        private Map<String, String> expectedPathParts;

        private int expectedStatusCode = 200;

        @Nullable
        private String expectedHttpMethod;

        @Nullable
        private String expectedNameEndpoint;

        @Nullable
        private String expectedNamePath;

        @Nullable
        private String expectedHttpUrl = "http://" + container.getHttpHostAddress();

        private boolean isAsyncRequest;

        public EsSpanValidationBuilder(SpanImpl spanToValidate, boolean isAsyncRequest) {
            this.span = spanToValidate;
            this.isAsyncRequest = isAsyncRequest;
        }

        public EsSpanValidationBuilder expectNoStatement() {
            statementExpectedNonNull = false;
            expectedStatement = null;
            return this;
        }

        public EsSpanValidationBuilder expectAnyStatement() {
            statementExpectedNonNull = true;
            expectedStatement = null;
            return this;
        }

        public EsSpanValidationBuilder expectAsync(boolean async) {
            isAsyncRequest = async;
            return this;
        }

        public EsSpanValidationBuilder expectStatement(String statement) {
            try {
                this.expectedStatement = jackson.readTree(statement);
                statementExpectedNonNull = true;
            } catch (JsonProcessingException e) {
                throw new IllegalArgumentException(e);
            }
            return this;
        }

        public EsSpanValidationBuilder expectPathPart(String key, String value) {
            if (expectedPathParts == null) {
                expectedPathParts = new HashMap<>();
            }
            expectedPathParts.put(key, value);
            return this;
        }

        public EsSpanValidationBuilder expectNoPathParts() {
            expectedPathParts = new HashMap<>();
            return this;
        }

        public EsSpanValidationBuilder statusCode(int expectedStatusCode) {
            this.expectedStatusCode = expectedStatusCode;
            return this;
        }

        public EsSpanValidationBuilder method(String httpMethod) {
            this.expectedHttpMethod = httpMethod;
            return this;
        }

        public EsSpanValidationBuilder disableHttpUrlCheck() {
            expectedHttpUrl = null;
            return this;
        }

        public EsSpanValidationBuilder endpointName(String endpoint) {
            this.expectedNameEndpoint = endpoint;
            this.expectedNamePath = null;
            return this;
        }

        public EsSpanValidationBuilder pathName(String pathFormat, Object... args) {
            this.expectedNameEndpoint = null;
            this.expectedNamePath = String.format(pathFormat, args);
            return this;
        }

        public void check() {
            assertThat(span)
                .hasType(SPAN_TYPE)
                .hasSubType(ELASTICSEARCH)
                .hasAction(SPAN_ACTION);

            if (expectedNameEndpoint != null) {
                assertThat(span.getOtelAttributes()).containsEntry("db.operation", expectedNameEndpoint);
                assertThat(span).hasName("Elasticsearch: " + expectedNameEndpoint);
            } else if (expectedNamePath != null) {
                assertThat(span).hasName("Elasticsearch: " + expectedHttpMethod + " " + expectedNamePath);
            }

            checkHttpContext();
            checkDbContext();
            checkPathPartAttributes();
            checkDestinationContext();
            if (isAsyncRequest) {
                assertThat(span).isAsync();
            } else {
                assertThat(span).isSync();
            }
        }


        private void checkHttpContext() {
            HttpImpl http = span.getContext().getHttp();
            assertThat(http).isNotNull();
            if (expectedHttpMethod != null) {
                assertThat(http.getMethod()).isEqualTo(expectedHttpMethod);
            }
            assertThat(http.getStatusCode()).isEqualTo(expectedStatusCode);
            if (expectedHttpUrl != null) {
                assertThat(http.getUrl().toString()).isEqualTo(expectedHttpUrl);
            }
        }

        private void checkDbContext() {
            DbImpl db = span.getContext().getDb();
            assertThat(db.getType()).isEqualTo(ELASTICSEARCH);
            CharSequence statement = db.getStatementBuffer();
            if (statementExpectedNonNull) {
                assertThat(statement).isNotNull();
                if (expectedStatement != null) {
                    //Comparing JsonNodes ensures that the child-order within JSON objects does not matter
                    JsonNode parsedStatement;
                    try {
                        parsedStatement = jackson.readTree(statement.toString());
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException(e);
                    }
                    assertThat(parsedStatement).isEqualTo(expectedStatement);
                }
            } else {
                assertThat(statement).isNull();
            }
        }

        private void checkPathPartAttributes() {
            if (expectedPathParts != null) {
                expectedPathParts.forEach((partName, value) -> {
                    assertThat(span.getOtelAttributes()).containsEntry("db.elasticsearch.path_parts." + partName, value);
                });
                List<String> spanPartAttributes = span.getOtelAttributes().keySet().stream()
                    .filter(name -> name.startsWith("db.elasticsearch.path_parts."))
                    .map(name -> name.substring("db.elasticsearch.path_parts.".length()))
                    .collect(Collectors.toList());
                assertThat(spanPartAttributes).containsExactlyElementsOf(expectedPathParts.keySet());
            }
        }

        private void checkDestinationContext() {
            DestinationImpl destination = span.getContext().getDestination();
            assertThat(destination).isNotNull();
            if (reporter.checkDestinationAddress()) {
                assertThat(destination.getAddress().toString()).isEqualTo(container.getContainerIpAddress());
                assertThat(destination.getPort()).isEqualTo(container.getMappedPort(9200));
            }
        }

    }

}
