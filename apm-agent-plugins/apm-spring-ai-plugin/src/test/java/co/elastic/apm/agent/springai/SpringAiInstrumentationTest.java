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
import co.elastic.apm.agent.impl.transaction.SpanImpl;
import co.elastic.apm.agent.impl.transaction.TransactionImpl;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
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

import java.util.Collections;
import java.util.List;

import static co.elastic.apm.agent.testutils.assertions.Assertions.assertThat;

class SpringAiInstrumentationTest extends AbstractInstrumentationTest {

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
            .hasOtelAttribute("gen_ai.system", "openai")
            .hasOtelAttribute("gen_ai.request.model", "gpt-test")
            .hasOtelAttribute("gen_ai.response.model", "gpt-test-result")
            .hasOtelAttribute("gen_ai.usage.input_tokens", 10)
            .hasOtelAttribute("gen_ai.usage.output_tokens", 5)
            .hasOtelAttribute("gen_ai.usage.total_tokens", 15);
        org.assertj.core.api.Assertions.assertThat(span.getOtelAttributes()).doesNotContainKey("gen_ai.input.messages");
    }

    @Test
    void capturesStreamingChatUntilFluxTerminates() {
        TransactionImpl transaction = startTestRootTransaction();
        try {
            ChatOptions options = ChatOptions.builder().model("gpt-stream").build();
            new OpenAiTestChatModel().stream(new Prompt("secret stream prompt", options)).blockLast();
        } finally {
            transaction.deactivate().end();
        }

        assertThat(reporter.getFirstSpan())
            .hasName("chat gpt-stream")
            .hasOtelAttribute("gen_ai.request.stream", true)
            .hasOtelAttribute("gen_ai.usage.total_tokens", 15);
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

        assertThat(reporter.getFirstSpan())
            .hasName("embedding embedding-test")
            .hasAction("embedding")
            .hasOtelAttribute("gen_ai.request.embedding.dimensions", 3)
            .hasOtelAttribute("gen_ai.usage.input_tokens", 4);
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
        org.assertj.core.api.Assertions.assertThat(span.getOtelAttributes())
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
        org.assertj.core.api.Assertions.assertThat(span.getOtelAttributes())
            .doesNotContainKeys("db.query.text", "db.vector.query.content", "db.vector.query.filter");
    }

    static class OpenAiTestChatModel implements ChatModel {
        @Override
        public ChatResponse call(Prompt prompt) {
            return response();
        }

        @Override
        public Flux<ChatResponse> stream(Prompt prompt) {
            return Flux.just(response());
        }

        private static ChatResponse response() {
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
            return Collections.emptyList();
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
