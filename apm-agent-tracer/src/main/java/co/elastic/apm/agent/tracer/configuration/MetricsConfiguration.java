package co.elastic.apm.agent.tracer.configuration;

import java.util.List;

public interface MetricsConfiguration {

    boolean isDedotCustomMetrics();

    List<Double> getCustomMetricsHistogramBoundaries();
}
