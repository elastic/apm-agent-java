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
import co.elastic.apm.impl.ElasticApmTracer;
import co.elastic.apm.impl.sampling.ConstantSampler;
import co.elastic.apm.impl.stacktrace.StacktraceConfiguration;
import co.elastic.apm.impl.transaction.Span;
import co.elastic.apm.impl.transaction.TraceContext;
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
import java.net.InetAddress;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class TransactionPayloadJsonSchemaTest {

    private JsonSchema schema;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        schema = JsonSchemaFactory.getInstance().getSchema(getClass().getResourceAsStream("/schema/transactions/payload.json"));
        objectMapper = new ObjectMapper();
    }

    private TransactionPayload createPayloadWithRequiredValues() {
        final TransactionPayload payload = createPayload();
        final Transaction transaction = createTransactionWithRequiredValues();
        payload.getTransactions().add(transaction);
        Span span = new Span(mock(ElasticApmTracer.class));
        span.start(TraceContext.fromParentSpan(), transaction)
            .withType("type")
            .withName("name");
        payload.getSpans().add(span);
        return payload;
    }

    private Transaction createTransactionWithRequiredValues() {
        Transaction t = new Transaction(mock(ElasticApmTracer.class));
        t.start(TraceContext.asRoot(), null, (long) 0, ConstantSampler.of(true));
        t.withType("type");
        t.getContext().getRequest().withMethod("GET");
        t.getContext().getRequest().getUrl().appendToFull("http://localhost:8080/foo/bar");
        return t;
    }

    private TransactionPayload createPayloadWithAllValues() {
        final Transaction transaction = new Transaction(mock(ElasticApmTracer.class));
        TransactionUtils.fillTransaction(transaction);
        final TransactionPayload payload = createPayload();
        payload.getTransactions().add(transaction);
        payload.getSpans().addAll(TransactionUtils.getSpans(transaction));
        return payload;
    }

    private TransactionPayload createPayload() {
        Service service = new Service().withAgent(new Agent("name", "version")).withName("name");
        SystemInfo system = SystemInfo.create();
        final ProcessInfo processInfo = new ProcessInfo("title");
        processInfo.getArgv().add("test");
        return new TransactionPayload(processInfo, service, system);
    }

    @Test
    void testJsonSchemaDslJsonEmptyValues() throws IOException {
        final TransactionPayload payload = createPayload();
        payload.getTransactions().add(new Transaction(mock(ElasticApmTracer.class)));
        final String content = new DslJsonSerializer(mock(StacktraceConfiguration.class)).toJsonString(payload);
        System.out.println(content);
        objectMapper.readTree(content);
    }

    @Test
    void testSystemInfo() throws IOException {
        String arc = System.getProperty("os.arch");
        String platform = System.getProperty("os.name");
        String hostname = SystemInfo.getNameOfLocalHost();
        TransactionPayload payload = createPayload();
        DslJsonSerializer serializer = new DslJsonSerializer(mock(StacktraceConfiguration.class));
        final String content = serializer.toJsonString(payload);
        System.out.println(content);
        JsonNode node = objectMapper.readTree(content);
        JsonNode system = node.get("system");
        assertThat(arc).isEqualTo(system.get("architecture").asText());
        assertThat(hostname).isEqualTo(system.get("hostname").asText());
        assertThat(platform).isEqualTo(system.get("platform").asText());
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
        validateJsonStructure(createPayloadWithAllValues());
    }

    private void validateJsonStructure(TransactionPayload payload) throws IOException {
        JsonNode serializedSpans = getSerializedSpans(payload);
        validateDbSpanSchema(serializedSpans, true);
        validateHttpSpanSchema(serializedSpans);

        for (Span span : payload.getSpans()) {
            if (span.getType() != null && span.getType().equals("db.postgresql.query")) {
                span.getContext().getTags().clear();
                validateDbSpanSchema(getSerializedSpans(payload), false);
                break;
            }
        }
    }

    private JsonNode getSerializedSpans(TransactionPayload payload) throws IOException {
        DslJsonSerializer serializer = new DslJsonSerializer(mock(StacktraceConfiguration.class));
        final String content = serializer.toJsonString(payload);
        System.out.println(content);
        JsonNode node = objectMapper.readTree(content);

        assertThat(node.get("transactions").get(0).get("spans")).isNull();
        return node.get("spans");
    }

    private void validateDbSpanSchema(JsonNode serializedSpans, boolean shouldContainTags) throws IOException {
        boolean contextOfDbSpanFound = false;
        for (JsonNode child: serializedSpans) {
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

    private void validateHttpSpanSchema(JsonNode serializedSpans)  {
        boolean contextOfHttpSpanFound = false;
        for (JsonNode child: serializedSpans) {
            if(child.get("type").textValue().startsWith("ext.http.")) {
                assertThat(child.get("name").textValue()).isEqualTo("GET test.elastic.co");
                JsonNode context = child.get("context");
                contextOfHttpSpanFound = true;
                JsonNode http = context.get("http");
                assertThat(http).isNotNull();
                assertThat(http.get("url").textValue()).isEqualTo("http://test.elastic.co/test-service");
                assertThat(http.get("method").textValue()).isEqualTo("POST");
                assertThat(http.get("status_code").intValue()).isEqualTo(201);
            }
        }
        assertThat(contextOfHttpSpanFound).isTrue();
    }

    private void validate(TransactionPayload payload) throws IOException {
        DslJsonSerializer serializer = new DslJsonSerializer(mock(StacktraceConfiguration.class));
        final String contentInDistributedTracingFormat = serializer.toJsonString(payload);
        System.out.println(contentInDistributedTracingFormat);
        Set<ValidationMessage> distributedTracingFormatErrors = schema.validate(objectMapper.readTree(contentInDistributedTracingFormat));
        assertThat(distributedTracingFormatErrors).isEmpty();
    }
}
