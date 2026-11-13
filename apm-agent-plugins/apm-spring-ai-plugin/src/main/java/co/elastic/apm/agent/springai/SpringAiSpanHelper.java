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

import co.elastic.apm.agent.tracer.GlobalTracer;
import co.elastic.apm.agent.tracer.Outcome;
import co.elastic.apm.agent.tracer.Span;
import co.elastic.apm.agent.tracer.TraceState;
import co.elastic.apm.agent.tracer.Tracer;
import co.elastic.apm.agent.tracer.configuration.CoreConfiguration;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.model.MessageAggregator;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingResponseMetadata;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.observation.AbstractObservationVectorStore;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationContext;
import reactor.core.publisher.Flux;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

final class SpringAiSpanHelper {

    private static final Tracer tracer = GlobalTracer.get();
    private static final SpringAiConfiguration config = tracer.getConfig(SpringAiConfiguration.class);
    private static final CoreConfiguration coreConfig = tracer.getConfig(CoreConfiguration.class);

    private SpringAiSpanHelper() {
    }

    @Nullable
    static Span<?> startModelSpan(String operation, Prompt prompt, boolean streaming) {
        Span<?> span = tracer.currentContext().createSpan();
        if (span == null) {
            return null;
        }

        ChatOptions options = prompt.getOptions();
        String requestModel = options == null ? null : options.getModel();
        span.withName(spanName(operation, requestModel))
            .withType("app")
            .withSubtype("internal")
            .withOtelAttribute("gen_ai.operation.name", operation)
            .withOtelAttribute("gen_ai.request.model", requestModel)
            .withOtelAttribute("gen_ai.request.stream", streaming);

        if (options != null) {
            span.withOtelAttribute("gen_ai.request.frequency_penalty", options.getFrequencyPenalty())
                .withOtelAttribute("gen_ai.request.max_tokens", options.getMaxTokens())
                .withOtelAttribute("gen_ai.request.presence_penalty", options.getPresencePenalty())
                .withOtelAttribute("gen_ai.request.stop_sequences", stringify(options.getStopSequences()))
                .withOtelAttribute("gen_ai.request.temperature", options.getTemperature())
                .withOtelAttribute("gen_ai.request.top_k", options.getTopK())
                .withOtelAttribute("gen_ai.request.top_p", options.getTopP());
        }
        if (config.isCaptureSpringAiContent()) {
            captureStructuredContent(span, "gen_ai.input.messages", serializeMessages(prompt.getInstructions()));
        }
        return span;
    }

    @Nullable
    static Span<?> startEmbeddingSpan(EmbeddingRequest request) {
        EmbeddingOptions options = request.getOptions();
        Span<?> span = tracer.currentContext().createSpan();
        if (span == null) {
            return null;
        }
        String requestModel = options == null ? null : options.getModel();
        span.withName(spanName("embedding", requestModel))
            .withType("app")
            .withSubtype("internal")
            .withOtelAttribute("gen_ai.operation.name", "embedding")
            .withOtelAttribute("gen_ai.request.model", requestModel)
            .withOtelAttribute("gen_ai.request.embedding.dimensions", options == null ? null : options.getDimensions());
        if (config.isCaptureSpringAiContent()) {
            captureStructuredContent(span, "spring.ai.embedding.request.inputs", serializeStrings(request.getInstructions()));
        }
        return span;
    }

    @Nullable
    static Span<?> startVectorStoreSpan(AbstractObservationVectorStore vectorStore, String operation,
                                        @Nullable Object argument) {
        Span<?> span = tracer.currentContext().createExitSpan();
        if (span == null) {
            return null;
        }
        VectorStoreObservationContext.Builder builder = vectorStore.createObservationContextBuilder(operation);
        SearchRequest searchRequest = argument instanceof SearchRequest ? (SearchRequest) argument : null;
        if (searchRequest != null) {
            builder.queryRequest(searchRequest);
        }
        VectorStoreObservationContext context = builder.build();
        String dbSystem = context.getDatabaseSystem();
        boolean hasDbSystem = dbSystem != null && !dbSystem.isEmpty();
        span.withName((hasDbSystem ? dbSystem : "vector_store") + " " + operation)
            .withType("db")
            .withSubtype("unknown")
            .withOtelAttribute("spring.ai.kind", "vector_store")
            .withOtelAttribute("db.operation.name", operation)
            .withOtelAttribute("db.collection.name", context.getCollectionName())
            .withOtelAttribute("db.namespace", context.getNamespace())
            .withOtelAttribute("db.search.similarity_metric", context.getSimilarityMetric())
            .withOtelAttribute("db.vector.dimension_count", context.getDimensions())
            .withOtelAttribute("db.vector.field_name", context.getFieldName());
        if (searchRequest != null) {
            span.withOtelAttribute("db.vector.query.top_k", searchRequest.getTopK())
                .withOtelAttribute("db.vector.query.similarity_threshold", searchRequest.getSimilarityThreshold());
            captureContent(span, "db.vector.query.content", searchRequest.getQuery());
            if (searchRequest.getFilterExpression() != null) {
                captureContent(span, "db.vector.query.filter", searchRequest.getFilterExpression().toString());
            }
        } else if ("add".equals(operation) && argument instanceof List) {
            if (config.isCaptureSpringAiContent()) {
                captureStructuredContent(span, "db.vector.documents", serializeDocuments((List<?>) argument));
            }
        }
        if (hasDbSystem) {
            span.withOtelAttribute("db.system", dbSystem);
            span.getContext().getServiceTarget().withType(dbSystem).withName(context.getCollectionName());
        }
        return span;
    }

    @Nullable
    static Span<?> startToolSpan(ToolCallback callback, String toolInput) {
        Span<?> span = tracer.currentContext().createSpan();
        if (span == null) {
            return null;
        }
        ToolDefinition definition = callback.getToolDefinition();
        String name = definition.name();
        span.withName("execute_tool " + name)
            .withType("app")
            .withSubtype("internal")
            .withOtelAttribute("gen_ai.operation.name", "execute_tool")
            .withOtelAttribute("spring.ai.kind", "tool_call")
            .withOtelAttribute("spring.ai.tool.type", "function")
            .withOtelAttribute("spring.ai.tool.definition.name", name)
            .withOtelAttribute("spring.ai.tool.definition.description", definition.description())
            .withOtelAttribute("spring.ai.tool.definition.schema", definition.inputSchema());
        captureContent(span, "spring.ai.tool.call.arguments", toolInput);
        return span;
    }

    @SuppressWarnings("unchecked")
    static Flux<?> wrapChatFlux(final Prompt prompt, final Flux<?> source) {
        final Flux<ChatResponse> typedSource = (Flux<ChatResponse>) source;
        return Flux.defer(() -> {
            final long startNanos = System.nanoTime();
            final Span<?> span = startModelSpan("chat", prompt, true);
            final AtomicBoolean firstChunkRecorded = new AtomicBoolean();
            final Flux<ChatResponse> timedSource = typedSource.doOnNext(value -> {
                if (firstChunkRecorded.compareAndSet(false, true)) {
                    double elapsedSeconds = Math.max(0, System.nanoTime() - startNanos) / 1_000_000_000d;
                    if (span != null) {
                        span.withOtelAttribute("gen_ai.response.time_to_first_chunk", elapsedSeconds);
                    }
                    ChatOptions options = prompt.getOptions();
                    SpringAiMetrics.recordTimeToFirstChunk(
                        options == null ? null : options.getModel(), elapsedSeconds);
                }
            });
            if (span == null) {
                return timedSource;
            }
            final AtomicReference<ChatResponse> lastResponse = new AtomicReference<>();
            final AtomicReference<ChatResponse> aggregatedResponse = new AtomicReference<>();
            final AtomicReference<Throwable> failure = new AtomicReference<>();
            final AtomicBoolean ended = new AtomicBoolean();
            span.withSync(false);
            Flux<ChatResponse> observedSource = config.isCaptureSpringAiContent()
                ? new MessageAggregator().aggregate(timedSource, aggregatedResponse::set)
                : timedSource;
            return observedSource
                .doOnNext(lastResponse::set)
                .doOnError(failure::set)
                .doFinally(signal -> {
                    if (ended.compareAndSet(false, true)) {
                        ChatResponse response = aggregatedResponse.get();
                        addChatResponseAttributes(span, response == null ? lastResponse.get() : response);
                        endAsyncSpan(span, failure.get());
                    }
                })
                .contextWrite(context -> context.put(TraceState.class, span));
        });
    }

    static void endChatSpan(@Nullable Span<?> span, @Nullable ChatResponse response, @Nullable Throwable thrown) {
        if (span == null) {
            return;
        }
        addChatResponseAttributes(span, response);
        endSpan(span, thrown);
    }

    private static void addChatResponseAttributes(Span<?> span, @Nullable ChatResponse response) {
        if (response != null) {
            ChatResponseMetadata metadata = response.getMetadata();
            if (metadata != null) {
                span.withOtelAttribute("gen_ai.response.id", metadata.getId())
                    .withOtelAttribute("gen_ai.response.model", metadata.getModel());
                addUsage(span, metadata.getUsage());
            }
            List<String> finishReasons = new ArrayList<>();
            for (Generation generation : response.getResults()) {
                ChatGenerationMetadata generationMetadata = generation.getMetadata();
                if (generationMetadata != null && generationMetadata.getFinishReason() != null) {
                    finishReasons.add(generationMetadata.getFinishReason());
                }
            }
            span.withOtelAttribute("gen_ai.response.finish_reasons", stringify(finishReasons));
            if (config.isCaptureSpringAiContent()) {
                captureStructuredContent(span, "gen_ai.output.messages", serializeGenerations(response.getResults()));
            }
        }
    }

    static void endVectorStoreSpan(@Nullable Span<?> span, @Nullable Object response, @Nullable Throwable thrown) {
        if (span != null && response instanceof List) {
            if (config.isCaptureSpringAiContent()) {
                captureStructuredContent(span, "db.vector.query.response.documents", serializeDocuments((List<?>) response));
            }
        }
        endSpan(span, thrown);
    }

    static void endToolSpan(@Nullable Span<?> span, @Nullable String result, @Nullable Throwable thrown) {
        if (span != null) {
            captureContent(span, "spring.ai.tool.call.result", result);
        }
        endSpan(span, thrown);
    }

    static void endEmbeddingSpan(@Nullable Span<?> span, @Nullable EmbeddingResponse response,
                                 @Nullable Throwable thrown) {
        if (span != null && response != null) {
            EmbeddingResponseMetadata metadata = response.getMetadata();
            if (metadata != null) {
                span.withOtelAttribute("gen_ai.response.model", metadata.getModel());
                addUsage(span, metadata.getUsage());
            }
        }
        endSpan(span, thrown);
    }

    static void endSpan(@Nullable Span<?> span, @Nullable Throwable thrown) {
        if (span != null) {
            span.captureException(thrown)
                .withOutcome(thrown == null ? Outcome.SUCCESS : Outcome.FAILURE)
                .deactivate()
                .end();
        }
    }

    private static void endAsyncSpan(Span<?> span, @Nullable Throwable thrown) {
        span.captureException(thrown)
            .withOutcome(thrown == null ? Outcome.SUCCESS : Outcome.FAILURE)
            .end();
    }

    private static void addUsage(Span<?> span, @Nullable Usage usage) {
        if (usage != null) {
            span.withOtelAttribute("gen_ai.usage.input_tokens", usage.getPromptTokens())
                .withOtelAttribute("gen_ai.usage.output_tokens", usage.getCompletionTokens())
                .withOtelAttribute("gen_ai.usage.total_tokens", usage.getTotalTokens())
                .withOtelAttribute("gen_ai.usage.cache_read.input_tokens", usage.getCacheReadInputTokens())
                .withOtelAttribute("gen_ai.usage.cache_creation.input_tokens", usage.getCacheWriteInputTokens());
        }
    }

    private static String spanName(String operation, @Nullable String model) {
        return model == null || model.isEmpty() ? operation : operation + " " + model;
    }

    @Nullable
    private static String stringify(@Nullable List<?> values) {
        return values == null || values.isEmpty() ? null : values.toString();
    }

    private static void captureContent(Span<?> span, String key, @Nullable String content) {
        if (config.isCaptureSpringAiContent() && content != null) {
            span.withOtelAttribute(key, truncate(content, coreConfig.getLongFieldMaxLength()));
        }
    }

    private static void captureStructuredContent(Span<?> span, String key, String content) {
        span.withOtelAttribute(key, content);
    }

    private static String truncate(String value, int maxCodePoints) {
        if (maxCodePoints < 0 || value.codePointCount(0, value.length()) <= maxCodePoints) {
            return value;
        }
        return value.substring(0, value.offsetByCodePoints(0, maxCodePoints));
    }

    private static String serializeMessages(List<? extends Message> messages) {
        StringBuilder result = new StringBuilder("[");
        for (int i = 0; i < messages.size(); i++) {
            if (i > 0) {
                result.append(',');
            }
            Message message = messages.get(i);
            result.append("{\"role\":");
            appendJsonString(result, message.getMessageType().getValue());
            result.append(",\"parts\":[");
            boolean partAdded = false;
            String text = message.getText();
            if (text != null && !text.isEmpty()) {
                appendTextPart(result, text);
                partAdded = true;
            }
            if (message instanceof AssistantMessage) {
                for (AssistantMessage.ToolCall toolCall : ((AssistantMessage) message).getToolCalls()) {
                    appendPartSeparator(result, partAdded);
                    appendToolCallPart(result, toolCall);
                    partAdded = true;
                }
            }
            if (message instanceof ToolResponseMessage) {
                for (ToolResponseMessage.ToolResponse response : ((ToolResponseMessage) message).getResponses()) {
                    appendPartSeparator(result, partAdded);
                    appendToolResponsePart(result, response);
                    partAdded = true;
                }
            }
            if (!partAdded) {
                appendTextPart(result, text);
            }
            result.append("]}");
        }
        return result.append(']').toString();
    }

    private static void appendPartSeparator(StringBuilder target, boolean partAdded) {
        if (partAdded) {
            target.append(',');
        }
    }

    private static void appendTextPart(StringBuilder target, @Nullable String content) {
        target.append("{\"type\":\"text\",\"content\":");
        appendJsonString(target, content);
        target.append('}');
    }

    private static void appendToolCallPart(StringBuilder target, AssistantMessage.ToolCall toolCall) {
        target.append("{\"type\":\"tool_call\",\"id\":");
        appendJsonString(target, toolCall.id());
        target.append(",\"name\":");
        appendJsonString(target, toolCall.name());
        target.append(",\"arguments\":");
        appendJsonString(target, toolCall.arguments());
        target.append('}');
    }

    private static void appendToolResponsePart(StringBuilder target,
                                               ToolResponseMessage.ToolResponse response) {
        target.append("{\"type\":\"tool_call_response\",\"id\":");
        appendJsonString(target, response.id());
        target.append(",\"name\":");
        appendJsonString(target, response.name());
        target.append(",\"response\":");
        appendJsonString(target, response.responseData());
        target.append('}');
    }

    private static String serializeGenerations(List<Generation> generations) {
        List<Message> messages = new ArrayList<>();
        for (Generation generation : generations) {
            if (generation.getOutput() != null) {
                messages.add(generation.getOutput());
            }
        }
        return serializeMessages(messages);
    }

    private static String serializeStrings(List<String> values) {
        StringBuilder result = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                result.append(',');
            }
            appendJsonString(result, values.get(i));
        }
        return result.append(']').toString();
    }

    private static String serializeDocuments(List<?> values) {
        StringBuilder result = new StringBuilder("[");
        boolean first = true;
        for (Object value : values) {
            if (!(value instanceof Document)) {
                continue;
            }
            if (!first) {
                result.append(',');
            }
            first = false;
            Document document = (Document) value;
            result.append("{\"id\":");
            appendJsonString(result, document.getId());
            result.append(",\"content\":");
            appendJsonString(result, document.getText());
            result.append('}');
        }
        return result.append(']').toString();
    }

    private static void appendJsonString(StringBuilder target, @Nullable String value) {
        if (value == null) {
            target.append("null");
            return;
        }
        value = truncate(value, coreConfig.getLongFieldMaxLength());
        target.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"': target.append("\\\""); break;
                case '\\': target.append("\\\\"); break;
                case '\b': target.append("\\b"); break;
                case '\f': target.append("\\f"); break;
                case '\n': target.append("\\n"); break;
                case '\r': target.append("\\r"); break;
                case '\t': target.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        target.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                    } else {
                        target.append(c);
                    }
            }
        }
        target.append('"');
    }

}
