package co.elastic.apm.reporter;

import co.elastic.apm.report.ApmServerHttpPayloadSender;
import co.elastic.apm.report.PayloadSender;
import co.elastic.apm.report.serialize.PayloadSerializer;
import io.undertow.Undertow;
import okhttp3.OkHttpClient;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.TearDown;

import java.net.InetSocketAddress;

public abstract class AbstractHttpReporterBenchmark extends AbstractReporterBenchmark {
    private Undertow server;
    private int port;

    @Setup
    public void setUp() {
        server = Undertow.builder()
            .addHttpListener(0, "127.0.0.1")
            .setHandler(exchange -> exchange.setStatusCode(200).endExchange()).build();
        server.start();
        port = ((InetSocketAddress) server.getListenerInfo().get(0).getAddress()).getPort();
        super.setUp();
    }

    @Override
    protected PayloadSender getPayloadSender() {
        return new ApmServerHttpPayloadSender(new OkHttpClient(), "http://localhost:" + port, getPayloadSerializer());
    }

    protected abstract PayloadSerializer getPayloadSerializer();

    @TearDown
    public void tearDown() {
        super.tearDown();
        server.stop();
    }
}
