package co.elastic.apm.report;

import co.elastic.apm.impl.error.ErrorCapture;
import co.elastic.apm.impl.transaction.Transaction;
import co.elastic.apm.impl.payload.ProcessInfo;
import co.elastic.apm.impl.payload.Service;
import co.elastic.apm.impl.payload.SystemInfo;
import co.elastic.apm.report.serialize.JacksonPayloadSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.afterburner.AfterburnerModule;
import io.undertow.Undertow;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

class ApmServerReporterIntegrationTest {

    private static Undertow server;
    private static int port;
    private static AtomicInteger receivedHttpRequests = new AtomicInteger();
    private ApmServerHttpPayloadSender payloadSender;
    private ReporterConfiguration reporterConfiguration;
    private ApmServerReporter reporter;

    @BeforeAll
    static void startServer() {
        server = Undertow.builder()
            .addHttpListener(0, "127.0.0.1")
            .setHandler(exchange -> {
                receivedHttpRequests.incrementAndGet();
                exchange.setStatusCode(200).endExchange();
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
        receivedHttpRequests.set(0);
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new AfterburnerModule());
        reporterConfiguration = spy(new ReporterConfiguration());
        when(reporterConfiguration.getFlushInterval()).thenReturn(-1);
        when(reporterConfiguration.getServerUrl()).thenReturn("http://localhost:" + port);
        payloadSender = new ApmServerHttpPayloadSender(new OkHttpClient(), new JacksonPayloadSerializer(objectMapper), reporterConfiguration);
        SystemInfo system = new SystemInfo("x64", "localhost", "platform");
        reporter = new ApmServerReporter(new Service(), new ProcessInfo(), system, payloadSender, false, reporterConfiguration);
    }

    @Test
    void testReportTransaction() throws ExecutionException, InterruptedException {
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
