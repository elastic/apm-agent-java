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
package co.elastic.apm.agent.configuration;

import co.elastic.apm.agent.configuration.converter.ListValueConverter;
import org.stagemonitor.configuration.ConfigurationOption;
import org.stagemonitor.configuration.ConfigurationOptionProvider;
import org.stagemonitor.configuration.converter.DoubleValueConverter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

public class MetricsConfiguration extends ConfigurationOptionProvider {

    private static final String METRICS_CATEGORY = "Metrics";

    private final ConfigurationOption<Boolean> dedotCustomMetrics = ConfigurationOption.booleanOption()
        .key("dedot_custom_metrics")
        .configurationCategory(METRICS_CATEGORY)
        .description("Replaces dots with underscores in the metric names for Micrometer metrics.\n" +
            "\n" +
            "WARNING: Setting this to `false` can lead to mapping conflicts as dots indicate nesting in Elasticsearch.\n" +
            "An example of when a conflict happens is two metrics with the name `foo` and `foo.bar`.\n" +
            "The first metric maps `foo` to a number and the second metric maps `foo` as an object.")
        .dynamic(true)
        .tags("added[1.22.0]")
        .buildWithDefault(true);

    private final ConfigurationOption<List<Double>> customMetricsHistogramBoundaries = ConfigurationOption.builder(new ListValueConverter<>(DoubleValueConverter.INSTANCE), List.class)
        .key("custom_metrics_histogram_boundaries")
        .configurationCategory(METRICS_CATEGORY)
        .description("Defines the default bucket boundaries to use for OpenTelemetry histograms.")
        .dynamic(false)
        .tags("added[1.37.0]", "experimental")
        .addValidator(new ConfigurationOption.Validator<List<Double>>() {
            @Override
            public void assertValid(List<Double> buckets) {
                if (new HashSet<Double>(buckets).size() != buckets.size()) {
                    throw new IllegalArgumentException("Bucket Boundaries contain duplicates!");
                }
                List<Double> sorted = new ArrayList<>(buckets);
                Collections.sort(sorted);
                if (!sorted.equals(buckets)) {
                    throw new IllegalArgumentException("Bucket Boundaries need to be sorted in ascending order!");
                }
            }
        })
        .buildWithDefault(Arrays.asList(
            0.00390625, 0.00552427, 0.0078125, 0.0110485, 0.015625, 0.0220971, 0.03125, 0.0441942,
            0.0625, 0.0883883, 0.125, 0.176777, 0.25, 0.353553, 0.5, 0.707107, 1.0, 1.41421, 2.0,
            2.82843, 4.0, 5.65685, 8.0, 11.3137, 16.0, 22.6274, 32.0, 45.2548, 64.0, 90.5097, 128.0,
            181.019, 256.0, 362.039, 512.0, 724.077, 1024.0, 1448.15, 2048.0, 2896.31, 4096.0, 5792.62,
            8192.0, 11585.2, 16384.0, 23170.5, 32768.0, 46341.0, 65536.0, 92681.9, 131072.0
        ));

    private final ConfigurationOption<Integer> metricSetLimit = ConfigurationOption.integerOption()
        .key("metric_set_limit")
        .configurationCategory(METRICS_CATEGORY)
        .description("Limits the number of active metric sets.\nThe metrics sets have associated labels, and" +
            " the metrics sets are held internally in a map using the labels as keys. The map is limited in size by this" +
            " option to prevent unbounded growth. If you hit the limit, you'll receive a warning in the agent log.\n" +
            "The recommended option to workaround the limit is to try to limit the cardinality of the labels, eg" +
            " naming your transactions so that there are fewer distinct transaction names.\n" +
            "But if you must, you can use this option to increase the limit.")
        .tags("added[1.33.0]")
        .dynamic(false)
        .buildWithDefault(1000);

    private final ConfigurationOption<Boolean> reporterHealthMetricsEnabled = ConfigurationOption.booleanOption()
        .key("agent_reporter_health_metrics")
        .configurationCategory(METRICS_CATEGORY)
        .description("Enables metrics which capture the health state of the agent's event reporting mechanism.")
        .tags("added[1.35.0]", "experimental")
        .dynamic(false)
        .buildWithDefault(false);

    private final ConfigurationOption<Boolean> overheadMetricsEnabled = ConfigurationOption.booleanOption()
        .key("agent_background_overhead_metrics")
        .configurationCategory(METRICS_CATEGORY)
        .description("Enables metrics which capture the resource consumption of agent background tasks.")
        .tags("added[1.35.0]", "experimental")
        .dynamic(false)
        .buildWithDefault(false);

    public boolean isDedotCustomMetrics() {
        return dedotCustomMetrics.get();
    }

    public int getMetricSetLimit() {
        return metricSetLimit.get();
    }

    public boolean isReporterHealthMetricsEnabled() {
        return reporterHealthMetricsEnabled.get();
    }

    public boolean isOverheadMetricsEnabled() {
        return overheadMetricsEnabled.get();
    }

    public List<Double> getCustomMetricsHistogramBoundaries() {
        return customMetricsHistogramBoundaries.get();
    }
}
