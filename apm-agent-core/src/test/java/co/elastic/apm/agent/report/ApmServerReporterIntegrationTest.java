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
import co.elastic.apm.agent.configuration.SpyConfiguration;
import co.elastic.apm.agent.configuration.converter.TimeDuration;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.MetaDataMock;
import co.elastic.apm.agent.impl.error.ErrorCapture;
import co.elastic.apm.agent.impl.payload.ProcessInfo;
import co.elastic.apm.agent.impl.payload.Service;
import co.elastic.apm.agent.impl.payload.SystemInfo;
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

import java.net.InetSocketAddress;
import java.net.URL;
import java.util.Collections;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.InflaterInputStream;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.doAnswer;
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
    private final AtomicInteger closeConnectionAfterEveryReceivedEvents = new AtomicInteger(Integer.MAX_VALUE);
    private IntakeV2ReportingEventHandler v2handler;
    private AbstractIntakeApiHandler.NanoClock clock;

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
                InflaterInputStream in = new InflaterInputStream(exchange.getInputStream());
                try (in) {
                    for (int n = 0; -1 != n; n = in.read()) {
                        if (n == '\n') {
                            receivedEvents.incrementAndGet();
                            if (receivedEvents.get() % closeConnectionAfterEveryReceivedEvents.get() == 0) {
                                exchange.getConnection().close();
                                return;
                            }
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
        SystemInfo system = new SystemInfo("x64", "localhost", "platform");
        final Service service = new Service();
        final ProcessInfo title = new ProcessInfo("title");
        final ProcessorEventHandler processorEventHandler = ProcessorEventHandler.loadProcessors(config);
        ApmServerClient apmServerClient = new ApmServerClient(reporterConfiguration);
        apmServerClient.start();
        clock = mock(AbstractIntakeApiHandler.NanoClock.class);
        v2handler = new IntakeV2ReportingEventHandler(
                reporterConfiguration,
                processorEventHandler,
                new DslJsonSerializer(
                        mock(StacktraceConfiguration.class),
                        apmServerClient,
                        MetaDataMock.create(title, service, system, null, Collections.emptyMap())
                ),
                apmServerClient,
                clock);
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
        assertThat(reporter.hardFlush(5, TimeUnit.SECONDS)).isTrue();
        assertThat(reporter.getDropped()).isEqualTo(0);
        assertThat(receivedIntakeApiCalls.get()).isEqualTo(1);
        assertThat(reporter.getReported()).isEqualTo(1);
    }

    @Test
    void testReportSpan() throws ExecutionException, InterruptedException {
        reporter.report(new Span(tracer));
        reporter.waitForHardFlush();
        assertThat(reporter.getDropped()).isEqualTo(0);
        assertThat(receivedIntakeApiCalls.get()).isEqualTo(1);
    }

    @Test
    void testSecretToken() throws ExecutionException, InterruptedException {
        doReturn("token").when(reporterConfiguration).getSecretToken();
        handler = exchange -> {
            if (exchange.getRequestPath().equals("/intake/v2/events")) {
                assertThat(exchange.getRequestHeaders().get("Authorization").getFirst()).isEqualTo("Bearer token");
                receivedIntakeApiCalls.incrementAndGet();
            }
            exchange.setStatusCode(200).endExchange();
        };
        reporter.report(new Transaction(tracer));
        reporter.waitForHardFlush();
        assertThat(reporter.getDropped()).isEqualTo(0);
        assertThat(receivedIntakeApiCalls.get()).isEqualTo(1);
    }

    @Test
    void testReportErrorCapture() throws ExecutionException, InterruptedException {
        reporter.report(new ErrorCapture(tracer));
        reporter.waitForHardFlush();
        assertThat(reporter.getDropped()).isEqualTo(0);
        assertThat(receivedIntakeApiCalls.get()).isEqualTo(1);
    }

    @Test
    void testTimeout() throws InterruptedException {
        doReturn(TimeDuration.of("1ms")).when(reporterConfiguration).getApiRequestTime();
        doAnswer(invocation -> System.nanoTime()).when(clock).nanoTicks();
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

        assertThat(reporter.softFlush(5, TimeUnit.SECONDS)).isTrue();
        // the metadata and the transaction event are now flushed to the network
        // they should arrive at the server momentarily
        await().untilAsserted(() -> assertThat(receivedEvents.get()).isEqualTo(2));
        assertThat(reporter.getReported()).isEqualTo(0);

        assertThat(reporter.hardFlush(5, TimeUnit.SECONDS)).isTrue();
        assertThat(reporter.getDropped()).isEqualTo(0);
        assertThat(reporter.getReported()).isEqualTo(1);
    }

    @Test
    void testFailingApmServer() {
        statusCode = HttpStatus.SERVICE_UNAVAILABLE_503;
        // try to report a few events to trigger backoff
        reporter.report(new Transaction(tracer));
        reporter.hardFlush(1, TimeUnit.SECONDS);
        reporter.report(new Transaction(tracer));
        reporter.hardFlush(1, TimeUnit.SECONDS);
        reporter.report(new Transaction(tracer));
        reporter.hardFlush(1, TimeUnit.SECONDS);
        reporter.report(new Transaction(tracer));

        assertThat(v2handler.isHealthy()).isFalse();
        assertThat(reporter.softFlush(1, TimeUnit.SECONDS)).isFalse();
        assertThat(reporter.hardFlush(1, TimeUnit.SECONDS)).isFalse();
    }

    @Test
    void testConnectionClosedByApmServer() {
        // tests that we can sustain APM Server closing the connection without going into a backoff
        closeConnectionAfterEveryReceivedEvents.set(2);

        int expectedReceivedEvents = 0;
        int expectedIntakeApiCalls = 0;
        // doing this for a couple of times makes sure we don't trigger the exponential backoff
        for (int i = 0; i < 5; i++) {
            sendTransactionEventAndFlush(expectedReceivedEvents += 2, expectedIntakeApiCalls += 1);
            // connection is now closed by the server
            assertThat(v2handler.isHealthy()).isTrue();
            // advance the clock so that on the next event, the request will be closed
            // the SocketException due to the closed connection will be ignored because the request has been open for one second beyond the threshold
            doReturn((i + 1) * TimeUnit.MILLISECONDS.toNanos(reporterConfiguration.getApiRequestTime().getMillis()) + TimeUnit.SECONDS.toNanos(1))
                .when(clock).nanoTicks();
        }
    }

    private void sendTransactionEventAndFlush(int expectedReceivedEvents, int expectedIntakeApiCalls) {
        reporter.report(new Transaction(tracer));
        // after the flush, the metadata and the transaction event will be received by the server
        assertThat(reporter.softFlush(1, TimeUnit.SECONDS)).isTrue();
        await().untilAsserted(() -> assertThat(receivedEvents.get()).isEqualTo(expectedReceivedEvents));
        assertThat(receivedIntakeApiCalls.get()).isEqualTo(expectedIntakeApiCalls);
    }
}
