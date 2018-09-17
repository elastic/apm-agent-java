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
package co.elastic.apm.impl.payload;

import co.elastic.apm.TransactionUtils;
import co.elastic.apm.configuration.CoreConfiguration;
import co.elastic.apm.impl.ElasticApmTracer;
import co.elastic.apm.impl.sampling.ConstantSampler;
import co.elastic.apm.impl.stacktrace.StacktraceConfiguration;
import co.elastic.apm.impl.transaction.Span;
import co.elastic.apm.impl.transaction.Transaction;
import co.elastic.apm.report.serialize.DslJsonSerializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.ValidationMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

class TransactionPayloadJsonSchemaTest {

    private JsonSchema schema;
    private ObjectMapper objectMapper;
    private CoreConfiguration coreConfiguration;

    @BeforeEach
    void setUp() {
        schema = JsonSchemaFactory.getInstance().getSchema(getClass().getResourceAsStream("/schema/transactions/payload.json"));
        coreConfiguration = spy(new CoreConfiguration());

        objectMapper = new ObjectMapper();
    }

    private TransactionPayload createPayloadWithRequiredValues() {
        final TransactionPayload payload = createPayload();
        payload.getTransactions().add(createTransactionWithRequiredValues());
        transformForDistributedTracing(payload);
        return payload;
    }

    private Transaction createTransactionWithRequiredValues() {
        Transaction t = new Transaction(mock(ElasticApmTracer.class));
        t.start(null, 0, ConstantSampler.of(true));
        t.withType("type");
        t.getContext().getRequest().withMethod("GET");
        t.getContext().getRequest().getUrl().appendToFull("http://localhost:8080/foo/bar");
        Span s = new Span(mock(ElasticApmTracer.class));
        s.start(t, null, 0, false)
            .withType("type")
            .withName("name");
        t.addSpan(s);
        return t;
    }

    private TransactionPayload createPayloadWithAllValues() {
        final Transaction transaction = new Transaction(mock(ElasticApmTracer.class));
        TransactionUtils.fillTransaction(transaction);
        final TransactionPayload payload = createPayload();
        payload.getTransactions().add(transaction);
        transformForDistributedTracing(payload);
        return payload;
    }

    private TransactionPayload createPayload() {
        Service service = new Service().withAgent(new Agent("name", "version")).withName("name");
        SystemInfo system = new SystemInfo("", "", "");
        final ProcessInfo processInfo = new ProcessInfo("title");
        processInfo.getArgv().add("test");
        return new TransactionPayload(processInfo, service, system);
    }

    @Test
    void testJsonSchemaDslJsonEmptyValues() throws IOException {
        final TransactionPayload payload = createPayload();
        payload.getTransactions().add(new Transaction(mock(ElasticApmTracer.class)));
        final String content = new DslJsonSerializer(coreConfiguration.isDistributedTracingEnabled(), mock(StacktraceConfiguration.class)).toJsonString(payload);
        System.out.println(content);
        objectMapper.readTree(content);
    }

    @Test
    void testJsonSchemaDslJsonMinimalValues() throws IOException {
        validate(createPayloadWithRequiredValues());
    }

    @Test
    void testJsonSchemaDslJsonAllValues() throws IOException {
        validate(createPayloadWithAllValues());
    }

    @Test
    void testJsonStructure() throws IOException {
        TransactionPayload payload = createPayloadWithAllValues();
        validateJsonStructure(payload, false);

        // create payload again because tags were removed from the former
        payload = createPayloadWithAllValues();
        Iterator<Span> transactionSpansIt = payload.getTransactions().get(0).getSpans().iterator();
        List<Span> directSpansList = payload.getSpans();

        while (transactionSpansIt.hasNext()) {
            directSpansList.add(transactionSpansIt.next());
            transactionSpansIt.remove();
        }
        validateJsonStructure(payload, true);
    }

    private void validateJsonStructure(TransactionPayload payload, boolean useIntakeV2) throws IOException {
        when(coreConfiguration.isDistributedTracingEnabled()).thenReturn(false);
        DslJsonSerializer serializer = new DslJsonSerializer(coreConfiguration.isDistributedTracingEnabled(), mock(StacktraceConfiguration.class));

        List<Span> v1Spans = payload.getTransactions().get(0).getSpans();
        List<Span> v2Spans = payload.getSpans();
        List<Span> spansForUse = (useIntakeV2)? v2Spans: v1Spans;
        assertThat((useIntakeV2)? v1Spans: v2Spans).isEmpty();

        validateDbSpanSchema(payload, serializer, true, useIntakeV2);

        for (Span span: spansForUse) {
            if (span.getType() != null && span.getType().equals("db.postgresql.query")) {
                span.getContext().getTags().clear();
                validateDbSpanSchema(payload, serializer, false, useIntakeV2);
                break;
            }
        }
    }

    private void validateDbSpanSchema(TransactionPayload payload, DslJsonSerializer serializer, boolean shouldContainTags, boolean useIntakeV2) throws IOException {
        final String content = serializer.toJsonString(payload);
        JsonNode node = objectMapper.readTree(content);

        JsonNode v1Spans = node.get("transactions").get(0).get("spans");
        JsonNode v2Spans = node.get("spans");
        assertThat((useIntakeV2)? v1Spans: v2Spans).isNull();

        boolean contextOfDbSpanFound = false;
        JsonNode spansForUse = (useIntakeV2)? v2Spans: v1Spans;
        for (JsonNode child: spansForUse) {
            if(child.get("type").textValue().startsWith("db.")) {
                contextOfDbSpanFound = true;
                JsonNode context = child.get("context");
                JsonNode db = context.get("db");
                assertThat(db).isNotNull();
                assertThat(db.get("instance").textValue()).isEqualTo("customers");
                assertThat(db.get("statement").textValue()).isEqualTo("SELECT * FROM product_types WHERE user_id=?");
                assertThat(db.get("type").textValue()).isEqualTo("sql");
                assertThat(db.get("user").textValue()).isEqualTo("readonly_user");
                JsonNode tags = context.get("tags");
                if (shouldContainTags) {
                    assertThat(tags).isNotNull();
                    assertThat(tags).hasSize(2);
                    assertThat(tags.get("monitored_by").textValue()).isEqualTo("ACME");
                    assertThat(tags.get("framework").textValue()).isEqualTo("some-framework");
                }
                else {
                    assertThat(tags).isNull();
                }
            }
        }
        assertThat(contextOfDbSpanFound).isTrue();
    }

    private void validate(TransactionPayload payload) throws IOException {
        when(coreConfiguration.isDistributedTracingEnabled()).thenReturn(false);
        DslJsonSerializer serializer = new DslJsonSerializer(coreConfiguration.isDistributedTracingEnabled(), mock(StacktraceConfiguration.class));

        final String content = serializer.toJsonString(payload);
        System.out.println(content);
        Set<ValidationMessage> errors = schema.validate(objectMapper.readTree(content));
        assertThat(errors).isEmpty();

        when(coreConfiguration.isDistributedTracingEnabled()).thenReturn(true);
        serializer = new DslJsonSerializer(coreConfiguration.isDistributedTracingEnabled(), mock(StacktraceConfiguration.class));
        transformForDistributedTracing(payload);
        final String contentInDistributedTracingFormat = serializer.toJsonString(payload);
        System.out.println(contentInDistributedTracingFormat);
        Set<ValidationMessage> distributedTracingFormatErrors = schema.validate(objectMapper.readTree(contentInDistributedTracingFormat));
        assertThat(distributedTracingFormatErrors).isEmpty();
    }

    private void transformForDistributedTracing(TransactionPayload payload) {
        if (coreConfiguration.isDistributedTracingEnabled()) {
            for (Transaction transaction : payload.getTransactions()) {
                payload.getSpans().addAll(transaction.getSpans());
                transaction.getSpans().clear();
            }
        }
    }
}
