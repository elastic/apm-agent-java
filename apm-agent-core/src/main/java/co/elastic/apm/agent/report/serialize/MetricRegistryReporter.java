/*
 * Licensed to Elasticsearch B.V. under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch B.V. licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package co.elastic.apm.agent.report.serialize;

import co.elastic.apm.agent.context.AbstractLifecycleListener;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.metrics.Labels;
import co.elastic.apm.agent.metrics.MetricRegistry;
import co.elastic.apm.agent.metrics.MetricSet;
import co.elastic.apm.agent.report.Reporter;
import co.elastic.apm.agent.report.ReporterConfiguration;
import com.dslplatform.json.JsonWriter;

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
    public void stop() throws Exception {
        // flushing out metrics before shutting down
        // this is especially important for counters as the counts that were accumulated between the last report and the shutdown would otherwise get lost
        tracer.getSharedSingleThreadedPool().submit(this);
    }

    @Override
    public void report(Map<? extends Labels, MetricSet> metricSets) {
        if (tracer.isRunning()) {
            for (MetricSet metricSet : metricSets.values()) {
                JsonWriter jw = serializer.serialize(metricSet);
                if (jw != null) {
                    reporter.report(jw);
                }
            }
        }
    }
}
