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
package co.elastic.apm.report;

import co.elastic.apm.configuration.CoreConfiguration;
import co.elastic.apm.configuration.SpyConfiguration;
import co.elastic.apm.impl.error.ErrorCapture;
import co.elastic.apm.impl.payload.ProcessInfo;
import co.elastic.apm.impl.payload.Service;
import co.elastic.apm.impl.payload.SystemInfo;
import co.elastic.apm.impl.transaction.Transaction;
import co.elastic.apm.report.processor.ProcessorEventHandler;
import co.elastic.apm.report.serialize.DslJsonSerializer;
import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.stagemonitor.configuration.ConfigurationRegistry;

import java.net.InetSocketAddress;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.mockito.Mockito.when;

class ApmServerReporterIntegrationTest {

    private static Undertow server;
    private static int port;
    private static AtomicInteger receivedHttpRequests = new AtomicInteger();
    private static HttpHandler handler;
    private ApmServerHttpPayloadSender payloadSender;
    private ReporterConfiguration reporterConfiguration;
    private ApmServerReporter reporter;

    @BeforeAll
    static void startServer() {
        server = Undertow.builder()
            .addHttpListener(0, "127.0.0.1")
            .setHandler(exchange -> {
                if (handler != null) {
                    handler.handleRequest(exchange);
                }
            }).build();
        server.start();
        port = ((InetSocketAddress) server.getListenerInfo().get(0).getAddress()).getPort();
    }

    @AfterAll
    static void stopServer() {
        server.stop();
    }

    @BeforeEach
    void setUp() {
        handler = exchange -> {
            receivedHttpRequests.incrementAndGet();
            exchange.setStatusCode(200).endExchange();
        };
        receivedHttpRequests.set(0);
        final ConfigurationRegistry config = SpyConfiguration.createSpyConfig();
        reporterConfiguration = config.getConfig(ReporterConfiguration.class);
        when(reporterConfiguration.getFlushInterval()).thenReturn(-1);
        when(reporterConfiguration.getServerUrl()).thenReturn("http://localhost:" + port);
        payloadSender = new ApmServerHttpPayloadSender(new OkHttpClient(),
            new DslJsonSerializer(config.getConfig(CoreConfiguration.class)),
            reporterConfiguration);
        SystemInfo system = new SystemInfo("x64", "localhost", "platform");
        reporter = new ApmServerReporter(new Service(), new ProcessInfo("title"), system, payloadSender, false,
            reporterConfiguration, ProcessorEventHandler.loadProcessors(config), config.getConfig(CoreConfiguration.class));
    }

    @Test
    void testReportTransaction() throws ExecutionException, InterruptedException {
        reporter.report(new Transaction());
        reporter.flush().get();
        assertThat(reporter.getDropped()).isEqualTo(0);
        assertThat(receivedHttpRequests.get()).isEqualTo(1);
    }

    @Test
    void testSecretToken() throws ExecutionException, InterruptedException {
        when(reporterConfiguration.getSecretToken()).thenReturn("token");
        handler = exchange -> {
            assertThat(exchange.getRequestHeaders().get("Authorization").getFirst()).isEqualTo("Bearer token");
            receivedHttpRequests.incrementAndGet();
            exchange.setStatusCode(200).endExchange();
        };
        reporter.report(new Transaction());
        reporter.flush().get();
        assertThat(reporter.getDropped()).isEqualTo(0);
        assertThat(receivedHttpRequests.get()).isEqualTo(1);
    }

    @Test
    void testReportErrorCapture() throws ExecutionException, InterruptedException {
        reporter.report(new ErrorCapture());
        reporter.flush().get();
        assertThat(reporter.getDropped()).isEqualTo(0);
        assertThat(receivedHttpRequests.get()).isEqualTo(1);
    }
}
