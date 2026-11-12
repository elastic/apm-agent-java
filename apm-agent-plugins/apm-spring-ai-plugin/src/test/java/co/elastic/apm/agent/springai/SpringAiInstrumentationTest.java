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
package co.elastic.apm.agent.springai;

import co.elastic.apm.agent.AbstractInstrumentationTest;
import co.elastic.apm.agent.embeddedotel.EmbeddedSdkManager;
import co.elastic.apm.agent.impl.transaction.SpanImpl;
import co.elastic.apm.agent.impl.transaction.TransactionImpl;
import co.elastic.apm.agent.tracer.configuration.CoreConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingResponseMetadata;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.vectorstore.AbstractVectorStoreBuilder;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.observation.AbstractObservationVectorStore;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationContext;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

import static co.elastic.apm.agent.testutils.assertions.Assertions.assertThat;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.mockito.Mockito.doReturn;

class SpringAiInstrumentationTest extends AbstractInstrumentationTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void capturesChatCallAndSemanticAttributes() {
        TransactionImpl transaction = startTestRootTransaction();
        try {
            ChatOptions options = ChatOptions.builder().model("gpt-test").temperature(0.2).maxTokens(42).build();
            new OpenAiTestChatModel().call(new Prompt("secret prompt", options));
        } finally {
            transaction.deactivate().end();
        }

        SpanImpl span = reporter.getFirstSpan();
        assertThat(span)
            .hasName("chat gpt-test")
            .hasType("app")
            .hasSubType("internal")
            .hasAction("chat")
            .hasOutcome(co.elastic.apm.agent.tracer.Outcome.SUCCESS)
            .hasOtelAttribute("gen_ai.operation.name", "chat")
            .hasOtelAttribute("gen_ai.request.model", "gpt-test")
            .hasOtelAttribute("gen_ai.response.model", "gpt-test-result")
            .hasOtelAttribute("gen_ai.usage.input_tokens", 10)
            .hasOtelAttribute("gen_ai.usage.output_tokens", 5)
            .hasOtelAttribute("gen_ai.usage.total_tokens", 15);
        assertThat(span.getOtelAttributes())
            .doesNotContainKeys("gen_ai.system", "gen_ai.provider.name",
                "gen_ai.input.messages", "gen_ai.output.messages");
    }

    @Test
    void capturesStreamingChatUntilFluxTerminates() throws Exception {
        enableContentCapture();
        TransactionImpl transaction = startTestRootTransaction();
        try {
            ChatOptions options = ChatOptions.builder().model("gpt-stream").build();
            Prompt prompt = new Prompt("secret stream prompt", options);
            new OpenAiTestChatModel().stream(prompt).blockLast();
            new DelayedOpenAiTestChatModel(20).stream(prompt).blockLast();
            new DelayedOpenAiTestChatModel(200).stream(prompt).blockLast();
        } finally {
            transaction.deactivate().end();
        }

        SpanImpl span = reporter.getFirstSpan();
        assertThat(span)
            .hasName("chat gpt-stream")
            .hasOtelAttribute("gen_ai.request.stream", true)
            .hasOtelAttribute("gen_ai.usage.total_tokens", 15)
            .hasOtelAttribute("gen_ai.output.messages",
                "[{\"role\":\"assistant\",\"parts\":[{\"type\":\"text\",\"content\":\"secret response\"}]}]");
        assertThat(
            ((Number) span.getOtelAttributes().get("gen_ai.response.time_to_first_chunk")).doubleValue())
            .isGreaterThanOrEqualTo(0);

        EmbeddedSdkManager sdkManager = tracer.getLifecycleListener(EmbeddedSdkManager.class);
        ((SdkMeterProvider) sdkManager.getMeterProvider().getDelegate()).forceFlush().join(10, SECONDS);
        assertThat(reporter.getBytes()).hasSize(1);
        JsonNode metricSet = OBJECT_MAPPER.readTree(reporter.getBytes().get(0)).get("metricset");
        JsonNode sample = metricSet.get("samples").get(SpringAiMetrics.TIME_TO_FIRST_CHUNK);
        assertThat(sample.get("type").asText()).isEqualTo("histogram");
        long count = 0;
        for (JsonNode bucketCount : sample.get("counts")) {
            count += bucketCount.asLong();
        }
        assertThat(count).isEqualTo(3);
        assertThat(sample.get("values").size()).isGreaterThanOrEqualTo(2);
        assertThat(metricSet.get("tags").toString())
            .contains("gen_ai_operation_name", "chat", "gen_ai_request_model", "gpt-stream")
            .doesNotContain("gen_ai_provider_name");

    }

    @Test
    void doesNotRecordTimeToFirstChunkForEmptyStream() {
        TransactionImpl transaction = startTestRootTransaction();
        try {
            new EmptyOpenAiTestChatModel().stream(new Prompt("empty stream")).blockLast();
        } finally {
            transaction.deactivate().end();
        }

        assertThat(reporter.getFirstSpan().getOtelAttributes())
            .doesNotContainKey("gen_ai.response.time_to_first_chunk");
    }

    @Test
    void capturesEmbeddingCall() {
        TransactionImpl transaction = startTestRootTransaction();
        try {
            EmbeddingOptions options = EmbeddingOptions.builder().model("embedding-test").dimensions(3).build();
            new OpenAiTestEmbeddingModel().call(new EmbeddingRequest(Collections.singletonList("secret"), options));
        } finally {
            transaction.deactivate().end();
        }

        SpanImpl span = reporter.getFirstSpan();
        assertThat(span)
            .hasName("embedding embedding-test")
            .hasAction("embedding")
            .hasOtelAttribute("gen_ai.request.embedding.dimensions", 3)
            .hasOtelAttribute("gen_ai.usage.input_tokens", 4);
        assertThat(span.getOtelAttributes())
            .doesNotContainKey("spring.ai.embedding.request.inputs");
    }

    @Test
    void capturesToolDefinitionButNotArgumentsOrResult() {
        TransactionImpl transaction = startTestRootTransaction();
        try {
            new TestToolCallback().call("{\"password\":\"secret\"}");
        } finally {
            transaction.deactivate().end();
        }

        SpanImpl span = reporter.getFirstSpan();
        assertThat(span)
            .hasName("execute_tool weather")
            .hasType("app")
            .hasSubType("internal")
            .hasAction("execute_tool")
            .hasOtelAttribute("spring.ai.tool.definition.name", "weather");
        assertThat(span.getOtelAttributes())
            .doesNotContainKeys("spring.ai.tool.call.arguments", "spring.ai.tool.call.result");
    }

    @Test
    void capturesVectorStoreQueryWithoutQueryText() {
        TestVectorStore vectorStore = new TestVectorStore(new OpenAiTestEmbeddingModel());

        TransactionImpl transaction = startTestRootTransaction();
        try {
            vectorStore.similaritySearch(SearchRequest.builder()
                .query("secret search query")
                .topK(3)
                .similarityThreshold(0.5)
                .build());
        } finally {
            transaction.deactivate().end();
        }

        SpanImpl span = reporter.getFirstSpan();
        assertThat(span)
            .hasName("simple query")
            .hasType("db")
            .hasSubType("unknown")
            .hasAction("query")
            .hasOtelAttribute("db.system", "simple")
            .hasOtelAttribute("db.operation.name", "query")
            .hasOtelAttribute("db.vector.query.top_k", 3)
            .hasOtelAttribute("db.vector.query.similarity_threshold", 0.5);
        assertThat(span.getOtelAttributes())
            .doesNotContainKeys("db.query.text", "db.vector.query.content", "db.vector.query.filter",
                "db.vector.query.response.documents");
    }

    @Test
    void capturesChatContentWhenEnabled() {
        enableContentCapture();
        TransactionImpl transaction = startTestRootTransaction();
        try {
            new OpenAiTestChatModel().call(new Prompt("secret prompt"));
        } finally {
            transaction.deactivate().end();
        }

        assertThat(reporter.getFirstSpan().getOtelAttributes())
            .extractingByKeys("gen_ai.input.messages", "gen_ai.output.messages")
            .allSatisfy(value -> assertThat(value.toString()).contains("secret"));
    }

    @Test
    void capturesToolCallsAndResponsesInChatMessages() throws Exception {
        enableContentCapture();
        AssistantMessage toolCall = AssistantMessage.builder()
            .content("")
            .toolCalls(Collections.singletonList(new AssistantMessage.ToolCall(
                "call-1", "function", "echo", "{\"message\":\"Hello from Spring AI\"}")))
            .build();
        ToolResponseMessage toolResponse = ToolResponseMessage.builder()
            .responses(Collections.singletonList(new ToolResponseMessage.ToolResponse(
                "call-1", "echo", "[{\"text\":\"Hello from Spring AI\"}]")))
            .build();
        Prompt prompt = new Prompt(List.of(
            new SystemMessage("Call the echo tool"),
            new UserMessage("Echo this message"),
            toolCall,
            toolResponse
        ));

        TransactionImpl transaction = startTestRootTransaction();
        try {
            new OpenAiTestChatModel().call(prompt);
        } finally {
            transaction.deactivate().end();
        }

        String messages = reporter.getFirstSpan().getOtelAttributes().get("gen_ai.input.messages").toString();
        JsonNode parsed = OBJECT_MAPPER.readTree(messages);
        assertThat(parsed.get(2).get("role").asText()).isEqualTo("assistant");
        assertThat(parsed.get(2).get("parts").get(0).get("type").asText())
            .isEqualTo("tool_call");
        assertThat(parsed.get(2).get("parts").get(0).get("name").asText())
            .isEqualTo("echo");
        assertThat(parsed.get(2).get("parts").get(0).get("arguments").asText())
            .contains("Hello from Spring AI");
        assertThat(parsed.get(3).get("role").asText()).isEqualTo("tool");
        assertThat(parsed.get(3).get("parts").get(0).get("type").asText())
            .isEqualTo("tool_call_response");
        assertThat(parsed.get(3).get("parts").get(0).get("response").asText())
            .contains("Hello from Spring AI");
    }

    @Test
    void capturesEmbeddingAndToolContentWhenEnabled() {
        enableContentCapture();
        TransactionImpl transaction = startTestRootTransaction();
        try {
            new OpenAiTestEmbeddingModel().call(new EmbeddingRequest(
                Collections.singletonList("secret embedding document"), EmbeddingOptions.builder().build()));
        } finally {
            transaction.deactivate().end();
        }
        assertThat(reporter.getFirstSpan())
            .hasOtelAttribute("spring.ai.embedding.request.inputs", "[\"secret embedding document\"]");

        reporter.reset();
        transaction = startTestRootTransaction();
        try {
            new TestToolCallback().call("{\"password\":\"secret\"}");
        } finally {
            transaction.deactivate().end();
        }
        assertThat(reporter.getFirstSpan())
            .hasOtelAttribute("spring.ai.tool.call.arguments", "{\"password\":\"secret\"}")
            .hasOtelAttribute("spring.ai.tool.call.result", "secret result");
    }

    @Test
    void capturesVectorDocumentsAndQueryContentWhenEnabled() {
        enableContentCapture();
        TestVectorStore vectorStore = new TestVectorStore(new OpenAiTestEmbeddingModel());
        TransactionImpl transaction = startTestRootTransaction();
        try {
            vectorStore.add(Collections.singletonList(new Document("secret stored document")));
        } finally {
            transaction.deactivate().end();
        }
        assertThat(
            reporter.getFirstSpan().getOtelAttributes().get("db.vector.documents").toString())
            .contains("secret stored document");

        reporter.reset();
        transaction = startTestRootTransaction();
        try {
            vectorStore.similaritySearch(SearchRequest.builder()
                .query("secret search query")
                .filterExpression("category == 'secret'")
                .build());
        } finally {
            transaction.deactivate().end();
        }
        assertThat(reporter.getFirstSpan())
            .hasOtelAttribute("db.vector.query.content", "secret search query");
        assertThat(
            reporter.getFirstSpan().getOtelAttributes())
            .hasEntrySatisfying("db.vector.query.filter",
                value -> assertThat(value.toString()).contains("secret"))
            .hasEntrySatisfying("db.vector.query.response.documents",
                value -> assertThat(value.toString()).contains("secret result document"));
    }

    @Test
    void truncatesCapturedContentAtLongFieldLimit() {
        enableContentCapture();
        doReturn(6).when(tracer.getConfig(CoreConfiguration.class)).getLongFieldMaxLength();
        TransactionImpl transaction = startTestRootTransaction();
        try {
            new TestToolCallback().call("123456789");
        } finally {
            transaction.deactivate().end();
        }

        assertThat(reporter.getFirstSpan())
            .hasOtelAttribute("spring.ai.tool.call.arguments", "123456")
            .hasOtelAttribute("spring.ai.tool.call.result", "secret");
    }

    private static void enableContentCapture() {
        doReturn(true).when(config.getConfig(SpringAiConfiguration.class)).isCaptureAiContent();
    }

    static class OpenAiTestChatModel implements ChatModel {
        @Override
        public ChatResponse call(Prompt prompt) {
            return response();
        }

        @Override
        public Flux<ChatResponse> stream(Prompt prompt) {
            return Flux.just(
                new ChatResponse(Collections.singletonList(
                    new Generation(new AssistantMessage("secret ")))),
                new ChatResponse(Collections.singletonList(
                    new Generation(new AssistantMessage("response")))),
                new ChatResponse(Collections.emptyList(), response().getMetadata())
            );
        }

        static ChatResponse response() {
            Generation generation = new Generation(new AssistantMessage("secret response"),
                ChatGenerationMetadata.builder().finishReason("stop").build());
            ChatResponseMetadata metadata = ChatResponseMetadata.builder()
                .id("response-id")
                .model("gpt-test-result")
                .usage(new DefaultUsage(10, 5, 15))
                .build();
            return new ChatResponse(Collections.singletonList(generation), metadata);
        }
    }

    static class DelayedOpenAiTestChatModel extends OpenAiTestChatModel {
        private final long delayMillis;

        DelayedOpenAiTestChatModel(long delayMillis) {
            this.delayMillis = delayMillis;
        }

        @Override
        public Flux<ChatResponse> stream(Prompt prompt) {
            return Flux.just(response()).delaySubscription(Duration.ofMillis(delayMillis));
        }
    }

    static class EmptyOpenAiTestChatModel extends OpenAiTestChatModel {
        @Override
        public Flux<ChatResponse> stream(Prompt prompt) {
            return Flux.empty();
        }
    }

    static class OpenAiTestEmbeddingModel implements EmbeddingModel {
        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            return new EmbeddingResponse(
                Collections.singletonList(new Embedding(new float[]{1, 2, 3}, 0)),
                new EmbeddingResponseMetadata("embedding-result", new DefaultUsage(4, 0, 4)));
        }

        @Override
        public float[] embed(Document document) {
            return new float[]{1, 2, 3};
        }
    }

    static class TestVectorStore extends AbstractObservationVectorStore {

        TestVectorStore(EmbeddingModel embeddingModel) {
            super(new TestVectorStoreBuilder(embeddingModel));
        }

        @Override
        public void doAdd(List<Document> documents) {
        }

        @Override
        public void doDelete(List<String> ids) {
        }

        @Override
        public List<Document> doSimilaritySearch(SearchRequest request) {
            return Collections.singletonList(new Document("secret result document"));
        }

        @Override
        public VectorStoreObservationContext.Builder createObservationContextBuilder(String operation) {
            return VectorStoreObservationContext.builder("simple", operation)
                .collectionName("test-collection")
                .dimensions(3)
                .similarityMetric("cosine");
        }
    }

    static class TestVectorStoreBuilder extends AbstractVectorStoreBuilder<TestVectorStoreBuilder> {

        TestVectorStoreBuilder(EmbeddingModel embeddingModel) {
            super(embeddingModel);
        }

        @Override
        public TestVectorStore build() {
            return new TestVectorStore(embeddingModel);
        }
    }

    static class TestToolCallback implements ToolCallback {
        @Override
        public ToolDefinition getToolDefinition() {
            return ToolDefinition.builder()
                .name("weather")
                .description("Gets the weather")
                .inputSchema("{\"type\":\"object\"}")
                .build();
        }

        @Override
        public String call(String toolInput) {
            return "secret result";
        }
    }
}
