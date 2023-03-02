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
package co.elastic.apm.agent.otelmetricsdk;

import co.elastic.apm.agent.configuration.MetricsConfiguration;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.report.Reporter;
import co.elastic.apm.agent.report.ReporterConfiguration;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;
import co.elastic.apm.agent.util.ExecutorUtils;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.metrics.Aggregation;
import io.opentelemetry.sdk.metrics.InstrumentType;
import io.opentelemetry.sdk.metrics.SdkMeterProviderBuilder;
import io.opentelemetry.sdk.metrics.data.AggregationTemporality;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.export.AggregationTemporalitySelector;
import io.opentelemetry.sdk.metrics.export.MetricExporter;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;

import java.time.Duration;
import java.util.Collection;

public class ElasticOtelMetricsExporter implements MetricExporter {

    private static final Logger logger = LoggerFactory.getLogger(ElasticOtelMetricsExporter.class);

    private static final AggregationTemporalitySelector TEMPORALITY_SELECTOR = AggregationTemporalitySelector.deltaPreferred();

    private final Aggregation defaultHistogramAggregation;

    private final OtelMetricSerializer serializer;

    private final Reporter reporter;

    public static void createAndRegisterOn(SdkMeterProviderBuilder builder, ElasticApmTracer tracer) {
        MetricsConfiguration metricsConfig = tracer.getConfig(MetricsConfiguration.class);
        ReporterConfiguration reporterConfig = tracer.getConfig(ReporterConfiguration.class);

        ElasticOtelMetricsExporter exporter = new ElasticOtelMetricsExporter(tracer.getReporter(), metricsConfig, reporterConfig);

        PeriodicMetricReader metricReader = PeriodicMetricReader.builder(exporter)
            .setExecutor(ExecutorUtils.createSingleThreadSchedulingDaemonPool("otel-metrics-exporter"))
            .setInterval(Duration.ofMillis(reporterConfig.getMetricsIntervalMs()))
            .build();

        builder.registerMetricReader(metricReader);
    }

    private ElasticOtelMetricsExporter(Reporter reporter, MetricsConfiguration metricsConfig, ReporterConfiguration reporterConfig) {
        serializer = new OtelMetricSerializer(metricsConfig, reporterConfig);
        this.reporter = reporter;
        this.defaultHistogramAggregation = Aggregation.explicitBucketHistogram(metricsConfig.getCustomMetricsHistogramBoundaries());
    }


    @Override
    public CompletableResultCode export(Collection<MetricData> metrics) {
        logger.debug("Metrics export called with {} metrics", metrics.size());

        for (MetricData metric : metrics) {
            serializer.addValues(metric);
        }

        serializer.flushAndReset(reporter);
        return CompletableResultCode.ofSuccess();
    }

    @Override
    public CompletableResultCode flush() {
        logger.debug("Flush called");
        return CompletableResultCode.ofSuccess();
    }

    @Override
    public CompletableResultCode shutdown() {
        logger.debug("Shutdown called");
        return CompletableResultCode.ofSuccess();
    }


    @Override
    public AggregationTemporality getAggregationTemporality(InstrumentType instrumentType) {
        return TEMPORALITY_SELECTOR.getAggregationTemporality(instrumentType);
    }

    @Override
    public Aggregation getDefaultAggregation(InstrumentType instrumentType) {
        if (instrumentType == InstrumentType.HISTOGRAM) {
            return defaultHistogramAggregation;
        } else {
            return Aggregation.defaultAggregation();
        }
    }
}
