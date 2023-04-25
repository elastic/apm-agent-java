package co.elastic.apm.agent.tracer.configuration;

import co.elastic.apm.agent.common.util.WildcardMatcher;

import java.util.List;

public interface ReporterConfiguration {

    long getMetricsIntervalMs();

    List<WildcardMatcher> getDisableMetrics();
}
