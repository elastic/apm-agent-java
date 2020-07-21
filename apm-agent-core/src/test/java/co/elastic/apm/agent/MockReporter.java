/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2020 Elastic and contributors
 * %%
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
 * #L%
 */
package co.elastic.apm.agent;

import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.context.Destination;
import co.elastic.apm.agent.impl.error.ErrorCapture;
import co.elastic.apm.agent.impl.stacktrace.StacktraceConfiguration;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.metrics.MetricRegistry;
import co.elastic.apm.agent.report.ApmServerClient;
import co.elastic.apm.agent.report.IntakeV2ReportingEventHandler;
import co.elastic.apm.agent.report.Reporter;
import co.elastic.apm.agent.report.ReportingEvent;
import co.elastic.apm.agent.report.serialize.DslJsonSerializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.ValidationMessage;
import org.awaitility.core.ConditionFactory;
import org.awaitility.core.ThrowingRunnable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MockReporter implements Reporter {
    private static final JsonSchema transactionSchema;
    private static final JsonSchema errorSchema;
    private static final JsonSchema spanSchema;
    private static final DslJsonSerializer dslJsonSerializer;

    // A set of exit span subtypes that do not support address and port discovery
    private static final Set<String> SPAN_TYPES_WITHOUT_ADDRESS;
    // A map of exit span type to actions that that do not support address and port discovery
    private static final Map<String, Collection<String>> SPAN_ACTIONS_WITHOUT_ADDRESS;
    // And for any case the disablement of the check cannot rely on subtype (eg Redis, where Jedis supports and Lettuce does not)
    private boolean disableDestinationAddressCheck;

    private final List<Transaction> transactions = Collections.synchronizedList(new ArrayList<>());
    private final List<Span> spans = Collections.synchronizedList(new ArrayList<>());
    private final List<ErrorCapture> errors = Collections.synchronizedList(new ArrayList<>());
    private final List<byte[]> bytes = Collections.synchronizedList(new ArrayList<>());
    private final ObjectMapper objectMapper;
    private final boolean verifyJsonSchema;
    private boolean closed;

    static {
        transactionSchema = getSchema("/schema/transactions/transaction.json");
        spanSchema = getSchema("/schema/transactions/span.json");
        errorSchema = getSchema("/schema/errors/error.json");
        ApmServerClient apmServerClient = mock(ApmServerClient.class);
        when(apmServerClient.isAtLeast(any())).thenReturn(true);
        dslJsonSerializer = new DslJsonSerializer(mock(StacktraceConfiguration.class), apmServerClient);
        SPAN_TYPES_WITHOUT_ADDRESS = Set.of("jms");
        SPAN_ACTIONS_WITHOUT_ADDRESS = Map.of("kafka", Set.of("poll"));
    }

    public MockReporter() {
        this(true);
    }

    public MockReporter(boolean verifyJsonSchema) {
        this.verifyJsonSchema = verifyJsonSchema;
        objectMapper = new ObjectMapper();
    }

    private static JsonSchema getSchema(String resource) {
        return JsonSchemaFactory.getInstance().getSchema(MockReporter.class.getResourceAsStream(resource));
    }

    public void disableDestinationAddressCheck() {
        disableDestinationAddressCheck = true;
    }

    @Override
    public void start() {}

    @Override
    public synchronized void report(Transaction transaction) {
        if (closed) {
            return;
        }
        verifyTransactionSchema(asJson(dslJsonSerializer.toJsonString(transaction)));
        transactions.add(transaction);
    }

    @Override
    public synchronized void report(Span span) {
        if (closed) {
            return;
        }
        verifySpanSchema(asJson(dslJsonSerializer.toJsonString(span)));
        verifyDestinationFields(span);
        spans.add(span);
    }

    private void verifyDestinationFields(Span span) {
        if (!span.isExit()) {
            return;
        }
        Destination destination = span.getContext().getDestination();
        if (!disableDestinationAddressCheck && !SPAN_TYPES_WITHOUT_ADDRESS.contains(span.getSubtype())) {
            // see if this span's action is not supported for its subtype
            Collection<String> unsupportedActions = SPAN_ACTIONS_WITHOUT_ADDRESS.getOrDefault(span.getSubtype(), Collections.emptySet());
            if (!unsupportedActions.contains(span.getAction())) {
                assertThat(destination.getAddress()).describedAs("destination address is required").isNotEmpty();
                assertThat(destination.getPort()).describedAs("destination port is required").isGreaterThan(0);
            }
        }
        Destination.Service service = destination.getService();
        assertThat(service.getName()).describedAs("service name is required").isNotEmpty();
        assertThat(service.getResource()).describedAs("service resourse is required").isNotEmpty();
        assertThat(service.getType()).describedAs("service type is required").isNotNull();
    }

    public void verifyTransactionSchema(JsonNode jsonNode) {
        verifyJsonSchema(transactionSchema, jsonNode);
    }

    public void verifySpanSchema(JsonNode jsonNode) {
        verifyJsonSchema(spanSchema, jsonNode);
    }

    public void verifyErrorSchema(JsonNode jsonNode) {
        verifyJsonSchema(errorSchema, jsonNode);
    }

    private void verifyJsonSchema(JsonSchema schema, JsonNode jsonNode) {
        if (verifyJsonSchema) {
            Set<ValidationMessage> errors = schema.validate(jsonNode);
            assertThat(errors).withFailMessage("%s\n%s", errors, jsonNode).isEmpty();
        }
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
        awaitTimeout(timeoutMs)
            .untilAsserted(() -> assertThat(getTransactions()).isNotEmpty());
        return getFirstTransaction();
    }

    public void assertNoTransaction() {
        assertThat(getTransactions())
            .describedAs("no transaction expected")
            .isEmpty();
    }

    public void assertNoTransaction(long timeoutMs) {
        awaitTimeout(timeoutMs)
            .untilAsserted(this::assertNoTransaction);
    }

    public void awaitUntilAsserted(long timeoutMs, ThrowingRunnable assertion){
        awaitTimeout(timeoutMs)
            .untilAsserted(assertion);
    }

    private static ConditionFactory awaitTimeout(long timeoutMs) {
        return await()
            .pollInterval(1, TimeUnit.MILLISECONDS)
            .timeout(timeoutMs, TimeUnit.MILLISECONDS);
    }

    public Span getFirstSpan(long timeoutMs) {
        awaitTimeout(timeoutMs)
            .untilAsserted(() -> assertThat(getSpans()).isNotEmpty());
        return getFirstSpan();
    }

    public void assertNoSpan() {
        assertThat(getSpans())
            .describedAs("no span expected")
            .isEmpty();
    }

    public void assertNoSpan(long timeoutMs) {
        awaitTimeout(timeoutMs)
            .untilAsserted(() -> assertThat(getSpans()).isEmpty());

        assertNoSpan();
    }

    public void awaitTransactionCount(int count) {
        awaitTimeout(1000)
            .untilAsserted(() -> assertThat(getNumReportedTransactions()).isEqualTo(count));
    }

    public void awaitTransactionReported() {
        awaitTimeout(1000)
            .untilAsserted(() -> assertThat(getNumReportedTransactions()).isGreaterThan(0));
    }

    public void awaitSpanCount(int count) {
        awaitTimeout(1000)
            .untilAsserted(() -> assertThat(getNumReportedSpans()).isEqualTo(count));
    }

    public void awaitSpanReported() {
        awaitTimeout(1000)
            .untilAsserted(() -> assertThat(getNumReportedSpans()).isGreaterThan(0));
    }

    @Override
    public synchronized void report(ErrorCapture error) {
        if (closed) {
            return;
        }
        verifyErrorSchema(asJson(dslJsonSerializer.toJsonString(error)));
        errors.add(error);
    }

    @Override
    public synchronized void report(byte[] bytes) {
        if (closed) {
            return;
        }
        this.bytes.add(bytes);
    }

    @Override
    public void scheduleMetricReporting(MetricRegistry metricRegistry, long intervalMs, final ElasticApmTracer tracer) {
        // noop
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

    public synchronized int getNumReportedSpans() {
        return spans.size();
    }

    public synchronized List<ErrorCapture> getErrors() {
        return Collections.unmodifiableList(errors);
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
    public Future<Void> flush() {
        return new Future<>() {
            @Override
            public boolean cancel(boolean mayInterruptIfRunning) {
                return false;
            }

            @Override
            public boolean isCancelled() {
                return false;
            }

            @Override
            public boolean isDone() {
                return false;
            }

            @Override
            public Void get() throws InterruptedException, ExecutionException {
                return null;
            }

            @Override
            public Void get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
                return null;
            }
        };
    }

    @Override
    public synchronized void close() {
        closed = true;
    }

    public synchronized void reset() {
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
            .filter(s-> !hasEmptyTraceContext(s))
            .collect(Collectors.toList());

        transactionsToFlush.forEach(Transaction::decrementReferences);
        spansToFlush.forEach(Span::decrementReferences);

        // transactions might be active after they have already been reported
        // after a short amount of time, all transactions and spans should have been recycled
        await()
            .timeout(1, TimeUnit.SECONDS)
            .untilAsserted(() -> transactions.forEach(t -> {
                assertThat(hasEmptyTraceContext(t))
                    .describedAs("should have empty trace context : %s", t)
                    .isTrue();
                assertThat(t.isReferenced())
                    .describedAs("should not have any reference left, but has %d : %s", t.getReferenceCount(), t)
                    .isFalse();
            }));
        await()
            .timeout(1, TimeUnit.SECONDS)
            .untilAsserted(() -> spans.forEach(s -> {
                assertThat(hasEmptyTraceContext(s))
                    .describedAs("should have empty trace context : %s", s)
                    .isTrue();
                assertThat(s.isReferenced())
                    .describedAs("should not have any reference left, but has %d : %s", s.getReferenceCount(), s)
                    .isFalse();
            }));

        // errors are recycled directly because they have no reference counter
        errors.forEach(ErrorCapture::recycle);
    }

    private static boolean hasEmptyTraceContext(AbstractSpan<?> item) {
        return item.getTraceContext().getId().isEmpty();
    }
}
