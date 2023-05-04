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
package co.elastic.apm.awslambda.fakeserver;

import co.elastic.apm.agent.test.TestPort;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

public class FakeApmServer implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(FakeApmServer.class);

    @Nullable
    private final HttpServer httpServer;

    private final AtomicInteger intakeRequestCounter = new AtomicInteger(0);

    private List<ServerEvent> receivedEvents = new ArrayList<>();

    public FakeApmServer() {
        this(TestPort.getAvailableRandomPort());
    }

    public FakeApmServer(int port) {
        int serverPort = TestPort.getAvailableRandomPort();
        try {
            httpServer = HttpServer.create(new InetSocketAddress(serverPort), 0);
            httpServer.setExecutor(Executors.newCachedThreadPool());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        HttpContext context = httpServer.createContext("/");
        context.setHandler(new Handler());
        httpServer.start();

        log.info("Starting fake APM server on port {}", port());
    }

    public String getServerUrl() {
        return "http://127.0.0.1:" + port();
    }

    public void close() {
        log.info("Stopping fake APM server on port {}", port());
        try {
            httpServer.stop(0);
        } catch (Exception e) {
            log.error("Error stopping fake apm server", e);
        }
    }

    public int port() {
        return httpServer.getAddress().getPort();
    }

    protected void handleConfigRequest(HttpExchange exchange) throws IOException {
        log.info("Receiving config request");
        sendResponseJson(exchange, 200, "{}");
    }

    protected void handleIntakeRequest(HttpExchange exchange) throws IOException {
        int reqId = intakeRequestCounter.incrementAndGet();
        IntakeRequest request = new IntakeRequest(reqId);
        log.info("Receiving Intake Request  No.{}", reqId);
        try (InputStream body = exchange.getRequestBody()) {

            InputStream input = body;
            List<String> contentEncoding = exchange.getRequestHeaders().get("Content-Encoding");
            if (contentEncoding != null && contentEncoding.contains("gzip")) {
                input = new GZIPInputStream(input);
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
            String event;
            while ((event = reader.readLine()) != null) {
                addEvent(new IntakeEvent(request, event));
            }
        }
        log.info("Finished Intake Request  No.{}", reqId);
        exchange.sendResponseHeaders(202, -1);
    }

    protected void handleHealthCheckRequest(HttpExchange exchange) throws IOException {
        log.info("Receiving health check request");
        sendResponseJson(exchange, 200, "{\"version\" : \"8.7.1\"}");
    }

    private void sendResponseJson(HttpExchange exchange, int status, String content) throws IOException {
        if (content == null || content.isEmpty()) {
            exchange.sendResponseHeaders(status, -1);
        } else {
            byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, contentBytes.length);
            exchange.getResponseBody().write(contentBytes);
        }
    }

    public List<ServerEvent> getReceivedEvents() {
        synchronized (receivedEvents) {
            return new ArrayList<>(receivedEvents);
        }
    }

    public List<IntakeEvent> getIntakeEvents() {
        return getReceivedEvents().stream()
            .filter(event -> event instanceof IntakeEvent)
            .map(event -> (IntakeEvent) event)
            .collect(Collectors.toList());
    }

    protected void addEvent(ServerEvent event) {
        synchronized (receivedEvents) {
            receivedEvents.add(event);
        }
    }

    public void reset() {
        synchronized (receivedEvents) {
            receivedEvents.clear();
        }
    }

    private class Handler implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();
            String pathAndMethod = method + " " + path;
            switch (pathAndMethod) {
                case "GET /":
                    handleHealthCheckRequest(exchange);
                    break;
                case "POST /intake/v2/events":
                    handleIntakeRequest(exchange);
                    break;
                case "POST /config/v1/agents":
                    handleConfigRequest(exchange);
                    break;
                default:
                    log.error("Encountered unexpected request {}", pathAndMethod);
                    exchange.sendResponseHeaders(404, -1);
            }
            exchange.close();
        }

    }

}
