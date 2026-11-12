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
package co.elastic.apm.springai;

import co.elastic.apm.agent.test.AgentFileAccessor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Predicate;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class SpringAiInstrumentationIT {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(2))
        .build();

    private static FakeApmServer apmServer;
    private static Process application;
    private static URI applicationUri;
    private static Path applicationLog;

    @BeforeAll
    static void startApplication() throws Exception {
        apmServer = new FakeApmServer();
        int applicationPort = availablePort();
        applicationUri = URI.create("http://127.0.0.1:" + applicationPort);
        applicationLog = Path.of("target", "spring-ai-test-app.log").toAbsolutePath();
        Files.deleteIfExists(applicationLog);

        Path agentJar = AgentFileAccessor.getPathToJavaagent();
        Path applicationJar = Path.of("target", "spring-ai-test.jar").toAbsolutePath();
        Path mcpServerScript = Path.of("target", "classes", "mcp-test-server.py").toAbsolutePath();
        assertThat(mcpServerScript).exists();
        List<String> command = List.of(
            javaBinary(),
            "-javaagent:" + agentJar,
            "-Delastic.apm.server_url=" + apmServer.getUrl(),
            "-Delastic.apm.service_name=spring-ai-integration-test",
            "-Delastic.apm.application_packages=co.elastic.apm.springai.testapp",
            "-Delastic.apm.enable_experimental_instrumentations=true",
            "-Delastic.apm.capture_ai_content=true",
            "-Delastic.apm.central_config=false",
            "-Delastic.apm.report_sync=true",
            "-Delastic.apm.metrics_interval=1s",
            "-Delastic.apm.transaction_ignore_urls=/health",
            "-Delastic.apm.span_compression_enabled=false",
            "-DMCP_SERVER_COMMAND=python3",
            "-DMCP_SERVER_SCRIPT=" + mcpServerScript,
            "-jar",
            applicationJar.toString(),
            "--server.port=" + applicationPort
        );
        application = new ProcessBuilder(command)
            .redirectErrorStream(true)
            .redirectOutput(applicationLog.toFile())
            .start();
        waitForApplication();
    }

    @AfterAll
    static void stopApplication() throws Exception {
        if (application != null && application.isAlive()) {
            application.destroy();
            if (!application.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)) {
                application.destroyForcibly();
            }
        }
        if (apmServer != null) {
            apmServer.close();
        }
    }

    @Test
    void capturesSpringAiOperationsFromPackagedApplication() throws Exception {
        assertSynchronousChat();
        assertStreamingChat();
        assertEmbedding();
        assertVectorStore();
        assertMcpToolPaths();
    }

    private static void assertSynchronousChat() throws Exception {
        apmServer.clear();
        HttpResponse<String> response = post("/chat", "{\"prompt\":\"sync integration prompt\",\"model\":\"chat-test\"}");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("Echo: sync integration prompt");

        Trace trace = awaitTrace("ChatController#chat", span -> "chat chat-test".equals(span.path("name").asText()));
        assertSpanBelongsToTransaction(trace);
        JsonNode attributes = otelAttributes(trace.span());
        assertThat(attributes.path("gen_ai.operation.name").asText()).isEqualTo("chat");
        assertThat(attributes.path("gen_ai.request.stream").asBoolean()).isFalse();
        assertThat(attributes.path("gen_ai.input.messages").asText()).contains("sync integration prompt");
        assertThat(attributes.path("gen_ai.output.messages").asText()).contains("Echo: sync integration prompt");
        assertThat(attributes.path("gen_ai.usage.total_tokens").asInt()).isEqualTo(5);
    }

    private static void assertStreamingChat() throws Exception {
        apmServer.clear();
        HttpResponse<String> response = post("/chat/stream",
            "{\"prompt\":\"stream integration prompt\",\"model\":\"stream-test\"}");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("Echo:", "stream integration prompt");

        Trace trace = awaitTrace("ChatController#stream", span -> "chat stream-test".equals(span.path("name").asText()));
        assertSpanBelongsToTransaction(trace);
        JsonNode attributes = otelAttributes(trace.span());
        assertThat(attributes.path("gen_ai.request.stream").asBoolean()).isTrue();
        assertThat(attributes.path("gen_ai.response.time_to_first_chunk").isNumber()).isTrue();
        assertThat(attributes.path("gen_ai.response.time_to_first_chunk").asDouble()).isGreaterThanOrEqualTo(0);
        assertThat(attributes.path("gen_ai.output.messages").asText()).contains("Echo: stream integration prompt");
        assertThat(attributes.path("gen_ai.usage.total_tokens").asInt()).isEqualTo(5);

        JsonNode histogram = awaitMetric("gen_ai.client.inference.time_to_first_chunk");
        assertThat(histogram.path("values")).isNotEmpty();
        assertThat(histogram.path("counts")).isNotEmpty();
        int observationCount = 0;
        for (JsonNode count : histogram.path("counts")) {
            observationCount += count.asInt();
        }
        assertThat(observationCount).isGreaterThanOrEqualTo(1);
    }

    private static void assertEmbedding() throws Exception {
        apmServer.clear();
        HttpResponse<String> response = post("/embeddings",
            "{\"inputs\":[\"integration embedding input\"],\"model\":\"embedding-test\",\"dimensions\":8}");
        assertThat(response.statusCode()).isEqualTo(200);

        Trace trace = awaitTrace("EmbeddingVectorController#embed",
            span -> "embedding embedding-test".equals(span.path("name").asText()));
        assertSpanBelongsToTransaction(trace);
        JsonNode attributes = otelAttributes(trace.span());
        assertThat(attributes.path("gen_ai.operation.name").asText()).isEqualTo("embedding");
        assertThat(attributes.path("spring.ai.embedding.request.inputs").asText()).contains("integration embedding input");
        assertThat(attributes.path("gen_ai.request.embedding.dimensions").asInt()).isEqualTo(8);
    }

    private static void assertVectorStore() throws Exception {
        apmServer.clear();
        HttpResponse<String> addResponse = post("/vector-store/documents",
            "{\"documents\":["
                + "{\"id\":\"observability\",\"text\":\"distributed tracing integration document\","
                + "\"metadata\":{\"category\":\"observability\"}},"
                + "{\"id\":\"gardening\",\"text\":\"growing tomatoes in a sunny garden\","
                + "\"metadata\":{\"category\":\"gardening\"}}]}");
        assertThat(addResponse.statusCode()).isEqualTo(200);

        Trace addTrace = awaitTrace("EmbeddingVectorController#addDocuments",
            span -> "add".equals(otelAttributes(span).path("db.operation.name").asText()));
        assertSpanBelongsToTransaction(addTrace);
        assertThat(otelAttributes(addTrace.span()).path("db.vector.documents").asText())
            .contains("distributed tracing integration document");

        apmServer.clear();
        HttpResponse<String> searchResponse = post("/vector-store/search",
            "{\"query\":\"distributed tracing\",\"topK\":3,\"similarityThreshold\":0.0,"
                + "\"filter\":\"category == 'observability'\"}");
        assertThat(searchResponse.statusCode()).isEqualTo(200);
        assertThat(searchResponse.body()).contains("distributed tracing integration document");

        Trace searchTrace = awaitTrace("EmbeddingVectorController#search",
            span -> "query".equals(otelAttributes(span).path("db.operation.name").asText()));
        assertSpanBelongsToTransaction(searchTrace);
        JsonNode attributes = otelAttributes(searchTrace.span());
        assertThat(attributes.path("db.vector.query.content").asText()).isEqualTo("distributed tracing");
        assertThat(attributes.path("db.vector.query.filter").asText()).contains("observability");
        assertThat(attributes.path("db.vector.query.response.documents").asText())
            .contains("distributed tracing integration document");

        apmServer.clear();
        HttpResponse<String> deleteResponse = delete("/vector-store/documents", "{\"ids\":[\"observability\"]}");
        assertThat(deleteResponse.statusCode()).isEqualTo(200);
        Trace deleteTrace = awaitTrace("EmbeddingVectorController#deleteDocuments",
            span -> "delete".equals(otelAttributes(span).path("db.operation.name").asText()));
        assertSpanBelongsToTransaction(deleteTrace);

        apmServer.clear();
        HttpResponse<String> afterDelete = post("/vector-store/search",
            "{\"query\":\"distributed tracing\",\"topK\":3,\"similarityThreshold\":0.0}");
        assertThat(afterDelete.statusCode()).isEqualTo(200);
        assertThat(afterDelete.body()).doesNotContain("distributed tracing integration document");
    }

    private static void assertMcpToolPaths() throws Exception {
        apmServer.clear();
        HttpResponse<String> response = post("/mcp/tools/echo",
            "{\"message\":\"Hello from direct MCP integration test\",\"repeat\":1}");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("Hello from direct MCP integration test");

        Trace trace = awaitTrace("McpToolController#invokeEchoTool",
            span -> span.path("name").asText().startsWith("execute_tool "));
        assertSpanBelongsToTransaction(trace);
        JsonNode attributes = otelAttributes(trace.span());
        assertThat(attributes.path("spring.ai.tool.definition.name").asText()).endsWith("echo");
        assertThat(attributes.path("spring.ai.tool.call.arguments").asText())
            .contains("Hello from direct MCP integration test");
        assertThat(attributes.path("spring.ai.tool.call.result").asText())
            .contains("Hello from direct MCP integration test");

        apmServer.clear();
        HttpResponse<String> chatResponse = post("/mcp/chat", "{\"message\":\"Hello from MCP chat integration test\"}");
        assertThat(chatResponse.statusCode()).isEqualTo(200);
        assertThat(chatResponse.body()).contains("Hello from MCP chat integration test");

        await().atMost(Duration.ofSeconds(15))
            .until(() -> findTransaction("McpToolController#chatWithEchoTool").isPresent());
        ObjectNode transaction = findTransaction("McpToolController#chatWithEchoTool").orElseThrow();
        String transactionId = transaction.path("id").asText();
        await().atMost(Duration.ofSeconds(15)).until(() -> spans(transactionId,
            span -> "chat tool-test".equals(span.path("name").asText())).size() >= 2);

        List<ObjectNode> chatSpans = spans(transactionId,
            span -> "chat tool-test".equals(span.path("name").asText()));
        assertThat(chatSpans).anySatisfy(span ->
            assertThat(otelAttributes(span).path("gen_ai.output.messages").asText())
                .contains("tool_call", "Hello from MCP chat integration test"));
        assertThat(chatSpans).anySatisfy(span -> {
            assertThat(otelAttributes(span).path("gen_ai.input.messages").asText())
                .contains("tool_call_response", "Hello from MCP chat integration test");
            assertThat(otelAttributes(span).path("gen_ai.output.messages").asText())
                .contains("I called the echo tool", "Hello from MCP chat integration test");
        });

        await().atMost(Duration.ofSeconds(15)).until(() -> spans(transactionId,
            span -> span.path("name").asText().startsWith("execute_tool ")).size() == 1);
        ObjectNode toolSpan = spans(transactionId,
            span -> span.path("name").asText().startsWith("execute_tool ")).get(0);
        assertThat(otelAttributes(toolSpan).path("spring.ai.tool.definition.name").asText()).endsWith("echo");
        assertThat(otelAttributes(toolSpan).path("spring.ai.tool.call.arguments").asText())
            .contains("Hello from MCP chat integration test");
    }

    private static void assertSpanBelongsToTransaction(Trace trace) {
        assertThat(trace.span().path("transaction_id").asText()).isEqualTo(trace.transaction().path("id").asText());
        assertThat(trace.span().path("trace_id").asText()).isEqualTo(trace.transaction().path("trace_id").asText());
        assertThat(trace.span().path("parent_id").asText()).isEqualTo(trace.transaction().path("id").asText());
        assertThat(trace.transaction().path("span_count").path("started").asInt()).isGreaterThanOrEqualTo(1);
    }

    private static JsonNode otelAttributes(ObjectNode span) {
        return span.path("otel").path("attributes");
    }

    private static Trace awaitTrace(String transactionName, Predicate<ObjectNode> spanPredicate) {
        await().atMost(Duration.ofSeconds(15)).until(() -> findTransaction(transactionName).isPresent());
        ObjectNode transaction = findTransaction(transactionName).orElseThrow();
        String transactionId = transaction.path("id").asText();
        await().atMost(Duration.ofSeconds(15)).until(() -> findSpan(transactionId, spanPredicate).isPresent());
        ObjectNode span = findSpan(transactionId, spanPredicate).orElseThrow();
        return new Trace(transaction, span);
    }

    private static Optional<ObjectNode> findTransaction(String name) {
        return apmServer.events("transaction").stream()
            .filter(event -> name.equals(event.path("name").asText()))
            .findFirst();
    }

    private static Optional<ObjectNode> findSpan(String transactionId, Predicate<ObjectNode> predicate) {
        return apmServer.events("span").stream()
            .filter(event -> transactionId.equals(event.path("transaction_id").asText()))
            .filter(predicate)
            .findFirst();
    }

    private static List<ObjectNode> spans(String transactionId, Predicate<ObjectNode> predicate) {
        return apmServer.events("span").stream()
            .filter(event -> transactionId.equals(event.path("transaction_id").asText()))
            .filter(predicate)
            .toList();
    }

    private static JsonNode awaitMetric(String name) {
        await().atMost(Duration.ofSeconds(15)).until(() -> findMetric(name).isPresent());
        return findMetric(name).orElseThrow();
    }

    private static Optional<JsonNode> findMetric(String name) {
        return apmServer.events("metricset").stream()
            .map(metricset -> metricset.path("samples").path(name))
            .filter(JsonNode::isObject)
            .findFirst();
    }

    private static HttpResponse<String> post(String path, String body) throws IOException, InterruptedException {
        return request("POST", path, body);
    }

    private static HttpResponse<String> delete(String path, String body) throws IOException, InterruptedException {
        return request("DELETE", path, body);
    }

    private static HttpResponse<String> request(String method, String path, String body)
        throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(applicationUri.resolve(path))
            .header("Content-Type", "application/json")
            .timeout(Duration.ofSeconds(15))
            .method(method, HttpRequest.BodyPublishers.ofString(body))
            .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static void waitForApplication() {
        await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(200)).until(() -> {
            if (!application.isAlive()) {
                throw new IllegalStateException("Spring AI test application stopped during startup:\n" + applicationLogs());
            }
            try {
                HttpRequest request = HttpRequest.newBuilder(applicationUri.resolve("/health"))
                    .timeout(Duration.ofSeconds(1))
                    .GET()
                    .build();
                return httpClient.send(request, HttpResponse.BodyHandlers.ofString()).statusCode() == 200;
            } catch (Exception ignored) {
                return false;
            }
        });
    }

    private static String applicationLogs() {
        try {
            return Files.exists(applicationLog) ? Files.readString(applicationLog) : "<no application log>";
        } catch (IOException e) {
            return "<unable to read application log: " + e.getMessage() + ">";
        }
    }

    private static int availablePort() throws IOException {
        try (java.net.ServerSocket socket = new java.net.ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static String javaBinary() {
        String executable = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win") ? "java.exe" : "java";
        return Path.of(System.getProperty("java.home"), "bin", executable).toString();
    }

    private record Trace(ObjectNode transaction, ObjectNode span) {
    }

    private static final class FakeApmServer implements AutoCloseable {

        private final HttpServer server;
        private final ExecutorService executor = Executors.newCachedThreadPool();
        private final List<ObjectNode> receivedEvents = new CopyOnWriteArrayList<>();

        private FakeApmServer() throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", this::handle);
            server.setExecutor(executor);
            server.start();
        }

        private String getUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        private void clear() {
            receivedEvents.clear();
        }

        private List<ObjectNode> events(String type) {
            return receivedEvents.stream()
                .filter(event -> event.has(type))
                .map(event -> (ObjectNode) event.get(type))
                .toList();
        }

        private void handle(HttpExchange exchange) throws IOException {
            try {
                String path = exchange.getRequestURI().getPath();
                if ("GET".equals(exchange.getRequestMethod()) && "/".equals(path)) {
                    respond(exchange, 200, "{\"version\":\"8.17.0\"}");
                } else if ("POST".equals(exchange.getRequestMethod()) && "/intake/v2/events".equals(path)) {
                    readEvents(exchange);
                    respond(exchange, 202, "");
                } else if ("POST".equals(exchange.getRequestMethod()) && "/config/v1/agents".equals(path)) {
                    respond(exchange, 200, "{}");
                } else {
                    respond(exchange, 404, "");
                }
            } finally {
                exchange.close();
            }
        }

        private void readEvents(HttpExchange exchange) throws IOException {
            try (InputStream decoded = decode(exchange);
                 BufferedReader reader = new BufferedReader(new InputStreamReader(decoded, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.isBlank()) {
                        receivedEvents.add((ObjectNode) objectMapper.readTree(line));
                    }
                }
            }
        }

        private static InputStream decode(HttpExchange exchange) throws IOException {
            InputStream body = exchange.getRequestBody();
            String encoding = exchange.getRequestHeaders().getFirst("Content-Encoding");
            if (encoding == null) {
                return body;
            }
            if (encoding.toLowerCase(Locale.ROOT).contains("gzip")) {
                return new GZIPInputStream(body);
            }
            if (encoding.toLowerCase(Locale.ROOT).contains("deflate")) {
                return new InflaterInputStream(body);
            }
            return body;
        }

        private static void respond(HttpExchange exchange, int status, String body) throws IOException {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
            if (bytes.length > 0) {
                try (OutputStream output = exchange.getResponseBody()) {
                    output.write(bytes);
                }
            }
        }

        @Override
        public void close() {
            server.stop(0);
            executor.shutdownNow();
        }
    }
}
