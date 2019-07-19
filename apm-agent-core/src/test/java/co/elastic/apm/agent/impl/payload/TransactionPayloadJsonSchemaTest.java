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
package co.elastic.apm.agent.impl.payload;

import co.elastic.apm.agent.TransactionUtils;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.sampling.ConstantSampler;
import co.elastic.apm.agent.impl.stacktrace.StacktraceConfiguration;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.TraceContext;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.report.serialize.DslJsonSerializer;
import co.elastic.apm.agent.util.IOUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.ValidationMessage;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class TransactionPayloadJsonSchemaTest {

    private JsonSchema schema;
    private ObjectMapper objectMapper;
    private DslJsonSerializer serializer;

    @BeforeEach
    void setUp() {
        schema = JsonSchemaFactory.getInstance().getSchema(getClass().getResourceAsStream("/schema/transactions/payload.json"));
        objectMapper = new ObjectMapper();
        serializer = new DslJsonSerializer(mock(StacktraceConfiguration.class));
    }

    private TransactionPayload createPayloadWithRequiredValues() {
        final TransactionPayload payload = createPayload();
        final Transaction transaction = createTransactionWithRequiredValues();
        payload.getTransactions().add(transaction);
        Span span = new Span(mock(ElasticApmTracer.class));
        span.start(TraceContext.fromParent(), transaction, -1, false)
            .withType("type")
            .withSubtype("subtype")
            .withAction("action")
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
        return createPayload(SystemInfo.create());
    }

    private TransactionPayload createPayload(SystemInfo system) {
        Service service = new Service().withAgent(new Agent("name", "version")).withName("name");
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
        final String content = serializer.toJsonString(payload);
        System.out.println(content);
        JsonNode node = objectMapper.readTree(content);
        JsonNode system = node.get("system");
        assertThat(arc).isEqualTo(system.get("architecture").asText());
        assertThat(hostname).isEqualTo(system.get("hostname").asText());
        assertThat(platform).isEqualTo(system.get("platform").asText());
    }

    @Test
    void testContainerInfo() throws IOException {
        SystemInfo.Container container = new SystemInfo.Container("containerId");
        String podName = "myPod";
        String nodeName = "myNode";
        String namespace = "/my/namespace";
        String podUID = "podUID";
        SystemInfo.Kubernetes kubernetes = new SystemInfo.Kubernetes(podName, nodeName, namespace, podUID);
        TransactionPayload payload = createPayload(new SystemInfo("x64", "localhost", "platform", container, kubernetes));
        final String content = serializer.toJsonString(payload);
        System.out.println(content);
        JsonNode systemNode = objectMapper.readTree(content).get("system");
        JsonNode containerNode = systemNode.get("container");
        assertThat(containerNode).isNotNull();
        assertThat(containerNode.get("id").textValue()).isEqualTo("containerId");
        JsonNode kubernetesNode = systemNode.get("kubernetes");
        assertThat(kubernetesNode).isNotNull();
        assertThat(kubernetesNode.get("namespace").textValue()).isEqualTo(namespace);
        assertThat(kubernetesNode.get("node").get("name").textValue()).isEqualTo(nodeName);
        assertThat(kubernetesNode.get("pod").get("name").textValue()).isEqualTo(podName);
        assertThat(kubernetesNode.get("pod").get("uid").textValue()).isEqualTo(podUID);
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

    @Test
    void testBodyBuffer() throws IOException {
        final Transaction transaction = createTransactionWithRequiredValues();
        final CharBuffer bodyBuffer = transaction.getContext().getRequest().withBodyBuffer();
        IOUtils.decodeUtf8Bytes("{f".getBytes(StandardCharsets.UTF_8), bodyBuffer);
        IOUtils.decodeUtf8Bytes(new byte[]{0, 0, 'o', 'o', 0}, 2, 2, bodyBuffer);
        IOUtils.decodeUtf8Byte((byte) '}', bodyBuffer);
        bodyBuffer.flip();
        final String content = serializer.toJsonString(transaction);
        System.out.println(content);
        final JsonNode transactionJson = objectMapper.readTree(content);
        assertThat(transactionJson.get("context").get("request").get("body").textValue()).isEqualTo("{foo}");

        transaction.resetState();
        assertThat((Object) transaction.getContext().getRequest().getBodyBuffer()).isNull();
    }

    @Test
    void testBodyBufferCopy() throws IOException {
        final Transaction transaction = createTransactionWithRequiredValues();
        final CharBuffer bodyBuffer = transaction.getContext().getRequest().withBodyBuffer();
        IOUtils.decodeUtf8Bytes("{foo}".getBytes(StandardCharsets.UTF_8), bodyBuffer);
        bodyBuffer.flip();

        Transaction copy = createTransactionWithRequiredValues();
        copy.getContext().copyFrom(transaction.getContext());

        assertThat(objectMapper.readTree(serializer.toJsonString(copy)).get("context"))
            .isEqualTo(objectMapper.readTree(serializer.toJsonString(transaction)).get("context"));
    }

    @Test
    void testCustomContext() throws Exception {
        final Transaction transaction = createTransactionWithRequiredValues();
        transaction.addCustomContext("string", "foo");
        final String longString = RandomStringUtils.randomAlphanumeric(10001);
        transaction.addCustomContext("long_string", longString);
        transaction.addCustomContext("number", 42);
        transaction.addCustomContext("boolean", true);

        final JsonNode customContext = objectMapper.readTree(serializer.toJsonString(transaction)).get("context").get("custom");
        assertThat(customContext.get("string").textValue()).isEqualTo("foo");
        assertThat(customContext.get("long_string").textValue()).isEqualTo(longString.substring(0, 9999) + "…");
        assertThat(customContext.get("number").intValue()).isEqualTo(42);
        assertThat(customContext.get("boolean").booleanValue()).isEqualTo(true);
    }

    private void validateJsonStructure(TransactionPayload payload) throws IOException {
        JsonNode serializedSpans = getSerializedSpans(payload);
        validateDbSpanSchema(serializedSpans, true);
        validateHttpSpanSchema(serializedSpans);

        for (Span span : payload.getSpans()) {
            if (span.getType() != null && span.getType().equals("db")) {
                span.getContext().clearLabels();
                validateDbSpanSchema(getSerializedSpans(payload), false);
                break;
            }
        }
    }

    private JsonNode getSerializedSpans(TransactionPayload payload) throws IOException {
        final String content = serializer.toJsonString(payload);
        System.out.println(content);
        JsonNode node = objectMapper.readTree(content);

        assertThat(node.get("transactions").get(0).get("spans")).isNull();
        return node.get("spans");
    }

    private void validateDbSpanSchema(JsonNode serializedSpans, boolean shouldContainTags) {
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
                assertThat(db.get("link").textValue()).isEqualTo("DB_LINK");
                JsonNode tags = context.get("tags");
                if (shouldContainTags) {
                    assertThat(tags).isNotNull();
                    assertThat(tags).hasSize(2);
                    assertThat(tags.get("monitored_by").textValue()).isEqualTo("ACME");
                    assertThat(tags.get("framework").textValue()).isEqualTo("some-framework");
                }
                else {
                    assertThat(tags).isNullOrEmpty();
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
        final String contentInDistributedTracingFormat = serializer.toJsonString(payload);
        System.out.println(contentInDistributedTracingFormat);
        Set<ValidationMessage> distributedTracingFormatErrors = schema.validate(objectMapper.readTree(contentInDistributedTracingFormat));
        assertThat(distributedTracingFormatErrors).isEmpty();
    }
}
