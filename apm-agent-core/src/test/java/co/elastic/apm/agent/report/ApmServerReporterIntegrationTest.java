/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2020 Elastic and contributors
 * %%
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
 * #L%
 */
package co.elastic.apm.agent.report;

import co.elastic.apm.agent.MockTracer;
import co.elastic.apm.agent.configuration.SpyConfiguration;
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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.stagemonitor.configuration.ConfigurationRegistry;

import java.net.InetSocketAddress;
import java.net.URL;
import java.util.Collections;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

class ApmServerReporterIntegrationTest {

    private static Undertow server;
    private static int port;
    private static AtomicInteger receivedIntakeApiCalls = new AtomicInteger();
    private static HttpHandler handler;
    private final ElasticApmTracer tracer = MockTracer.create();
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
    void setUp() throws Exception {
        handler = exchange -> {
            if (exchange.getRequestPath().equals("/intake/v2/events")) {
                receivedIntakeApiCalls.incrementAndGet();
            }
            exchange.setStatusCode(200).endExchange();
        };
        receivedIntakeApiCalls.set(0);
        ConfigurationRegistry config = SpyConfiguration.createSpyConfig();
        reporterConfiguration = config.getConfig(ReporterConfiguration.class);
        doReturn(Collections.singletonList(new URL("http://localhost:" + port))).when(reporterConfiguration).getServerUrls();
        SystemInfo system = new SystemInfo("x64", "localhost", "platform");
        final Service service = new Service();
        final ProcessInfo title = new ProcessInfo("title");
        final ProcessorEventHandler processorEventHandler = ProcessorEventHandler.loadProcessors(config);
        ApmServerClient apmServerClient = new ApmServerClient(reporterConfiguration);
        apmServerClient.start();
        final IntakeV2ReportingEventHandler v2handler = new IntakeV2ReportingEventHandler(
                reporterConfiguration,
                processorEventHandler,
                new DslJsonSerializer(
                        mock(StacktraceConfiguration.class),
                        apmServerClient,
                        MetaDataMock.create(title, service, system, null, Collections.emptyMap())
                ),
                apmServerClient);
        reporter = new ApmServerReporter(false, reporterConfiguration, v2handler);
        reporter.start();
    }

    @Test
    void testReportTransaction() throws ExecutionException, InterruptedException {
        reporter.report(new Transaction(tracer));
        reporter.flush().get();
        assertThat(reporter.getDropped()).isEqualTo(0);
        assertThat(receivedIntakeApiCalls.get()).isEqualTo(1);
    }

    @Test
    void testReportSpan() throws ExecutionException, InterruptedException {
        reporter.report(new Span(tracer));
        reporter.flush().get();
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
        reporter.flush().get();
        assertThat(reporter.getDropped()).isEqualTo(0);
        assertThat(receivedIntakeApiCalls.get()).isEqualTo(1);
    }

    @Test
    void testReportErrorCapture() throws ExecutionException, InterruptedException {
        reporter.report(new ErrorCapture(tracer));
        reporter.flush().get();
        assertThat(reporter.getDropped()).isEqualTo(0);
        assertThat(receivedIntakeApiCalls.get()).isEqualTo(1);
    }

}
