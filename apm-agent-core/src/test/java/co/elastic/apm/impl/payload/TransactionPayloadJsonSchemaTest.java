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
import co.elastic.apm.impl.transaction.Span;
import co.elastic.apm.impl.transaction.Transaction;
import co.elastic.apm.report.serialize.DslJsonSerializer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.ValidationMessage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
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
        Transaction t = new Transaction(null);
        t.start(mock(ElasticApmTracer.class), null, 0, ConstantSampler.of(true));
        t.setType("type");
        t.getContext().getRequest().withMethod("GET");
        t.getContext().getRequest().getUrl().appendToFull("http://localhost:8080/foo/bar");
        Span s = new Span();
        s.start(mock(ElasticApmTracer.class), t, null, 0, false)
            .withType("type")
            .withName("name");
        t.addSpan(s);
        return t;
    }

    private TransactionPayload createPayloadWithAllValues() {
        final Transaction transaction = new Transaction(null);
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
        payload.getTransactions().add(new Transaction(null));
        final String content = new DslJsonSerializer(coreConfiguration).toJsonString(payload);
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

    private void validate(TransactionPayload payload) throws IOException {
        when(coreConfiguration.isDistributedTracingEnabled()).thenReturn(false);
        DslJsonSerializer serializer = new DslJsonSerializer(coreConfiguration);

        final String content = serializer.toJsonString(payload);
        Set<ValidationMessage> errors = schema.validate(objectMapper.readTree(content));
        assertThat(errors).isEmpty();

        when(coreConfiguration.isDistributedTracingEnabled()).thenReturn(true);
        serializer = new DslJsonSerializer(coreConfiguration);
        transformForDistributedTracing(payload);
        final String contentInDistributedTracingFormat = serializer.toJsonString(payload);
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
