package co.elastic.apm.agent.report.serialize;

import co.elastic.apm.agent.context.AbstractLifecycleListener;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.metrics.Labels;
import co.elastic.apm.agent.metrics.MetricRegistry;
import co.elastic.apm.agent.metrics.MetricSet;
import co.elastic.apm.agent.report.Reporter;
import co.elastic.apm.agent.report.ReporterConfiguration;

import java.util.Map;
import java.util.concurrent.TimeUnit;

public class MetricRegistryReporter extends AbstractLifecycleListener implements MetricRegistry.MetricsReporter, Runnable {

    private final Reporter reporter;
    private final ElasticApmTracer tracer;
    private final MetricRegistry metricRegistry;
    private final MetricRegistrySerializer serializer;

    public MetricRegistryReporter(ElasticApmTracer tracer) {
        this.tracer = tracer;
        this.reporter = tracer.getReporter();
        this.metricRegistry = tracer.getMetricRegistry();
        this.serializer = new MetricRegistrySerializer();
    }

    @Override
    public void start(ElasticApmTracer tracer) {
        long intervalMs = tracer.getConfig(ReporterConfiguration.class).getMetricsIntervalMs();
        if (intervalMs > 0) {
            tracer.getSharedSingleThreadedPool().scheduleAtFixedRate(this, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
        }
    }

    @Override
    public void run() {
        metricRegistry.flipPhaseAndReport(this);
    }

    @Override
    public void report(Map<? extends Labels, MetricSet> metricSets) {
        if (tracer.isRunning()) {
            reporter.report(serializer.serialize(metricSets));
        }
    }
}
