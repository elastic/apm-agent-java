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
package co.elastic.apm.agent.report;

import co.elastic.apm.agent.MockTracer;
import co.elastic.apm.agent.configuration.CoreConfiguration;
import co.elastic.apm.agent.configuration.SpyConfiguration;
import co.elastic.apm.agent.configuration.converter.TimeDuration;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.metadata.MetaDataMock;
import co.elastic.apm.agent.impl.error.ErrorCapture;
import co.elastic.apm.agent.impl.metadata.ProcessInfo;
import co.elastic.apm.agent.impl.metadata.Service;
import co.elastic.apm.agent.impl.metadata.SystemInfo;
import co.elastic.apm.agent.impl.stacktrace.StacktraceConfiguration;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.report.processor.ProcessorEventHandler;
import co.elastic.apm.agent.report.serialize.DslJsonSerializer;
import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.handlers.BlockingHandler;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.stagemonitor.configuration.ConfigurationRegistry;

import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

class ApmServerReporterIntegrationTest {

    private static Undertow server;
    private static int port;
    private static AtomicInteger receivedIntakeApiCalls = new AtomicInteger();
    private static HttpHandler handler;
    private final ElasticApmTracer tracer = MockTracer.create();
    private volatile int statusCode = HttpStatus.OK_200;
    private ReporterConfiguration reporterConfiguration;
    private ApmServerReporter reporter;
    private final AtomicInteger receivedEvents = new AtomicInteger();
    private IntakeV2ReportingEventHandler v2handler;

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
    void setUp() throws Exception {
        handler = new BlockingHandler(exchange -> {
            if (statusCode < 300 && exchange.getRequestPath().equals("/intake/v2/events")) {
                receivedIntakeApiCalls.incrementAndGet();
                InputStream in = exchange.getInputStream();
                try (in) {
                    for (int n = 0; -1 != n; n = in.read()) {
                        if (n == '\n') {
                            receivedEvents.incrementAndGet();
                        }
                    }
                }
            }
            exchange.setStatusCode(statusCode).endExchange();
        });
        receivedIntakeApiCalls.set(0);
        ConfigurationRegistry config = SpyConfiguration.createSpyConfig();
        reporterConfiguration = config.getConfig(ReporterConfiguration.class);
        doReturn(TimeDuration.of("60m")).when(reporterConfiguration).getApiRequestTime();
        doReturn(Collections.singletonList(new URL("http://localhost:" + port))).when(reporterConfiguration).getServerUrls();
        SystemInfo system = new SystemInfo("x64", "localhost", null, "platform");
        final Service service = new Service();
        final ProcessInfo title = new ProcessInfo("title");
        final ProcessorEventHandler processorEventHandler = ProcessorEventHandler.loadProcessors(config);
        ApmServerClient apmServerClient = new ApmServerClient(reporterConfiguration, config.getConfig(CoreConfiguration.class));
        apmServerClient.start();
        v2handler = new IntakeV2ReportingEventHandler(
                reporterConfiguration,
                processorEventHandler,
                new DslJsonSerializer(
                        mock(StacktraceConfiguration.class),
                        apmServerClient,
                        MetaDataMock.create(title, service, system, null, Collections.emptyMap() ,null)
                ),
                apmServerClient);
        reporter = new ApmServerReporter(false, reporterConfiguration, v2handler);
        reporter.start();
    }

    @AfterEach
    void tearDown() {
        reporter.close();
    }

    @Test
    void testReportTransaction() {
        reporter.report(new Transaction(tracer));
        assertThat(reporter.flush(5, TimeUnit.SECONDS)).isTrue();
        assertThat(reporter.getDropped()).isEqualTo(0);
        assertThat(receivedIntakeApiCalls.get()).isEqualTo(1);
        assertThat(reporter.getReported()).isEqualTo(1);
    }

    @Test
    void testReportSpan() {
        reporter.report(new Span(tracer));
        reporter.flush();
        assertThat(reporter.getDropped()).isEqualTo(0);
        assertThat(receivedIntakeApiCalls.get()).isEqualTo(1);
    }

    @Test
    void testSecretToken() {
        doReturn("token").when(reporterConfiguration).getSecretToken();
        handler = exchange -> {
            if (exchange.getRequestPath().equals("/intake/v2/events")) {
                assertThat(exchange.getRequestHeaders().get("Authorization").getFirst()).isEqualTo("Bearer token");
                receivedIntakeApiCalls.incrementAndGet();
            }
            exchange.setStatusCode(200).endExchange();
        };
        reporter.report(new Transaction(tracer));
        reporter.flush();
        assertThat(reporter.getDropped()).isEqualTo(0);
        assertThat(receivedIntakeApiCalls.get()).isEqualTo(1);
    }

    @Test
    void testReportErrorCapture() {
        reporter.report(new ErrorCapture(tracer));
        reporter.flush();
        assertThat(reporter.getDropped()).isEqualTo(0);
        assertThat(receivedIntakeApiCalls.get()).isEqualTo(1);
    }

    @Test
    void testTimeout() {
        doReturn(TimeDuration.of("1ms")).when(reporterConfiguration).getApiRequestTime();
        reporter.report(new Transaction(tracer));
        await().untilAsserted(() -> assertThat(reporter.getReported()).isEqualTo(1));
        assertThat(reporter.getDropped()).isEqualTo(0);
        assertThat(receivedIntakeApiCalls.get()).isEqualTo(1);
        assertThat(receivedEvents.get()).isEqualTo(2);
    }

    @Test
    void testFlush() {
        reporter.report(new Transaction(tracer));
        assertThat(receivedEvents.get()).isEqualTo(0);
        assertThat(reporter.flush(5, TimeUnit.SECONDS)).isTrue();
        assertThat(reporter.getDropped()).isEqualTo(0);
        assertThat(reporter.getReported()).isEqualTo(1);
    }

    @Test
    void testFailingApmServer() {
        statusCode = HttpStatus.SERVICE_UNAVAILABLE_503;
        // try to report a few events to trigger backoff
        reporter.report(new Transaction(tracer));
        reporter.flush(1, TimeUnit.SECONDS);
        reporter.report(new Transaction(tracer));
        reporter.flush(1, TimeUnit.SECONDS);
        reporter.report(new Transaction(tracer));
        reporter.flush(1, TimeUnit.SECONDS);
        reporter.report(new Transaction(tracer));

        assertThat(v2handler.isHealthy()).isFalse();
        assertThat(reporter.flush(1, TimeUnit.SECONDS)).isFalse();
    }

}
