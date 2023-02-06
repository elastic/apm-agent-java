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
package co.elastic.apm.agent.otelmetricexport;

import co.elastic.apm.agent.common.util.WildcardMatcher;
import co.elastic.apm.agent.configuration.MetricsConfiguration;
import co.elastic.apm.agent.report.Reporter;
import co.elastic.apm.agent.report.ReporterConfiguration;
import co.elastic.apm.agent.report.serialize.DslJsonSerializer;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.metrics.data.AggregationTemporality;
import io.opentelemetry.sdk.metrics.data.Data;
import io.opentelemetry.sdk.metrics.data.DoublePointData;
import io.opentelemetry.sdk.metrics.data.HistogramData;
import io.opentelemetry.sdk.metrics.data.HistogramPointData;
import io.opentelemetry.sdk.metrics.data.LongPointData;
import io.opentelemetry.sdk.metrics.data.MetricData;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class OtelMetricSerializer {

    private static final Logger logger = LoggerFactory.getLogger(OtelMetricSerializer.class);
    private final MetricsConfiguration metricsConfig;
    private final ReporterConfiguration reporterConfig;
    private final StringBuilder serializationTempBuilder;
    private final StringBuilder metricNameBuilder;

    private final Set<String> metricsWithBadAggregations = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private final Map<InstrumentationScopeAndTimestamp, Map<Attributes, MetricSetGenerator>> metricSets;

    @Nullable
    private InstrumentationScopeAndTimestamp lastCreatedInstrScopeAndTimestamp;

    public OtelMetricSerializer(MetricsConfiguration metricsConfig, ReporterConfiguration reporterConfig) {
        this.metricsConfig = metricsConfig;
        this.reporterConfig = reporterConfig;
        metricSets = new HashMap<>();
        serializationTempBuilder = new StringBuilder();
        metricNameBuilder = new StringBuilder();
    }

    public void addValues(MetricData metric) {
        String orginalName = metric.getName();

        CharSequence name;
        boolean isEnabled;
        if (metricsConfig.isDedotCustomMetrics()) {
            name = DslJsonSerializer.sanitizePropertyName(orginalName, metricNameBuilder);
            isEnabled = !isMetricDisabled(name) && !isMetricDisabled(orginalName);
        } else {
            name = orginalName;
            isEnabled = !isMetricDisabled(name);
        }
        if (isEnabled) {
            addMetricValues(name, metric);
        }
    }

    private boolean isMetricDisabled(CharSequence name) {
        for (WildcardMatcher matcher : reporterConfig.getDisableMetrics()) {
            if (matcher.matches(name)) {
                return true;
            }
        }
        return false;
    }

    private void addMetricValues(CharSequence sanitizedName, MetricData metric) {
        boolean isDelta;
        String instrumentationScopeName = metric.getInstrumentationScopeInfo().getName();
        switch (metric.getType()) {
            case LONG_GAUGE:
                addLongValues(sanitizedName, instrumentationScopeName, metric.getLongGaugeData(), false);
                break;
            case DOUBLE_GAUGE:
                addDoubleValues(sanitizedName, instrumentationScopeName, metric.getDoubleGaugeData(), false);
                break;
            case LONG_SUM:
                isDelta = metric.getLongSumData().getAggregationTemporality().equals(AggregationTemporality.DELTA);
                addLongValues(sanitizedName, instrumentationScopeName, metric.getLongSumData(), isDelta);
                break;
            case DOUBLE_SUM:
                isDelta = metric.getDoubleSumData().getAggregationTemporality().equals(AggregationTemporality.DELTA);
                addDoubleValues(sanitizedName, instrumentationScopeName, metric.getDoubleSumData(), isDelta);
                break;
            case HISTOGRAM:
                addHistogramValues(sanitizedName, instrumentationScopeName, metric.getHistogramData());
                break;
            case SUMMARY:
            case EXPONENTIAL_HISTOGRAM:
            default:
                if (metricsWithBadAggregations.add(metric.getName())) {
                    logger.warn("Ignoring metric '%s' due to unsupported aggregation '%s'", metric.getName(), metric.getType());
                }
                break;
        }
    }

    private void addHistogramValues(CharSequence name, CharSequence instrScopeName, HistogramData histogramData) {
        for (HistogramPointData histo : histogramData.getPoints()) {
            long timestampMicros = histo.getEpochNanos() / 1000L;
            MetricSetGenerator metricSet = getOrCreateMetricSet(instrScopeName, timestampMicros, histo.getAttributes());
            metricSet.addExplicitBucketHistogram(name, histo.getBoundaries(), histo.getCounts());
        }
    }

    private void addDoubleValues(CharSequence name, CharSequence instrScopeName, Data<DoublePointData> metricValues, boolean omitZeroes) {
        for (DoublePointData data : metricValues.getPoints()) {
            long timestampMicros = data.getEpochNanos() / 1000L;
            if (!omitZeroes || data.getValue() != 0) {
                MetricSetGenerator metricSet = getOrCreateMetricSet(instrScopeName, timestampMicros, data.getAttributes());
                metricSet.addValue(name, data.getValue());
            }
        }
    }

    private void addLongValues(CharSequence name, CharSequence instrScopeName, Data<LongPointData> metricValues, boolean omitZeroes) {
        for (LongPointData data : metricValues.getPoints()) {
            if (!omitZeroes || data.getValue() != 0) {
                long timestampMicros = data.getEpochNanos() / 1000L;
                MetricSetGenerator metricSet = getOrCreateMetricSet(instrScopeName, timestampMicros, data.getAttributes());
                metricSet.addValue(name, data.getValue());
            }
        }
    }


    private MetricSetGenerator getOrCreateMetricSet(CharSequence instrScopeName, long timestampMicros, Attributes attributes) {
        //This function is often called in a loop with the same instrumentation scope name and timestamp
        //In order to minimize allocations, we make use of this fact and remember the map key from the last iteration and reuse it if possible
        InstrumentationScopeAndTimestamp key;
        if (lastCreatedInstrScopeAndTimestamp != null && lastCreatedInstrScopeAndTimestamp.equals(timestampMicros, instrScopeName)) {
            key = lastCreatedInstrScopeAndTimestamp;
        } else {
            key = new InstrumentationScopeAndTimestamp(instrScopeName, timestampMicros);
            lastCreatedInstrScopeAndTimestamp = key;
        }

        Map<Attributes, MetricSetGenerator> timestampMetricSets = metricSets.get(key);
        if (timestampMetricSets == null) {
            timestampMetricSets = new HashMap<>();
            metricSets.put(key, timestampMetricSets);
        }

        MetricSetGenerator ms = timestampMetricSets.get(attributes);
        if (ms == null) {
            ms = new MetricSetGenerator(attributes, key.instrumentationScopeName, key.timestamp, serializationTempBuilder);
            timestampMetricSets.put(attributes, ms);
        }
        return ms;
    }

    public void flushAndReset(Reporter reporter) {
        for (Map<?, MetricSetGenerator> map : metricSets.values()) {
            for (MetricSetGenerator metricSet : map.values()) {
                metricSet.finishAndReport(reporter);
            }
        }
        metricSets.clear();
    }

    private static class InstrumentationScopeAndTimestamp {
        private final long timestamp;
        private final CharSequence instrumentationScopeName;

        public InstrumentationScopeAndTimestamp(CharSequence instrumentationScopeName, long timestamp) {
            this.instrumentationScopeName = instrumentationScopeName;
            this.timestamp = timestamp;
        }

        public boolean equals(long timestamp, CharSequence instrumentationScopeName) {
            return this.timestamp == timestamp && this.instrumentationScopeName.equals(instrumentationScopeName);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            InstrumentationScopeAndTimestamp that = (InstrumentationScopeAndTimestamp) o;
            if (timestamp != that.timestamp) return false;
            return instrumentationScopeName.equals(that.instrumentationScopeName);
        }

        @Override
        public int hashCode() {
            int result = (int) (timestamp ^ (timestamp >>> 32));
            result = 31 * result + instrumentationScopeName.hashCode();
            return result;
        }
    }


}
