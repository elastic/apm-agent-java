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

import co.elastic.apm.AbstractServletTest;
import co.elastic.apm.configuration.SpyConfiguration;
import co.elastic.apm.impl.ElasticApmTracer;
import co.elastic.apm.impl.ElasticApmTracerBuilder;
import co.elastic.apm.impl.error.ErrorCapture;
import co.elastic.apm.impl.payload.ProcessInfo;
import co.elastic.apm.impl.payload.Service;
import co.elastic.apm.impl.payload.SystemInfo;
import co.elastic.apm.impl.stacktrace.StacktraceConfiguration;
import co.elastic.apm.impl.transaction.Span;
import co.elastic.apm.impl.transaction.Transaction;
import co.elastic.apm.report.processor.ProcessorEventHandler;
import co.elastic.apm.report.serialize.DslJsonSerializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.Response;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.stagemonitor.configuration.ConfigurationRegistry;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.zip.InflaterInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IntakeV2ReportingEventHandlerTest extends AbstractServletTest {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static CountDownLatch serverReceivedPayload = new CountDownLatch(1);
    private IntakeV2ReportingEventHandler reportingEventHandler;

    @Nonnull
    private static JsonNode getReadTree(String s) {
        try {
            return objectMapper.readTree(s);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @BeforeEach
    void setUp() {
        final ConfigurationRegistry configurationRegistry = SpyConfiguration.createSpyConfig();
        new ElasticApmTracerBuilder().configurationRegistry(configurationRegistry).build();
        final ReporterConfiguration reporterConfiguration = configurationRegistry.getConfig(ReporterConfiguration.class);
        when(reporterConfiguration.getServerUrl()).thenReturn("http://localhost:" + getPort());
        SystemInfo system = new SystemInfo("x64", "localhost", "platform");
        reportingEventHandler = new IntakeV2ReportingEventHandler(new Service(), new ProcessInfo("title"), system,
            reporterConfiguration,
            mock(ProcessorEventHandler.class), new DslJsonSerializer(true, mock(StacktraceConfiguration.class)));
        serverReceivedPayload = new CountDownLatch(1);
    }

    @Test
    void testReport() throws Exception {
        reportTransaction();
        reportSpan();
        reportError();
        reportingEventHandler.flush();
        serverReceivedPayload.await(5, TimeUnit.SECONDS);

        final List<JsonNode> ndJsonNodes = getNdJsonNodes();
        assertThat(ndJsonNodes).hasSize(4);
        assertThat(ndJsonNodes.get(0).get("metadata")).isNotNull();
        assertThat(ndJsonNodes.get(1).get("transaction")).isNotNull();
        assertThat(ndJsonNodes.get(2).get("span")).isNotNull();
        assertThat(ndJsonNodes.get(3).get("error")).isNotNull();
    }

    @Test
    void testExponentialBackoff() {
        assertThat(IntakeV2ReportingEventHandler.getBackoffTimeSeconds(0)).isEqualTo(0);
        assertThat(IntakeV2ReportingEventHandler.getBackoffTimeSeconds(1)).isEqualTo(1);
        assertThat(IntakeV2ReportingEventHandler.getBackoffTimeSeconds(2)).isEqualTo(4);
        assertThat(IntakeV2ReportingEventHandler.getBackoffTimeSeconds(3)).isEqualTo(9);
        assertThat(IntakeV2ReportingEventHandler.getBackoffTimeSeconds(4)).isEqualTo(16);
        assertThat(IntakeV2ReportingEventHandler.getBackoffTimeSeconds(5)).isEqualTo(25);
        assertThat(IntakeV2ReportingEventHandler.getBackoffTimeSeconds(6)).isEqualTo(36);
        assertThat(IntakeV2ReportingEventHandler.getBackoffTimeSeconds(7)).isEqualTo(36);
    }

    private void reportTransaction() throws IOException {
        final ReportingEvent reportingEvent = new ReportingEvent();
        reportingEvent.setTransaction(new Transaction(mock(ElasticApmTracer.class)));

        reportingEventHandler.onEvent(reportingEvent, -1, true);
    }

    private void reportSpan() throws IOException {
        final ReportingEvent reportingEvent = new ReportingEvent();
        reportingEvent.setSpan(new Span(mock(ElasticApmTracer.class)));

        reportingEventHandler.onEvent(reportingEvent, -1, true);
    }

    private void reportError() throws IOException {
        final ReportingEvent reportingEvent = new ReportingEvent();
        reportingEvent.setError(new ErrorCapture());

        reportingEventHandler.onEvent(reportingEvent, -1, true);
    }

    private List<JsonNode> getNdJsonNodes() throws IOException {
        final Response response = get("/v2/intake");
        if (response.isSuccessful()) {
            return new BufferedReader(new InputStreamReader(response.body().byteStream())).lines()
                .map(IntakeV2ReportingEventHandlerTest::getReadTree)
                .collect(Collectors.toList());
        }
        throw new RuntimeException(response.toString());
    }

    @Override
    protected void setUpHandler(ServletContextHandler handler) {
        handler.addServlet(EchoServlet.class, "/v2/intake");
    }

    public static class EchoServlet extends HttpServlet {

        private static final Logger logger = LoggerFactory.getLogger(EchoServlet.class);

        @Nullable
        private byte[] bytes;

        @Override
        protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            assertThat(req.getHeader("Content-Encoding")).isEqualTo("deflate");
            assertThat(req.getHeader("Transfer-Encoding")).isEqualTo("chunked");
            assertThat(req.getHeader("Content-Type")).isEqualTo("application/x-ndjson");
            assertThat(req.getContentLength()).isEqualTo(-1);
            InputStream in = req.getInputStream();
            in = new InflaterInputStream(in);
            bytes = in.readAllBytes();
            logger.info("Received payload with {} bytes:\n{}", bytes.length, new String(bytes));
            serverReceivedPayload.countDown();
        }

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            if (bytes == null) {
                resp.sendError(400, "Did not receive a payload yet");
            } else {
                resp.getOutputStream().write(bytes);
            }
            bytes = null;
        }
    }
}
