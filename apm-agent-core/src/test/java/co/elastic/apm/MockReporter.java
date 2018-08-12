/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 Elastic and contributors
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
package co.elastic.apm;

import co.elastic.apm.configuration.CoreConfiguration;
import co.elastic.apm.impl.error.ErrorCapture;
import co.elastic.apm.impl.stacktrace.StacktraceConfiguration;
import co.elastic.apm.impl.transaction.Span;
import co.elastic.apm.impl.transaction.Transaction;
import co.elastic.apm.report.Reporter;
import co.elastic.apm.report.serialize.DslJsonSerializer;
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
    private final List<Transaction> transactions = new ArrayList<>();
    private final List<Span> spans = new ArrayList<>();
    private final List<ErrorCapture> errors = new ArrayList<>();
    private final JsonSchema transactionSchema;
    private final JsonSchema errorSchema;
    private final JsonSchema spanSchema;
    private final DslJsonSerializer dslJsonSerializer;
    private final ObjectMapper objectMapper;
    private final boolean verifyJsonSchema;

    public MockReporter() {
        this(true);
    }

    public MockReporter(boolean verifyJsonSchema) {
        this.verifyJsonSchema = verifyJsonSchema;
        transactionSchema = getSchema("/schema/transactions/transaction.json");
        spanSchema = getSchema("/schema/transactions/span.json");
        errorSchema = getSchema("/schema/errors/error.json");
        final CoreConfiguration config = new CoreConfiguration();
        dslJsonSerializer = new DslJsonSerializer(config.isDistributedTracingEnabled(), mock(StacktraceConfiguration.class));
        objectMapper = new ObjectMapper();
    }

    private JsonSchema getSchema(String resource) {
        return JsonSchemaFactory.getInstance().getSchema(getClass().getResourceAsStream(resource));
    }

    @Override
    public void report(Transaction transaction) {
        verifyJsonSchema(transaction);
        transactions.add(transaction);
        spans.addAll(transaction.getSpans());
    }

    @Override
    public void report(Span span) {
        verifyJsonSchema(span);
        spans.add(span);
    }

    private void verifyJsonSchema(Transaction transaction) {
        final String content = dslJsonSerializer.toJsonString(transaction);
        verifyJsonSchema(content, transactionSchema);
    }

    private void verifyJsonSchema(Span span) {
        final String content = dslJsonSerializer.toJsonString(span);
        verifyJsonSchema(content, spanSchema);
    }

    private void verifyJsonSchema(ErrorCapture error) {
        final String content = dslJsonSerializer.toJsonString(error);
        verifyJsonSchema(content, errorSchema);
    }

    private void verifyJsonSchema(String jsonContent, JsonSchema transactionSchema) {
        try {
            final JsonNode node = objectMapper.readTree(jsonContent);
            if (verifyJsonSchema) {
                Set<ValidationMessage> errors = transactionSchema.validate(node);
                assertThat(errors).isEmpty();
            }
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
        verifyJsonSchema(error);
        errors.add(error);
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
