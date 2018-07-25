package co.elastic.apm.report;

import co.elastic.apm.AbstractServletTest;
import co.elastic.apm.configuration.SpyConfiguration;
import co.elastic.apm.impl.ElasticApmTracer;
import co.elastic.apm.impl.ElasticApmTracerBuilder;
import co.elastic.apm.impl.payload.ProcessInfo;
import co.elastic.apm.impl.payload.Service;
import co.elastic.apm.impl.payload.SystemInfo;
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
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.zip.InflaterInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IntakeV2ReportingEventHandlerTest extends AbstractServletTest {

    private static final ObjectMapper objectMapper = new ObjectMapper();
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
            mock(ProcessorEventHandler.class), new DslJsonSerializer(true));
    }

    @Test
    void testReportTransaction() throws Exception {
        final ReportingEvent reportingEvent = new ReportingEvent();
        reportingEvent.setTransaction(new Transaction(mock(ElasticApmTracer.class)));

        reportingEventHandler.onEvent(reportingEvent, 1, true);
        reportingEventHandler.flush();
        TimeUnit.SECONDS.sleep(1);

        final List<JsonNode> ndJsonNodes = getNdJsonNodes();
        assertThat(ndJsonNodes.size()).isGreaterThan(1);
        assertThat(ndJsonNodes.get(0).get("metaData")).isNotNull();
        assertThat(ndJsonNodes.get(1).get("transaction")).isNotNull();
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
