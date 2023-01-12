package co.elastic.apm.agent.otelmetricsdk;

import co.elastic.apm.agent.AbstractInstrumentationTest;
import co.elastic.apm.agent.report.ReporterConfiguration;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

public class MetricExportTimingTest extends AbstractInstrumentationTest {

    /**
     * This test has been separated from the other because we don't want it to run in the integration tests: It would be too slow.
     */
    @Test
    void testMetricExportIntervalRespected() throws Exception {
        ReporterConfiguration reporterConfig = tracer.getConfig(ReporterConfiguration.class);
        doReturn(50L).when(reporterConfig).getMetricsIntervalMs();

        try (SdkMeterProvider meterProvider = SdkMeterProvider.builder().build()) {
            Meter meter = meterProvider.meterBuilder("test").build();

            meter.gaugeBuilder("my_gauge").buildWithCallback(obs -> obs.record(42.0));

            Thread.sleep(1000);

            //To account for CI slowness, we try to check that the number of exports is just in the correct ballpark
            assertThat(reporter.getBytes().size())
                .isBetween(10, 21);

        }

    }
}
