/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2019 Elastic and contributors
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

import static org.assertj.core.api.Assertions.assertThat;
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

    private final List<Transaction> transactions = new ArrayList<>();
    private final List<Span> spans = new ArrayList<>();
    private final List<ErrorCapture> errors = new ArrayList<>();
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
                assertThat(destination.getAddress()).isNotEmpty();
                assertThat(destination.getPort()).isGreaterThan(0);
            }
        }
        Destination.Service service = destination.getService();
        assertThat(service.getName()).isNotEmpty();
        assertThat(service.getResource()).isNotEmpty();
        assertThat(service.getType()).isNotNull();
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

    public synchronized Transaction getFirstTransaction() {
        return transactions.iterator().next();
    }

    public Transaction getFirstTransaction(long timeoutMs) throws InterruptedException {
        final long end = System.currentTimeMillis() + timeoutMs;
        do {
            synchronized (this) {
                if (!transactions.isEmpty()) {
                    return getFirstTransaction();
                }
            }
            Thread.sleep(1);
        } while (System.currentTimeMillis() < end);
        return getFirstTransaction();
    }

    public Span getFirstSpan(long timeoutMs) throws InterruptedException {
        final long end = System.currentTimeMillis() + timeoutMs;
        do {
            synchronized (this) {
                if (!spans.isEmpty()) {
                    return getFirstSpan();
                }
            }
            Thread.sleep(1);
        } while (System.currentTimeMillis() < end);
        return getFirstSpan();
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
    public void scheduleMetricReporting(MetricRegistry metricRegistry, long intervalMs) {
        // noop
    }

    public synchronized Span getFirstSpan() {
        return spans.get(0);
    }

    public synchronized List<Span> getSpans() {
        return spans;
    }

    public synchronized List<ErrorCapture> getErrors() {
        return Collections.unmodifiableList(errors);
    }

    public synchronized ErrorCapture getFirstError() {
        return errors.iterator().next();
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
    public void close() {
        closed = true;
    }

    public void reset() {
        transactions.clear();
        errors.clear();
        spans.clear();
    }

    /**
     * Calls {@link AbstractSpan#decrementReferences()} for all reported transactions and spans to emulate the references being decremented
     * after reporting to the APM Server.
     * See {@link IntakeV2ReportingEventHandler#writeEvent(ReportingEvent)}
     */
    public void decrementReferences() {
        transactions.forEach(Transaction::decrementReferences);
        spans.forEach(Span::decrementReferences);
    }

    public void assertRecycledAfterDecrementingReferences() {
        transactions.forEach(t -> assertThat(t.getTraceContext().getId().isEmpty()).isFalse());
        spans.forEach(s -> assertThat(s.getTraceContext().getId().isEmpty()).isFalse());
        transactions.forEach(Transaction::decrementReferences);
        spans.forEach(Span::decrementReferences);
        transactions.forEach(t -> assertThat(t.getTraceContext().getId().isEmpty()).isTrue());
        spans.forEach(s -> assertThat(s.getTraceContext().getId().isEmpty()).isTrue());
    }
}
