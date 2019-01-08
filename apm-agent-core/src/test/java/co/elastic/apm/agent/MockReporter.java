/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018-2019 Elastic and contributors
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package co.elastic.apm.agent;

import co.elastic.apm.agent.impl.error.ErrorCapture;
import co.elastic.apm.agent.impl.error.ErrorPayload;
import co.elastic.apm.agent.impl.payload.PayloadUtils;
import co.elastic.apm.agent.impl.payload.TransactionPayload;
import co.elastic.apm.agent.impl.stacktrace.StacktraceConfiguration;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.metrics.MetricRegistry;
import co.elastic.apm.agent.report.Reporter;
import co.elastic.apm.agent.report.serialize.DslJsonSerializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.ValidationMessage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

public class MockReporter implements Reporter {
    private static final JsonSchema transactionSchema;
    private static final JsonSchema errorSchema;
    private static final JsonSchema spanSchema;
    private static final DslJsonSerializer dslJsonSerializer;
    private final List<Transaction> transactions = new ArrayList<>();
    private final List<Span> spans = new ArrayList<>();
    private final List<ErrorCapture> errors = new ArrayList<>();
    private final ObjectMapper objectMapper;
    private final boolean verifyJsonSchema;

    static {
        transactionSchema = getSchema("/schema/transactions/transaction.json");
        spanSchema = getSchema("/schema/transactions/span.json");
        errorSchema = getSchema("/schema/errors/error.json");
        dslJsonSerializer = new DslJsonSerializer(mock(StacktraceConfiguration.class));
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

    @Override
    public void report(Transaction transaction) {
        verifyTransactionSchema(asJson(dslJsonSerializer.toJsonString(transaction)));
        transactions.add(transaction);
    }

    @Override
    public void report(Span span) {
        verifySpanSchema(asJson(dslJsonSerializer.toJsonString(span)));
        spans.add(span);
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
            assertThat(errors).isEmpty();
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

    public List<Transaction> getTransactions() {
        return Collections.unmodifiableList(transactions);
    }

    public Transaction getFirstTransaction() {
        return transactions.iterator().next();
    }

    public Transaction getFirstTransaction(long timeoutMs) throws InterruptedException {
        final long end = System.currentTimeMillis() + timeoutMs;
        do {
            if (!transactions.isEmpty()) {
                return getFirstTransaction();
            }
            Thread.sleep(1);
        } while (System.currentTimeMillis() < end);
        return getFirstTransaction();
    }

    @Override
    public void report(ErrorCapture error) {
        verifyErrorSchema(asJson(dslJsonSerializer.toJsonString(error)));
        errors.add(error);
    }

    @Override
    public void scheduleMetricReporting(MetricRegistry metricRegistry, long intervalMs) {
        // noop
    }

    public Span getFirstSpan() {
        return spans.get(0);
    }

    public List<Span> getSpans() {
        return spans;
    }

    public List<ErrorCapture> getErrors() {
        return Collections.unmodifiableList(errors);
    }

    public ErrorCapture getFirstError() {
        return errors.iterator().next();
    }

    public String generateTransactionPayloadJson() {
        TransactionPayload payload = PayloadUtils.createTransactionPayload();
        payload.getTransactions().addAll(transactions);
        payload.getSpans().addAll(spans);
        return dslJsonSerializer.toJsonString(payload);
    }

    public String generateErrorPayloadJson() {
        ErrorPayload errorPayload = PayloadUtils.createErrorPayload();
        errorPayload.getErrors().addAll(errors);
        return dslJsonSerializer.toJsonString(errorPayload);
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

    }

    public void reset() {
        transactions.clear();
        errors.clear();
        spans.clear();
    }
}
