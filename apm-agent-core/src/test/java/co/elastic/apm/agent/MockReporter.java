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
package co.elastic.apm.agent;

import co.elastic.apm.agent.configuration.CoreConfiguration;
import co.elastic.apm.agent.configuration.SpyConfiguration;
import co.elastic.apm.agent.impl.context.Destination;
import co.elastic.apm.agent.impl.error.ErrorCapture;
import co.elastic.apm.agent.impl.metadata.MetaData;
import co.elastic.apm.agent.impl.stacktrace.StacktraceConfiguration;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.report.ApmServerClient;
import co.elastic.apm.agent.report.IntakeV2ReportingEventHandler;
import co.elastic.apm.agent.report.Reporter;
import co.elastic.apm.agent.report.ReporterMonitor;
import co.elastic.apm.agent.report.ReportingEvent;
import co.elastic.apm.agent.report.serialize.DslJsonSerializer;
import co.elastic.apm.agent.report.serialize.SerializationConstants;
import co.elastic.apm.agent.tracer.Outcome;
import com.dslplatform.json.JsonWriter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.ValidationMessage;
import org.awaitility.core.ThrowingRunnable;
import org.stagemonitor.configuration.ConfigurationRegistry;
import specs.TestJsonSpec;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Function;
import java.util.stream.Collectors;

import static co.elastic.apm.agent.testutils.assertions.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;


public class MockReporter implements Reporter {

    // A set of exit span subtypes that do not support address and port discovery
    private static final Set<String> SPAN_TYPES_WITHOUT_ADDRESS;
    // A map of exit span type to subtypes that do not support address and port discovery
    private static final Map<String, Collection<String>> SPAN_SUBTYPES_WITHOUT_ADDRESS;
    // A map of exit span subtypes to actions that do not support address and port discovery
    private static final Map<String, Collection<String>> SPAN_ACTIONS_WITHOUT_ADDRESS;
    // And for any case the disablement of the check cannot rely on subtype (eg Redis, where Jedis supports and Lettuce does not)
    private boolean checkDestinationAddress = true;
    // All exit spans coming from internal plugins should have a valid service target
    private boolean checkServiceTarget = true;
    // Allows optional opt-out for unknown outcome
    private boolean checkUnknownOutcomes = true;
    // Allows optional opt-out from strict span type/sub-type checking
    private boolean checkStrictSpanType = true;
    //Instead of recording data, they are recycled immediately. Mainly used in repeated tests to uncover issues with premature recycling.
    private boolean enabledImmediateRecycling = false;

    /**
     * If set to {@code true}, the reporter will attempt to execute gc when asserting that all objects were properly
     * recycled. This is useful for tests that use weak maps and rely on GC to clear reference and recycle objects.
     */
    private boolean gcWhenAssertingRecycling;

    private final List<Transaction> transactions = Collections.synchronizedList(new ArrayList<>());
    private final List<Span> spans = Collections.synchronizedList(new ArrayList<>());
    private final List<ErrorCapture> errors = Collections.synchronizedList(new ArrayList<>());
    private final List<byte[]> bytes = new CopyOnWriteArrayList<>();
    private final List<String> logs = Collections.synchronizedList(new ArrayList<>());
    private final ObjectMapper objectMapper;
    private final boolean verifyJsonSchema;

    private boolean closed;

    // we have to use a longer timeout on Windows to help reduce flakyness
    private static final long DEFAULT_ASSERTION_TIMEOUT = System.getProperty("os.name").startsWith("Windows") ? 3000 : 1000;

    private static final JsonNode SPAN_TYPES_SPEC = TestJsonSpec.getJson("span_types.json");

    static {
        SPAN_TYPES_WITHOUT_ADDRESS = Set.of("jms");
        SPAN_SUBTYPES_WITHOUT_ADDRESS = Map.of("db", Set.of("h2", "unknown"));
        SPAN_ACTIONS_WITHOUT_ADDRESS = Map.of("kafka", Set.of("poll"));
    }

    public MockReporter() {
        this(true);
    }

    public MockReporter(boolean verifyJsonSchema) {
        this.verifyJsonSchema = verifyJsonSchema;
        objectMapper = new ObjectMapper();
    }

    /**
     * Resets checks to be executed based on the default behaviour.
     * All optional checks are enabled and special requested behaviour is disabled.
     * Should be used as a shortcut to reset mock reporter state after/before using it for a single test execution.
     */
    public void resetChecks() {
        checkDestinationAddress = true;
        checkServiceTarget = true;
        checkUnknownOutcomes = true;
        checkStrictSpanType = true;
        gcWhenAssertingRecycling = false;
    }

    /**
     * Disables unknown outcome check
     */
    public void disableCheckUnknownOutcome() {
        checkUnknownOutcomes = false;
    }

    /**
     * Disables destination address check
     */
    public void disableCheckDestinationAddress() {
        checkDestinationAddress = false;
    }

    /**
     * Disables destination service check
     */
    public void disableCheckServiceTarget() {
        checkServiceTarget = false;
    }

    public boolean checkDestinationAddress() {
        return checkDestinationAddress;
    }

    /**
     * Disables strict span type and sub-type check (against shared spec)
     */
    public void disableCheckStrictSpanType() {
        checkStrictSpanType = false;
    }

    /**
     * If invoked, the reporter will attempt to execute gc when asserting that all objects were properly
     * recycled. This is useful for tests that use weak maps and rely on GC to clear reference and recycle objects.
     */
    public void enableGcWhenAssertingObjectRecycling() {
        gcWhenAssertingRecycling = true;
    }

    /**
     * If set to true, spans & transactions will not be recorded but instead immediately recycled.
     *
     * @param enable true to enable immediate recycling
     */
    public void setImmediateRecycling(boolean enable) {
        this.enabledImmediateRecycling = enable;
    }

    @Override
    public void start() {
    }

    @Override
    public synchronized void report(Transaction transaction) {
        if (closed) {
            return;
        }

        String type = transaction.getType();
        assertThat(type).isNotNull();

        if (checkUnknownOutcomes) {
            assertThat(transaction.getOutcome())
                .describedAs("transaction outcome should be either success or failure for type = %s", type)
                .isNotEqualTo(Outcome.UNKNOWN);
        }

        verifyTransactionSchema(transaction);
        if (enabledImmediateRecycling) {
            transaction.decrementReferences();
        } else {
            transactions.add(transaction);
        }
    }

    @Override
    public synchronized void report(Span span) {
        if (closed) {
            return;
        }

        try {
            verifySpanSchema(span);
            verifySpanType(span);
            verifyDestinationFields(span);
            verifyServiceTarget(span);

            if (checkUnknownOutcomes) {
                assertThat(span.getOutcome())
                    .describedAs("span outcome should be either success or failure for type = %s", span.getType())
                    .isNotEqualTo(Outcome.UNKNOWN);
            }
        } catch (Exception e) {
            // in case a validation error occurs, ensure that it's properly logged for easier debugging
            e.printStackTrace(System.err);
            throw e;
        }
        if (enabledImmediateRecycling) {
            span.decrementReferences();
        } else {
            spans.add(span);
        }
    }


    private void verifySpanType(Span span) {
        String type = span.getType();
        assertThat(type)
            .describedAs("span type is mandatory")
            .isNotNull();

        if (checkStrictSpanType) {
            JsonNode typeJson = getMandatoryJson(SPAN_TYPES_SPEC, type, String.format("span type '%s' is not allowed by the spec", type));

            boolean allowNullSubtype = getBooleanJson(typeJson, "allow_null_subtype");
            boolean allowUnlistedSubtype = getBooleanJson(typeJson, "allow_unlisted_subtype");

            String subType = span.getSubtype();

            JsonNode subTypesJson = typeJson.get("subtypes");
            boolean hasSubtypes = subTypesJson != null && !subTypesJson.isEmpty();

            if (null == subType) {
                if (hasSubtypes) {
                    assertThat(allowNullSubtype)
                        .describedAs("span type '%s' requires non-null subtype (allow_null_subtype=false)", type)
                        .isTrue();
                }
            } else {
                if (!allowUnlistedSubtype && hasSubtypes) {
                    getMandatoryJson(subTypesJson, subType, String.format("span subtype '%s' is not allowed by the spec for type '%s'", subType, type));
                }
            }

        }

    }

    private void verifyDestinationFields(Span span) {
        if (!span.isExit()) {
            return;
        }
        Destination destination = span.getContext().getDestination();
        if (checkDestinationAddress && !SPAN_TYPES_WITHOUT_ADDRESS.contains(span.getSubtype())) {
            // see if this span's subtype is not supported for its type
            Collection<String> unsupportedSubtypes = SPAN_SUBTYPES_WITHOUT_ADDRESS.getOrDefault(span.getType(), Collections.emptySet());
            // see if this span's action is not supported for its subtype
            Collection<String> unsupportedActions = SPAN_ACTIONS_WITHOUT_ADDRESS.getOrDefault(span.getSubtype(), Collections.emptySet());
            if (!(unsupportedSubtypes.contains(span.getSubtype()) || unsupportedActions.contains(span.getAction()))) {
                assertThat(destination.getAddress()).describedAs("destination address is required").isNotEmpty();
                assertThat(destination.getPort()).describedAs("destination port is required").isGreaterThan(0);
            }
        }
    }

    private void verifyServiceTarget(Span span) {
        if (!span.isExit() || !checkServiceTarget) {
            return;
        }

        assertThat(span.getContext().getServiceTarget())
            .describedAs("service target is required")
            .isNotEmpty();

        assertThat(span.getContext().getServiceTarget().getDestinationResource())
            .describedAs("legacy destination service resource is required")
            .isNotEmpty();
    }

    /**
     * Checks the transaction serialization against all available schemas
     *
     * @param transaction transaction to serialize
     */
    public void verifyTransactionSchema(Transaction transaction) {
        verifyJsonSchemas(dsl -> dsl.toJsonString(transaction), si -> si.transactionSchema, si -> si.transactionSchemaPath);
    }

    /**
     * Checks the transaction serialization against all available schemas
     *
     * @param span span to serialize
     */
    public void verifySpanSchema(Span span) {
        verifyJsonSchemas(dsl -> dsl.toJsonString(span), si -> si.spanSchema, si -> si.spanSchemaPath);
    }

    /**
     * Checks the error serialization against all available schemas
     *
     * @param error error to serialize
     */
    public void verifyErrorSchema(ErrorCapture error) {
        verifyJsonSchemas(dsl -> dsl.toJsonString(error), si -> si.errorSchema, si -> si.errorSchemaPath);
    }

    /**
     * Checks the transaction JSON against the current schema
     *
     * @param jsonNode serialized transaction json
     */
    public void verifyTransactionSchema(JsonNode jsonNode) {
        verifyJsonSchema(jsonNode, SchemaInstance.CURRENT.transactionSchema, SchemaInstance.CURRENT.transactionSchemaPath);
    }

    /**
     * Checks the span JSON against the current schema
     *
     * @param jsonNode serialized span json
     */
    public void verifySpanSchema(JsonNode jsonNode) {
        verifyJsonSchema(jsonNode, SchemaInstance.CURRENT.spanSchema, SchemaInstance.CURRENT.spanSchemaPath);
    }

    /**
     * Checks the error JSON against the current schema
     *
     * @param jsonNode serialized error json
     */
    public void verifyErrorSchema(JsonNode jsonNode) {
        verifyJsonSchema(jsonNode, SchemaInstance.CURRENT.errorSchema, SchemaInstance.CURRENT.errorSchemaPath);
    }

    private void verifyJsonSchemas(Function<DslJsonSerializer.Writer, String> serializerFunction,
                                   Function<SchemaInstance, JsonSchema> schemaFunction,
                                   Function<SchemaInstance, String> schemaPathFunction) {
        if (!verifyJsonSchema) {
            return;
        }

        for (SchemaInstance schemaInstance : SchemaInstance.values()) {
            String serializedString = serializerFunction.apply(schemaInstance.serializer);
            JsonNode jsonNode = asJson(serializedString);

            JsonSchema schema = schemaFunction.apply(schemaInstance);
            verifyJsonSchema(jsonNode, schema, schemaPathFunction.apply(schemaInstance));
        }
    }

    private void verifyJsonSchema(JsonNode jsonNode, JsonSchema schema, String schemaPath) {
        Set<ValidationMessage> errors = schema.validate(jsonNode);
        assertThat(errors)
            .withFailMessage("%s\nJSON schema path = %s\n\n%s", errors, schemaPath, jsonNode.toPrettyString())
            .isEmpty();
    }

    private JsonNode asJson(String jsonContent) {
        try {
            return objectMapper.readTree(jsonContent);
        } catch (IOException e) {
            System.out.println(jsonContent);
            throw new RuntimeException(e);
        }
    }

    public synchronized List<Transaction> getTransactions() {
        return Collections.unmodifiableList(transactions);
    }

    public synchronized int getNumReportedTransactions() {
        return transactions.size();
    }

    public synchronized Transaction getFirstTransaction() {
        assertThat(transactions)
            .describedAs("at least one transaction expected, none have been reported (yet)")
            .isNotEmpty();
        return transactions.get(0);
    }

    public Transaction getFirstTransaction(long timeoutMs) {
        awaitUntilAsserted(timeoutMs, () -> assertThat(getTransactions()).isNotEmpty());
        return getFirstTransaction();
    }

    public void assertNoTransaction() {
        assertThat(getTransactions())
            .describedAs("no transaction expected")
            .isEmpty();
    }

    public void assertNoTransaction(long timeoutMs) {
        awaitUntilTimeout(timeoutMs, this::assertNoTransaction);
    }

    public Span getFirstSpan(long timeoutMs) {
        awaitUntilAsserted(timeoutMs, () -> assertThat(getSpans()).isNotEmpty());
        return getFirstSpan();
    }

    public ErrorCapture getFirstError(long timeoutMs) {
        awaitUntilAsserted(timeoutMs, () -> assertThat(getErrors()).isNotEmpty());
        return getFirstError();
    }

    public void assertNoSpan() {
        assertThat(getSpans())
            .describedAs("no span expected")
            .isEmpty();
    }

    public void assertNoSpan(long timeoutMs) {
        awaitUntilTimeout(timeoutMs, this::assertNoSpan);
    }

    public void awaitTransactionCount(int count) {
        awaitUntilAsserted(() -> assertThat(getNumReportedTransactions())
            .describedAs("expecting %d transactions, transactions = %s", count, transactions)
            .isEqualTo(count));
    }

    public void awaitTransactionCount(int count, long timeoutMs) {
        awaitUntilAsserted(timeoutMs, () -> assertThat(getNumReportedTransactions())
            .describedAs("expecting %d transactions, transactions = %s", count, transactions)
            .isEqualTo(count));
    }

    public void awaitSpanCount(int count) {
        awaitUntilAsserted(() -> assertThat(getNumReportedSpans())
            .describedAs("expecting %d spans", count)
            .isEqualTo(count));
    }

    public void awaitErrorCount(int count) {
        awaitUntilAsserted(() -> assertThat(getNumReportedErrors())
            .describedAs("expecting %d errors", count)
            .isEqualTo(count));
    }

    public void awaitLogsCount(int count) {
        awaitUntilAsserted(() -> assertThat(getNumReportedLogs())
            .describedAs("expecting %d logs", count)
            .isEqualTo(count));
    }

    @Override
    public synchronized void report(ErrorCapture error) {
        if (closed) {
            return;
        }
        verifyErrorSchema(error);
        errors.add(error);
    }

    @Override
    public synchronized void reportMetrics(JsonWriter jsonWriter) {
        if (closed) {
            return;
        }
        this.bytes.add(jsonWriter.toByteArray());
    }

    @Override
    public void reportLog(String log) {
        this.logs.add(log);
    }

    @Override
    public void reportLog(byte[] log) {
        this.logs.add(new String(log, StandardCharsets.UTF_8));
    }

    @Override
    public void reportAgentLog(byte[] log) {
        this.logs.add(new String(log, StandardCharsets.UTF_8));
    }

    @Override
    public boolean flush() {
        return true;
    }

    public synchronized Span getFirstSpan() {
        assertThat(spans)
            .describedAs("at least one span expected, none have been reported")
            .isNotEmpty();
        return spans.get(0);
    }

    public synchronized List<Span> getSpans() {
        return Collections.unmodifiableList(spans);
    }

    public synchronized JsonNode getFirstLog() {
        assertThat(logs)
            .describedAs("at least one log expected, none have been reported")
            .isNotEmpty();
        return asJson(logs.get(0));
    }

    public synchronized List<JsonNode> getLogs() {
        return logs.stream()
            .map(log -> asJson(log))
            .collect(Collectors.toList());
    }

    public Span getSpanByName(String name) {
        Optional<Span> optional = getSpans().stream().filter(s -> s.getNameAsString().equals(name)).findAny();
        assertThat(optional)
            .withFailMessage("No span with name '%s' found in reported spans %s", name,
                getSpans().stream().map(Span::getNameAsString).collect(Collectors.toList()))
            .isPresent();
        return optional.get();
    }

    public synchronized int getNumReportedSpans() {
        return spans.size();
    }

    public synchronized List<ErrorCapture> getErrors() {
        return Collections.unmodifiableList(errors);
    }

    public synchronized int getNumReportedErrors() {
        return errors.size();
    }

    public synchronized int getNumReportedLogs() {
        return logs.size();
    }

    public synchronized ErrorCapture getFirstError() {
        assertThat(errors)
            .describedAs("at least one error expected, none have been reported")
            .isNotEmpty();
        return errors.iterator().next();
    }

    public synchronized List<byte[]> getBytes() {
        return bytes;
    }

    @Override
    public long getDropped() {
        return 0;
    }

    @Override
    public long getReported() {
        return 0;
    }

    @Override
    public boolean flush(long timeout, TimeUnit unit, boolean followupWithFlushRequest) {
        return true;
    }

    @Override
    public synchronized void close() {
        closed = true;
    }

    public synchronized void reset() {
        assertRecycledAfterDecrementingReferences();
        resetWithoutRecycling();
    }

    public synchronized void resetWithoutRecycling() {
        transactions.clear();
        spans.clear();
        errors.clear();
        bytes.clear();
    }

    /**
     * Calls {@link AbstractSpan#decrementReferences()} for all reported transactions and spans to emulate the references being decremented
     * after reporting to the APM Server.
     * See {@link IntakeV2ReportingEventHandler#writeEvent(ReportingEvent)}
     */
    public synchronized void decrementReferences() {
        transactions.forEach(Transaction::decrementReferences);
        spans.forEach(Span::decrementReferences);
    }

    /**
     * Decrements transactions and spans reference count and check that they are properly recycled. This method should likely be called
     * last in the test execution as it destroys any transaction/span that has happened.
     */
    public synchronized void assertRecycledAfterDecrementingReferences() {

        List<Transaction> transactions = getTransactions();
        List<Transaction> transactionsToFlush = transactions.stream()
            .filter(t -> !hasEmptyTraceContext(t))
            .collect(Collectors.toList());

        List<Span> spans = getSpans();
        List<Span> spansToFlush = spans.stream()
            .filter(s -> !hasEmptyTraceContext(s))
            .collect(Collectors.toList());

        transactionsToFlush.forEach(Transaction::decrementReferences);
        spansToFlush.forEach(Span::decrementReferences);

        if (gcWhenAssertingRecycling) {
            System.gc();
        }

        try {
            awaitUntilAsserted(() -> {
                spans.forEach(s -> {
                    assertThat(s.isReferenced())
                        .describedAs("should not have any reference left, but has %d : %s", s.getReferenceCount(), s)
                        .isFalse();
                    assertThat(hasEmptyTraceContext(s))
                        .describedAs("should have empty trace context : %s", s)
                        .isTrue();
                });
                transactions.forEach(t -> {
                    assertThat(t.isReferenced())
                        .describedAs("should not have any reference left, but has %d : %s", t.getReferenceCount(), t)
                        .isFalse();
                    assertThat(hasEmptyTraceContext(t))
                        .describedAs("should have empty trace context : %s", t)
                        .isTrue();
                });
            });
        } catch (AssertionError e) {
            // clear collections when assertion fails to prevent a test failure to affect following tests
            this.transactions.clear();
            this.spans.clear();
            throw e;
        }


        // errors are recycled directly because they have no reference counter
        errors.forEach(ErrorCapture::recycle);
    }

    /**
     * Uses the default timeout (see {@link  #DEFAULT_ASSERTION_TIMEOUT})
     *
     * @see #awaitUntilAsserted(long, ThrowingRunnable)
     */
    public void awaitUntilAsserted(ThrowingRunnable runnable) {
        awaitUntilAsserted(DEFAULT_ASSERTION_TIMEOUT, runnable);
    }

    /**
     * This is deliberately not using {@link org.awaitility.Awaitility} as it uses an {@link java.util.concurrent.Executor} for polling.
     * This is an issue when testing instrumentations that instrument {@link java.util.concurrent.Executor}.
     *
     * @param timeoutMs the timeout of the condition
     * @param runnable  a runnable that throws an exception if the condition is not met
     */
    public void awaitUntilAsserted(long timeoutMs, ThrowingRunnable runnable) {
        Throwable thrown = null;
        for (int i = 0; i < timeoutMs; i += 5) {
            try {
                runnable.run();
                return;
            } catch (Throwable e) {
                thrown = e;
                LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(5));
            }
        }
        throw new RuntimeException(String.format("Condition not fulfilled within %d ms", timeoutMs), thrown);
    }

    /**
     * @param timeoutMs timeout of the condition
     * @param runnable  a runnable that throws an exception when the condition is not met
     */
    public void awaitUntilTimeout(long timeoutMs, ThrowingRunnable runnable) {
        for (int i = 0; i < timeoutMs; i += 5) {
            try {
                runnable.run();
                LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(5));
            } catch (Throwable e) {
                throw new RuntimeException(String.format("Condition not fulfilled within %d ms (timeout at %d ms)", i, timeoutMs), e);
            }
        }
    }

    private static boolean hasEmptyTraceContext(AbstractSpan<?> item) {
        return item.getTraceContext().getId().isEmpty();
    }

    private static JsonNode getMandatoryJson(JsonNode json, String name, String desc) {
        JsonNode jsonNode = json.get(name);
        assertThat(jsonNode)
            .describedAs(desc)
            .isNotNull();
        assertThat(jsonNode.isObject())
            .isTrue();
        return jsonNode;
    }

    private static boolean getBooleanJson(JsonNode json, String name) {
        JsonNode jsonValue = json.get(name);
        boolean value = false;
        if (jsonValue != null) {
            assertThat(jsonValue.isBoolean())
                .describedAs("property %s should be a boolean", name)
                .isTrue();
            value = jsonValue.asBoolean();
        }
        return value;
    }

    private enum SchemaInstance {

        CURRENT("/apm-server-schema/current/transaction.json",
            "/apm-server-schema/current/span.json",
            "/apm-server-schema/current/error.json",
            true),
        V6_5(
            "/apm-server-schema/v6_5/transactions/transaction.json",
            "/apm-server-schema/v6_5/transactions/span.json",
            "/apm-server-schema/v6_5/errors/error.json",
            false);

        private final DslJsonSerializer.Writer serializer;
        private final JsonSchema transactionSchema;
        private final String transactionSchemaPath;
        private final JsonSchema spanSchema;
        private final String spanSchemaPath;
        private final JsonSchema errorSchema;
        private final String errorSchemaPath;

        SchemaInstance(String transactionSchema, String spanSchema, String errorSchema, boolean isLatest) {
            this.transactionSchema = getSchema(transactionSchema);
            this.transactionSchemaPath = transactionSchema;
            this.spanSchema = getSchema(spanSchema);
            this.spanSchemaPath = spanSchema;
            this.errorSchema = getSchema(errorSchema);
            this.errorSchemaPath = errorSchema;

            ConfigurationRegistry spyConfig = SpyConfiguration.createSpyConfig();
            StacktraceConfiguration stacktraceConfiguration = spyConfig.getConfig(StacktraceConfiguration.class);

            Future<MetaData> metaData = MetaData.create(spyConfig, null);
            ApmServerClient client = mock(ApmServerClient.class);
            doReturn(isLatest).when(client).isAtLeast(any());

            // The oldest server does not support any of those features, the current server supports all of them
            doReturn(isLatest).when(client).supportsNumericUrlPort();
            doReturn(isLatest).when(client).supportsNonStringLabels();
            doReturn(isLatest).when(client).supportsLogsEndpoint();

            SerializationConstants.init(spyConfig.getConfig(CoreConfiguration.class));
            this.serializer = new DslJsonSerializer(stacktraceConfiguration, client, metaData).newWriter();
        }

        private static JsonSchema getSchema(String resource) {
            InputStream input = Objects.requireNonNull(MockReporter.class.getResourceAsStream(resource), "missing resource " + resource);
            return JsonSchemaFactory.getInstance().getSchema(input);
        }
    }

    public void setReporterMonitor(ReporterMonitor monitor) {
        throw new UnsupportedOperationException();
    }

}
