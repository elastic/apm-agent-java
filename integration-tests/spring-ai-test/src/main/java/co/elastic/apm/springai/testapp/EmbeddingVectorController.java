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

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping(produces = MediaType.APPLICATION_JSON_VALUE)
public class EmbeddingVectorController {

    private final EmbeddingModel embeddingModel;
    private final VectorStore vectorStore;

    public EmbeddingVectorController(EmbeddingModel embeddingModel, VectorStore vectorStore) {
        this.embeddingModel = embeddingModel;
        this.vectorStore = vectorStore;
    }

    @PostMapping(value = "/embeddings", consumes = MediaType.APPLICATION_JSON_VALUE)
    public EmbeddingReply embed(@RequestBody EmbeddingRequestBody request) {
        if (request == null || request.inputs() == null || request.inputs().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "inputs must not be empty");
        }
        int dimensions = request.dimensions() == null
            ? TestDataConfiguration.DeterministicEmbeddingModel.DEFAULT_DIMENSIONS
            : request.dimensions();
        String model = request.model() == null || request.model().isBlank()
            ? TestDataConfiguration.DeterministicEmbeddingModel.MODEL_NAME
            : request.model();
        EmbeddingOptions options = EmbeddingOptions.builder().model(model).dimensions(dimensions).build();
        EmbeddingResponse response = embeddingModel.call(new EmbeddingRequest(request.inputs(), options));
        List<float[]> vectors = response.getResults().stream().map(Embedding::getOutput).toList();
        return new EmbeddingReply(model, dimensions, vectors);
    }

    @PostMapping(value = "/vector-store/documents", consumes = MediaType.APPLICATION_JSON_VALUE)
    public DocumentMutationReply addDocuments(@RequestBody AddDocumentsRequest request) {
        if (request == null || request.documents() == null || request.documents().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "documents must not be empty");
        }
        List<Document> documents = request.documents().stream()
            .map(input -> Document.builder().id(input.id()).text(input.text())
                .metadata(input.metadata() == null ? Map.of() : input.metadata()).build())
            .toList();
        vectorStore.add(documents);
        return new DocumentMutationReply(documents.size());
    }

    @DeleteMapping(value = "/vector-store/documents", consumes = MediaType.APPLICATION_JSON_VALUE)
    public DocumentMutationReply deleteDocuments(@RequestBody DeleteDocumentsRequest request) {
        if (request == null || request.ids() == null || request.ids().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ids must not be empty");
        }
        vectorStore.delete(request.ids());
        return new DocumentMutationReply(request.ids().size());
    }

    @PostMapping(value = "/vector-store/search", consumes = MediaType.APPLICATION_JSON_VALUE)
    public SearchReply search(@RequestBody VectorSearchRequest request) {
        if (request == null || request.query() == null || request.query().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "query must not be blank");
        }
        SearchRequest.Builder builder = SearchRequest.builder()
            .query(request.query())
            .topK(request.topK() == null ? SearchRequest.DEFAULT_TOP_K : request.topK())
            .similarityThreshold(request.similarityThreshold() == null
                ? SearchRequest.SIMILARITY_THRESHOLD_ACCEPT_ALL
                : request.similarityThreshold());
        if (request.filter() != null && !request.filter().isBlank()) {
            builder.filterExpression(request.filter());
        }
        SearchRequest searchRequest = builder.build();
        List<String> documents = vectorStore.similaritySearch(searchRequest).stream().map(Document::getText).toList();
        return new SearchReply(documents);
    }

    public record EmbeddingRequestBody(List<String> inputs, String model, Integer dimensions) {
    }

    public record EmbeddingReply(String model, int dimensions, List<float[]> embeddings) {
    }

    public record DocumentInput(String id, String text, Map<String, Object> metadata) {
    }

    public record AddDocumentsRequest(List<DocumentInput> documents) {
    }

    public record DeleteDocumentsRequest(List<String> ids) {
    }

    public record DocumentMutationReply(int count) {
    }

    public record VectorSearchRequest(String query, Integer topK, Double similarityThreshold, String filter) {
    }

    public record SearchReply(List<String> documents) {
    }
}
