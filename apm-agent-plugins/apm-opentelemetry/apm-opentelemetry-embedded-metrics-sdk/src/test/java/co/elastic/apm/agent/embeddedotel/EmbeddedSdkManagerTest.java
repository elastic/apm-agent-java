package co.elastic.apm.agent.embeddedotel;

import co.elastic.apm.agent.embeddedotel.proxy.ProxyMeter;
import co.elastic.apm.agent.tracer.Tracer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class EmbeddedSdkManagerTest {

    /**
     * The instrumentation of the agent is performed before {@link EmbeddedSdkManager#init(Tracer)} is invoked.
     * This means if the agent is started asynchronously, it can happen that {@link EmbeddedSdkManager#getMeterProvider()}
     * is invoked before the tracer has been provided.
     * This test verifies that in that case no exception occurs and a noop-meter implementation is used.
     */
    @Test
    public void ensureNoExceptionOnMissingTracer() throws Exception {
        ProxyMeter meter = new EmbeddedSdkManager().getMeterProvider().get("foobar");
        assertThat(meter.getDelegate()).isInstanceOf(Class.forName("io.opentelemetry.api.metrics.DefaultMeter"));
    }
}
