package co.elastic.apm.agent.metrics;

import co.elastic.apm.agent.matcher.WildcardMatcher;
import co.elastic.apm.agent.report.ReporterConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MetricRegistryTest {

    private MetricRegistry metricRegistry;
    private ReporterConfiguration config;

    @BeforeEach
    void setUp() {
        config = mock(ReporterConfiguration.class);
        metricRegistry = new MetricRegistry(config);
    }

    @Test
    void testDisabledMetrics() {
        when(config.getDisableMetrics()).thenReturn(List.of(WildcardMatcher.valueOf("jvm.gc.*")));
        final DoubleSupplier problematicMetric = () -> {
            throw new RuntimeException("Huston, we have a problem");
        };
        metricRegistry.addUnlessNegative("jvm.gc.count", emptyMap(), problematicMetric);
        metricRegistry.addUnlessNan("jvm.gc.count", emptyMap(), problematicMetric);
        metricRegistry.add("jvm.gc.count", emptyMap(), problematicMetric);
        assertThat(metricRegistry.getMetricSets()).isEmpty();
    }
}
