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
package co.elastic.apm.agent.testutils.assertions.metrics;

import co.elastic.apm.agent.testutils.assertions.BaseAssert;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

public class MetricSetAssert extends BaseAssert<MetricSetAssert, MetricSetJson> {

    protected MetricSetAssert(MetricSetJson metricSet) {
        super(metricSet, MetricSetAssert.class);
    }

    public MetricSetAssert hasMetricsCount(int expected) {
        int count = Optional.ofNullable(actual.samples).map(Map::size).orElse(0);
        if (count != expected) {
            failWithMessage("Expected metricset to contain %d metrics but contained %d.", expected, count);
        }
        return this;
    }

    public MetricSetAssert containsMetric(String name) {
        extractMetric(name);
        return this;
    }

    public MetricSetAssert metricSatisfies(String name, Consumer<MetricJson> assertions) {
        assertions.accept(extractMetric(name));
        return this;
    }

    public MetricSetAssert containsValueMetric(String name, Number expected) {
        MetricJson metric = extractMetric(name);
        if (!Objects.equals(metric.value, expected)) {
            failWithMessage("Expected metric '%s' to have metric value '%s' but was '%s'", name, expected, metric.value);
        }
        if (!Objects.equals(metric.values, null)) {
            failWithMessage("Expected metric '%s' to have metric value but did contain histogram values %s", name, metric.values);
        }
        if (!Objects.equals(metric.counts, null)) {
            failWithMessage("Expected metric '%s' to have metric value but did contain histogram counts %s", name, metric.counts);
        }
        return this;
    }

    public MetricSetAssert containsHistogramMetric(String name, Collection<Number> expectedValues, Collection<Long> expectedCounts) {
        MetricJson metric = extractMetric(name);
        if (!Objects.equals(metric.values, expectedValues)) {
            failWithMessage("Expected metric '%s' to have histogram values '%s' but was '%s'", name, expectedValues, metric.values);
        }
        if (!Objects.equals(metric.counts, expectedCounts)) {
            failWithMessage("Expected metric '%s' to have histogram counts '%s' but was '%s'", name, expectedCounts, metric.counts);
        }
        if (!Objects.equals(metric.type, "histogram")) {
            failWithMessage("Expected metric '%s' to have a histogram type but actual type was %s", name, metric.type);
        }
        if (!Objects.equals(metric.value, null)) {
            failWithMessage("Expected metric '%s' to have a histogram value but it did contain plain value %s", name, metric.value);
        }
        return this;
    }

    public MetricSetAssert containsMetrics(String... names) {
        for (String name : names) {
            containsMetric(name);
        }
        return this;
    }

    public MetricSetAssert containsExactlyMetrics(String... names) {
        if (actual.samples == null) {
            if (names.length > 0) {
                failWithMessage("Expected metricset to contain metrics %s but 'samples' was null", Arrays.toString(names));
            }
        }
        for (String name : names) {
            containsMetric(name);
        }
        if (names.length > actual.samples.size()) {
            Set<String> expectedNames = new HashSet<>(Arrays.asList(names));
            for (String name : actual.samples.keySet()) {
                if (!expectedNames.contains(name)) {
                    failWithMessage("Expected metricset to not contain metric '%s' but was found", name);
                }
            }
        }
        return this;
    }

    private MetricJson extractMetric(String name) {
        if (actual.samples == null) {
            failWithMessage("Expected metric with name '%s' to exist but metricset 'samples' was null", name);
        }
        MetricJson metric = actual.samples.get(name);
        if (metric == null) {
            failWithMessage("Expected metric with name '%s' to exist but metricset only contains %s", name, actual.samples.keySet());
        }
        return metric;
    }

    public MetricSetAssert hasTimestampInRange(long min, long max) {
        assertThat(actual.timestamp).isBetween(min, max);
        return this;
    }
}
