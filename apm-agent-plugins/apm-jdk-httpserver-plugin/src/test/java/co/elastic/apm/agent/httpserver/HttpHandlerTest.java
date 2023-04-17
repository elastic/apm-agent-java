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
package co.elastic.apm.agent.httpserver;

import co.elastic.apm.agent.AbstractInstrumentationTest;
import co.elastic.apm.agent.impl.context.Request;
import co.elastic.apm.agent.impl.context.Response;
import co.elastic.apm.agent.impl.context.Socket;
import co.elastic.apm.agent.impl.context.Url;
import co.elastic.apm.agent.impl.context.web.ResultUtil;
import co.elastic.apm.agent.impl.context.web.WebConfiguration;
import co.elastic.apm.agent.impl.transaction.TraceContext;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.common.util.WildcardMatcher;
import co.elastic.apm.agent.util.PotentiallyMultiValuedMap;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doReturn;

class HttpHandlerTest extends AbstractInstrumentationTest {

    private static class MyHttpHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();

            exchange.getResponseHeaders().add("Bar", "xyz");
            exchange.sendResponseHeaders(Integer.parseInt(path.substring(path.length() - 3)), 0);
            exchange.close();
        }
    }

    private static HttpServer httpServer;

    private static OkHttpClient httpClient;

    private static int count = 0;

    @BeforeEach
    void createServer() throws IOException {
        httpServer = HttpServer.create(new InetSocketAddress(0), -1);

        // ensure that 2 ways to create context and register handler are covered
        if (((count++) % 2) == 0) {
            httpServer.createContext("/", new MyHttpHandler());
        } else {
            httpServer.createContext("/").setHandler(new MyHttpHandler());
        }

        httpServer.start();

        httpClient = new OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.SECONDS)
            .connectTimeout(0, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build();
    }

    @AfterEach
    void stopServer() {
        httpServer.stop(0);
    }

    @ParameterizedTest
    @ValueSource(ints = {200, 300, 400, 500})
    void testRootTransaction(int expectedStatus) throws IOException {
        okhttp3.Request httpRequest = new okhttp3.Request.Builder()
            .url("http://localhost:" + httpServer.getAddress().getPort() + "/status_" + expectedStatus + "?q=p")
            .addHeader("Cookie", "a=b")
            .addHeader("Foo", "abc")
            .build();
        okhttp3.Response httpResponse = httpClient.newCall(httpRequest).execute();
        assertThat(httpResponse.code()).isEqualTo(expectedStatus);
        assertThat(httpResponse.body().string()).isEmpty();

        Transaction transaction = reporter.getFirstTransaction(500);
        assertThat(transaction.getTraceContext().getParentId().toString()).isEqualTo("0000000000000000");
        assertThat(transaction.getType()).isEqualTo(co.elastic.apm.agent.tracer.Transaction.TYPE_REQUEST);
        assertThat(transaction.getNameAsString()).isEqualTo("GET /status_%d", expectedStatus);
        assertThat(transaction.getResult()).isEqualTo(ResultUtil.getResultByHttpStatus(expectedStatus));
        assertThat(transaction.getOutcome()).isEqualTo(ResultUtil.getOutcomeByHttpServerStatus(expectedStatus));

        Request request = transaction.getContext().getRequest();

        Socket socket = request.getSocket();
        assertThat(socket.getRemoteAddress()).isEqualTo("127.0.0.1");

        Url url = request.getUrl();
        assertThat(url.getProtocol()).isEqualTo("http");
        assertThat(url.getHostname()).isEqualTo("localhost");
        assertThat(url.getPort()).isEqualTo(httpServer.getAddress().getPort());
        assertThat(url.getPathname()).isEqualTo("/status_" + expectedStatus);
        assertThat(url.getSearch()).isEqualTo("q=p");

        PotentiallyMultiValuedMap cookies = request.getCookies();
        assertThat(cookies.size()).isEqualTo(1);
        assertThat(cookies.get("a")).isEqualTo("b");

        assertThat(request.getHeaders().get("Foo")).isEqualTo("abc");

        Response response = transaction.getContext().getResponse();
        assertThat(response.getStatusCode()).isEqualTo(expectedStatus);

        assertThat(response.getHeaders().get("Bar")).isEqualTo("xyz");

        assertThat(reporter.getSpans()).isEmpty();
        assertThat(reporter.getErrors()).isEmpty();
    }

    @Test
    void testChildTransaction() throws IOException {
        okhttp3.Request httpRequest = new okhttp3.Request.Builder()
            .url("http://localhost:" + httpServer.getAddress().getPort() + "/status_200")
            .addHeader("traceparent", "00-e00fef7cc6023c8e2c02d003cf50a266-a048a11f61713b66-01")
            .build();
        okhttp3.Response httpResponse = httpClient.newCall(httpRequest).execute();
        assertThat(httpResponse.code()).isEqualTo(200);
        assertThat(httpResponse.body().string()).isEmpty();

        TraceContext traceContext = reporter.getFirstTransaction(500).getTraceContext();
        assertThat(traceContext.getTraceId().toString()).isEqualTo("e00fef7cc6023c8e2c02d003cf50a266");
        assertThat(traceContext.getParentId().toString()).isEqualTo("a048a11f61713b66");

        assertThat(reporter.getSpans()).isEmpty();
        assertThat(reporter.getErrors()).isEmpty();
    }

    @Test
    void testExcludedUrl() throws IOException {
        WebConfiguration webConfiguration = config.getConfig(WebConfiguration.class);
        doReturn(List.of(WildcardMatcher.valueOf("/status_*"))).when(webConfiguration).getIgnoreUrls();
        try {
            okhttp3.Request httpRequest = new okhttp3.Request.Builder()
                .url("http://localhost:" + httpServer.getAddress().getPort() + "/status_200")
                .build();
            okhttp3.Response httpResponse = httpClient.newCall(httpRequest).execute();
            assertThat(httpResponse.code()).isEqualTo(200);
            assertThat(httpResponse.body().string()).isEmpty();

            assertThat(reporter.getTransactions()).isEmpty();
            assertThat(reporter.getSpans()).isEmpty();
            assertThat(reporter.getErrors()).isEmpty();
        } finally {
            doCallRealMethod().when(webConfiguration).getIgnoreUrls();
        }
    }

    @Test
    void testExcludedUserAgent() throws IOException {
        WebConfiguration webConfiguration = config.getConfig(WebConfiguration.class);
        doReturn(List.of(WildcardMatcher.valueOf("okhttp"))).when(webConfiguration).getIgnoreUserAgents();

        try {
            okhttp3.Request httpRequest = new okhttp3.Request.Builder()
                .url("http://localhost:" + httpServer.getAddress().getPort() + "/status_200")
                .addHeader("User-Agent", "okhttp")
                .build();
            okhttp3.Response httpResponse = httpClient.newCall(httpRequest).execute();
            assertThat(httpResponse.code()).isEqualTo(200);
            assertThat(httpResponse.body().string()).isEmpty();

            assertThat(reporter.getTransactions()).isEmpty();
            assertThat(reporter.getSpans()).isEmpty();
            assertThat(reporter.getErrors()).isEmpty();
        } finally {
            doCallRealMethod().when(webConfiguration).getIgnoreUserAgents();
        }
    }
}
