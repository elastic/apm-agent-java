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
package co.elastic.apm.springai.testapp;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingResponseMetadata;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

@Configuration
public class TestDataConfiguration {

    @Bean
    ChatModel deterministicChatModel() {
        return new DeterministicChatModel();
    }

    @Bean
    ChatClient.Builder chatClientBuilder(ChatModel chatModel) {
        return ChatClient.builder(chatModel);
    }

    @Bean
    EmbeddingModel deterministicEmbeddingModel() {
        return new DeterministicEmbeddingModel();
    }

    @Bean
    VectorStore testVectorStore(EmbeddingModel embeddingModel) {
        return SimpleVectorStore.builder(embeddingModel).build();
    }

    @Bean
    ToolCallback echoToolCallback() {
        return new EchoToolCallback();
    }

    static final class DeterministicChatModel implements ChatModel {

        static final String MODEL_NAME = "deterministic-chat";
        private volatile String toolName = "echo";

        @Override
        public ChatResponse call(Prompt prompt) {
            OptionalToolResponse toolResponse = findToolResponse(prompt);
            if (toolResponse.present()) {
                return response("I called the echo tool. Tool result: " + toolResponse.response());
            }
            String userText = prompt.getUserMessage().getText();
            if (userText.startsWith("Call the echo tool with this exact message")) {
                String requestedMessage = userText.substring(userText.lastIndexOf(':') + 1).trim();
                String arguments = "{\"message\":\"" + jsonEscape(requestedMessage) + "\",\"repeat\":1}";
                AssistantMessage output = AssistantMessage.builder()
                    .content("")
                    .toolCalls(List.of(new AssistantMessage.ToolCall("integration-call-1", "function", toolName, arguments)))
                    .build();
                return new ChatResponse(Collections.singletonList(new Generation(output)), responseMetadata());
            }
            String promptText = prompt.getInstructions().get(0).getText();
            return response("Echo: " + promptText);
        }

        void setToolName(String toolName) {
            this.toolName = toolName;
        }

        @Override
        public Flux<ChatResponse> stream(Prompt prompt) {
            String promptText = prompt.getInstructions().get(0).getText();
            ChatResponse finalResponse = response("Echo: " + promptText);
            return Flux.just(
                    contentChunk("Echo: "),
                    contentChunk(promptText),
                    new ChatResponse(Collections.emptyList(), finalResponse.getMetadata())
                )
                .delayElements(Duration.ofMillis(20));
        }

        private static ChatResponse contentChunk(String content) {
            Generation generation = new Generation(new AssistantMessage(content),
                ChatGenerationMetadata.builder().finishReason("stop").build());
            return new ChatResponse(Collections.singletonList(generation));
        }

        private static ChatResponse response(String content) {
            Generation generation = new Generation(new AssistantMessage(content),
                ChatGenerationMetadata.builder().finishReason("stop").build());
            return new ChatResponse(Collections.singletonList(generation), responseMetadata());
        }

        private static ChatResponseMetadata responseMetadata() {
            return ChatResponseMetadata.builder()
                .id("deterministic-response")
                .model(MODEL_NAME)
                .usage(new DefaultUsage(3, 2, 5))
                .build();
        }

        private static OptionalToolResponse findToolResponse(Prompt prompt) {
            return prompt.getInstructions().stream()
                .filter(ToolResponseMessage.class::isInstance)
                .map(ToolResponseMessage.class::cast)
                .flatMap(message -> message.getResponses().stream())
                .findFirst()
                .map(response -> new OptionalToolResponse(true, response.responseData()))
                .orElseGet(() -> new OptionalToolResponse(false, ""));
        }

        private static String jsonEscape(String value) {
            return value.replace("\\", "\\\\").replace("\"", "\\\"");
        }

        private record OptionalToolResponse(boolean present, String response) {
        }
    }

    static final class DeterministicEmbeddingModel implements EmbeddingModel {

        static final String MODEL_NAME = "deterministic-embedding";
        static final int DEFAULT_DIMENSIONS = 8;

        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            int dimensions = request.getOptions() == null || request.getOptions().getDimensions() == null
                ? DEFAULT_DIMENSIONS
                : request.getOptions().getDimensions();
            List<Embedding> embeddings = new ArrayList<>();
            int tokenCount = 0;
            for (int index = 0; index < request.getInstructions().size(); index++) {
                String input = request.getInstructions().get(index);
                embeddings.add(new Embedding(toVector(input, dimensions), index));
                tokenCount += tokens(input).length;
            }
            String model = request.getOptions() == null || request.getOptions().getModel() == null
                ? MODEL_NAME
                : request.getOptions().getModel();
            return new EmbeddingResponse(embeddings,
                new EmbeddingResponseMetadata(model, new DefaultUsage(tokenCount, 0, tokenCount)));
        }

        @Override
        public float[] embed(Document document) {
            return embed(getEmbeddingContent(document));
        }

        @Override
        public int dimensions() {
            return DEFAULT_DIMENSIONS;
        }

        private static float[] toVector(String input, int dimensions) {
            float[] vector = new float[dimensions];
            for (String token : tokens(input)) {
                int hash = token.hashCode();
                vector[Math.floorMod(hash, dimensions)] += (hash & 1) == 0 ? 1.0f : -1.0f;
            }
            double magnitude = 0;
            for (float value : vector) {
                magnitude += value * value;
            }
            if (magnitude == 0) {
                vector[0] = 1.0f;
                return vector;
            }
            float divisor = (float) Math.sqrt(magnitude);
            for (int index = 0; index < vector.length; index++) {
                vector[index] /= divisor;
            }
            return vector;
        }

        private static String[] tokens(String input) {
            if (input == null || input.isBlank()) {
                return new String[0];
            }
            String normalized = input.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
            return normalized.isEmpty() ? new String[0] : normalized.split("\\s+");
        }
    }

    static final class EchoToolCallback implements ToolCallback {

        @Override
        public ToolDefinition getToolDefinition() {
            return ToolDefinition.builder()
                .name("echo")
                .description("Returns the supplied message")
                .inputSchema("{\"type\":\"object\",\"properties\":{\"message\":{\"type\":\"string\"}}}")
                .build();
        }

        @Override
        public String call(String toolInput) {
            return "Echo result: " + toolInput;
        }
    }
}
