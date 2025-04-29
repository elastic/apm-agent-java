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
import co.elastic.apm.agent.configuration.CoreConfigurationImpl;
import co.elastic.apm.agent.configuration.SpyConfiguration;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.error.ErrorCaptureImpl;
import co.elastic.apm.agent.impl.metadata.MetaDataMock;
import co.elastic.apm.agent.impl.metadata.ProcessInfo;
import co.elastic.apm.agent.impl.metadata.ServiceImpl;
import co.elastic.apm.agent.impl.metadata.SystemInfo;
import co.elastic.apm.agent.impl.transaction.SpanImpl;
import co.elastic.apm.agent.impl.transaction.TransactionImpl;
import co.elastic.apm.agent.objectpool.ObjectPoolFactoryImpl;
import co.elastic.apm.agent.report.processor.ProcessorEventHandler;
import co.elastic.apm.agent.report.serialize.DslJsonSerializer;
import co.elastic.apm.agent.report.serialize.SerializationConstants;
import co.elastic.apm.agent.tracer.configuration.TimeDuration;
import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.handlers.BlockingHandler;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.stagemonitor.configuration.ConfigurationRegistry;

import javax.annotation.Nullable;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.Collections;
import java.util.Deque;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.AdditionalMatchers.gt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class ApmServerReporterIntegrationTest {

    private static Undertow server;
    private static int port;
    private static final AtomicInteger receivedIntakeApiCalls = new AtomicInteger();
    private static final AtomicInteger receivedIntakeApiCallsWithFlushParam = new AtomicInteger();
    private static final AtomicInteger receivedEvents = new AtomicInteger();

    @Nullable
    private static HttpHandler handler;
    private final ElasticApmTracer tracer = MockTracer.create();
    private volatile int statusCode = HttpStatus.OK_200;
    private volatile int acceptedEventCount = 0;
    private ReporterConfigurationImpl reporterConfiguration;

    private CoreConfigurationImpl coreConfiguration;
    private ApmServerReporter reporter;

    private ReporterMonitor mockMonitor;

    private IntakeV2ReportingEventHandler v2handler;

    private final AtomicReference<String> token = new AtomicReference<>();
    private final AtomicReference<TimeDuration> timeout = new AtomicReference<>();

    @BeforeAll
    static void startServer() {
        server = Undertow.builder()
            .addHttpListener(0, "127.0.0.1")
            .setHandler(exchange -> Objects.requireNonNull(handler, "handler should be set").handleRequest(exchange))
            .build();
        server.start();
        port = ((InetSocketAddress) server.getListenerInfo().get(0).getAddress()).getPort();
    }

    @AfterAll
    static void stopServer() {
        server.stop();
    }

    @BeforeEach
    void setUp() throws Exception {
        statusCode = HttpStatus.OK_200;
        acceptedEventCount = 0;
        handler = new BlockingHandler(exchange -> {
            exchange.setStatusCode(statusCode);
            if (statusCode < 300 && exchange.getRequestPath().equals("/intake/v2/events")) {
                receivedIntakeApiCalls.incrementAndGet();
                Deque<String> flushedParamValue = exchange.getQueryParameters().get("flushed");
                if (flushedParamValue != null) {
                    assertThat(flushedParamValue.size()).isEqualTo(1);
                    assertThat(flushedParamValue.getFirst()).isEqualTo(Boolean.TRUE.toString());
                    receivedIntakeApiCallsWithFlushParam.incrementAndGet();
                }
                InputStream in = exchange.getInputStream();
                try (in) {
                    for (int n = 0; -1 != n; n = in.read()) {
                        if (n == '\n') {
                            receivedEvents.incrementAndGet();
                        }
                    }
                }
            }
            if (statusCode >= 400) {
                String response = String.format("{ \"accepted\" : %d, \"foo\" : \"bar\" }", acceptedEventCount);
                exchange.getOutputStream().write(response.getBytes());
            }
            exchange.endExchange();
        });

        ConfigurationRegistry config = tracer.getConfigurationRegistry();
        reporterConfiguration = config.getConfig(ReporterConfigurationImpl.class);
        coreConfiguration = config.getConfig(CoreConfigurationImpl.class);
        SerializationConstants.init(coreConfiguration);

        // mockito mocking does not seem to reliably work here
        // thus we rely on mutable test state instead of having different mocking strategies.
        doAnswer(invocationOnMock -> token.get()).when(reporterConfiguration).getSecretToken();
        doAnswer(invocationOnMock -> Optional.ofNullable(timeout.get()).orElseGet(() -> TimeDuration.of("60m")))
            .when(reporterConfiguration).getApiRequestTime();
        doReturn(64).when(reporterConfiguration).getMaxQueueSize();

        doReturn(Collections.singletonList(new URL("http://localhost:" + port))).when(reporterConfiguration).getServerUrls();
        SystemInfo system = new SystemInfo("x64", "localhost", null, "platform");
        final ServiceImpl service = new ServiceImpl();
        final ProcessInfo title = new ProcessInfo("title");
        final ProcessorEventHandler processorEventHandler = ProcessorEventHandler.loadProcessors(config);
        ApmServerClient apmServerClient = new ApmServerClient(config);
        apmServerClient.start();
        DslJsonSerializer payloadSerializer = new DslJsonSerializer(
            SpyConfiguration.createSpyConfig(),
            apmServerClient,
            MetaDataMock.create(title, service, system, null, Collections.emptyMap(), null)
        );
        v2handler = new IntakeV2ReportingEventHandler(
            reporterConfiguration,
            processorEventHandler,
            payloadSerializer,
            apmServerClient);
        mockMonitor = Mockito.mock(ReporterMonitor.class);
        reporter = new ApmServerReporter(false, reporterConfiguration, coreConfiguration, v2handler, mockMonitor, apmServerClient, payloadSerializer, new ObjectPoolFactoryImpl());
        reporter.start();
    }

    @AfterEach
    void tearDown() {
        reporter.close();

        token.set(null);
        timeout.set(null);
        receivedIntakeApiCalls.set(0);
        receivedIntakeApiCallsWithFlushParam.set(0);
        receivedEvents.set(0);
    }

    @Test
    void testReportTransaction() {
        reporter.report(new TransactionImpl(tracer));
        assertThat(reporter.flush(5, TimeUnit.SECONDS, false)).isTrue();
        assertThat(reporter.getDropped()).isEqualTo(0);
        assertThat(receivedIntakeApiCalls.get()).isEqualTo(1);
        assertThat(receivedIntakeApiCallsWithFlushParam.get()).isEqualTo(0);
        assertThat(reporter.getReported()).isEqualTo(1);

        verify(mockMonitor).eventCreated(eq(ReportingEvent.ReportingEventType.TRANSACTION), eq(64L), eq(0L));
        verify(mockMonitor).eventDequeued(eq(ReportingEvent.ReportingEventType.TRANSACTION), eq(64L), anyLong());
        ReportingEventCounter payload = new ReportingEventCounter();
        payload.increment(ReportingEvent.ReportingEventType.TRANSACTION);
        verify(mockMonitor).requestFinished(eq(payload), eq(1L), gt(0L), eq(true));
    }


    @Test
    void testContextPropagationOnlyRespected() {
        doReturn(true).when(coreConfiguration).isContextPropagationOnly();

        reporter.reportPartialTransaction(new TransactionImpl(tracer));
        reporter.report(new TransactionImpl(tracer));
        assertThat(reporter.flush(5, TimeUnit.SECONDS, false)).isTrue();
        assertThat(reporter.getDropped()).isEqualTo(0);
        assertThat(receivedIntakeApiCalls.get()).isEqualTo(0);
        assertThat(receivedIntakeApiCallsWithFlushParam.get()).isEqualTo(0);
        assertThat(reporter.getReported()).isEqualTo(0);

        verifyNoInteractions(mockMonitor);
    }

    @Test
    void testReportTransaction_withFlushRequest() {
        reporter.report(new TransactionImpl(tracer));
        assertThat(receivedEvents.get()).isEqualTo(0);
        assertThat(reporter.flush(5, TimeUnit.SECONDS, true)).isTrue();
        assertThat(reporter.getDropped()).isEqualTo(0);
        assertThat(receivedIntakeApiCalls.get()).isEqualTo(2);
        assertThat(receivedIntakeApiCallsWithFlushParam.get()).isEqualTo(1);
        assertThat(reporter.getReported()).isEqualTo(1);

        verify(mockMonitor).eventCreated(eq(ReportingEvent.ReportingEventType.TRANSACTION), eq(64L), eq(0L));
        verify(mockMonitor).eventDequeued(eq(ReportingEvent.ReportingEventType.TRANSACTION), eq(64L), anyLong());
        ReportingEventCounter payload = new ReportingEventCounter();
        payload.increment(ReportingEvent.ReportingEventType.TRANSACTION);
        verify(mockMonitor).requestFinished(eq(payload), eq(1L), gt(0L), eq(true));
        verify(mockMonitor).requestFinished(eq(new ReportingEventCounter()), eq(0L), gt(0L), eq(true));
    }

    @Test
    void testReportSpan() {
        reporter.report(new SpanImpl(tracer));
        reporter.flush();
        assertThat(reporter.getDropped()).isEqualTo(0);
        assertThat(receivedIntakeApiCalls.get()).isEqualTo(1);
        assertThat(receivedIntakeApiCallsWithFlushParam.get()).isEqualTo(0);

        verify(mockMonitor).eventCreated(eq(ReportingEvent.ReportingEventType.SPAN), eq(64L), eq(0L));
        verify(mockMonitor).eventDequeued(eq(ReportingEvent.ReportingEventType.SPAN), eq(64L), anyLong());
        ReportingEventCounter payload = new ReportingEventCounter();
        payload.increment(ReportingEvent.ReportingEventType.SPAN);
        verify(mockMonitor).requestFinished(eq(payload), eq(1L), gt(0L), eq(true));
    }

    @Test
    void testReportSpan_withFlushRequest() {
        reporter.report(new SpanImpl(tracer));
        reporter.flush(5, TimeUnit.SECONDS, true);
        assertThat(reporter.getDropped()).isEqualTo(0);
        assertThat(receivedIntakeApiCalls.get()).isEqualTo(2);
        assertThat(receivedIntakeApiCallsWithFlushParam.get()).isEqualTo(1);

        verify(mockMonitor).eventCreated(eq(ReportingEvent.ReportingEventType.SPAN), eq(64L), eq(0L));
        verify(mockMonitor).eventDequeued(eq(ReportingEvent.ReportingEventType.SPAN), eq(64L), anyLong());
        ReportingEventCounter payload = new ReportingEventCounter();
        payload.increment(ReportingEvent.ReportingEventType.SPAN);
        verify(mockMonitor).requestFinished(eq(payload), eq(1L), gt(0L), eq(true));
        verify(mockMonitor).requestFinished(eq(new ReportingEventCounter()), eq(0L), gt(0L), eq(true));
    }

    @Test
    void testSecretToken() {
        token.set("token");

        handler = exchange -> {
            if (exchange.getRequestPath().equals("/intake/v2/events")) {
                assertThat(exchange.getRequestHeaders().get("Authorization").getFirst()).isEqualTo("Bearer token");
                receivedIntakeApiCalls.incrementAndGet();
            }
            exchange.setStatusCode(200).endExchange();
        };
        reporter.report(new TransactionImpl(tracer));
        reporter.flush();
        assertThat(reporter.getDropped()).isEqualTo(0);
        assertThat(receivedIntakeApiCalls.get()).isEqualTo(1);
    }

    @Test
    void testReportErrorCapture() {
        reporter.report(new ErrorCaptureImpl(tracer));
        reporter.flush();
        assertThat(reporter.getDropped()).isEqualTo(0);
        assertThat(receivedIntakeApiCalls.get()).isEqualTo(1);

        verify(mockMonitor).eventCreated(eq(ReportingEvent.ReportingEventType.ERROR), eq(64L), eq(0L));
        verify(mockMonitor).eventDequeued(eq(ReportingEvent.ReportingEventType.ERROR), eq(64L), anyLong());
        ReportingEventCounter payload = new ReportingEventCounter();
        payload.increment(ReportingEvent.ReportingEventType.ERROR);
        verify(mockMonitor).requestFinished(eq(payload), eq(1L), gt(0L), eq(true));
    }

    @Test
    void testTimeout() {
        timeout.set(TimeDuration.of("1ms"));
        reporter.report(new TransactionImpl(tracer));
        await().untilAsserted(() -> assertThat(reporter.getReported()).isEqualTo(1));
        assertThat(reporter.getDropped()).isEqualTo(0);
        assertThat(receivedIntakeApiCalls.get()).isEqualTo(1);
        assertThat(receivedEvents.get()).isEqualTo(2);

        verify(mockMonitor).eventCreated(eq(ReportingEvent.ReportingEventType.TRANSACTION), eq(64L), eq(0L));
        verify(mockMonitor).eventDequeued(eq(ReportingEvent.ReportingEventType.TRANSACTION), eq(64L), anyLong());
        ReportingEventCounter payload = new ReportingEventCounter();
        payload.increment(ReportingEvent.ReportingEventType.TRANSACTION);
        verify(mockMonitor).requestFinished(eq(payload), eq(1L), gt(0L), eq(true));
    }

    @Test
    void testFailingApmServer() {
        statusCode = HttpStatus.SERVICE_UNAVAILABLE_503;
        // try to report a few events to trigger backoff
        reporter.report(new TransactionImpl(tracer));
        reporter.flush(1, TimeUnit.SECONDS, false);
        reporter.report(new TransactionImpl(tracer));
        reporter.flush(1, TimeUnit.SECONDS, false);
        reporter.report(new TransactionImpl(tracer));
        reporter.flush(1, TimeUnit.SECONDS, false);
        reporter.report(new TransactionImpl(tracer));

        assertThat(v2handler.isHealthy()).isFalse();
        assertThat(reporter.flush(1, TimeUnit.SECONDS, false)).isFalse();

        ReportingEventCounter payload = new ReportingEventCounter();
        payload.increment(ReportingEvent.ReportingEventType.TRANSACTION);
        verify(mockMonitor, times(2)).requestFinished(eq(payload), eq(0L), gt(0L), eq(false));
    }

    @Test
    void testErrorResponseParsing() {
        statusCode = HttpStatus.SERVICE_UNAVAILABLE_503;
        acceptedEventCount = 2;
        // try to report a few events to trigger backoff
        for (int i = 0; i < 5; i++) {
            reporter.report(new TransactionImpl(tracer));

        }
        reporter.flush(1, TimeUnit.SECONDS, false);

        ReportingEventCounter payload = new ReportingEventCounter();
        payload.add(ReportingEvent.ReportingEventType.TRANSACTION, 5);
        verify(mockMonitor).requestFinished(eq(payload), eq(2L), gt(0L), eq(false));
        assertThat(reporter.getDropped()).isEqualTo(3);
    }

}
