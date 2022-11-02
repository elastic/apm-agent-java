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

import org.stagemonitor.configuration.ConfigurationOption;
import org.stagemonitor.configuration.ConfigurationOptionProvider;

public class MetricsConfiguration extends ConfigurationOptionProvider {

    private static final String METRICS_CATEGORY = "Metrics";

    private final ConfigurationOption<Boolean> dedotCustomMetrics = ConfigurationOption.booleanOption()
        .key("dedot_custom_metrics")
        .configurationCategory(METRICS_CATEGORY)
        .description("Replaces dots with underscores in the metric names for custom metrics, such as Micrometer metrics.\n" +
            "\n" +
            "WARNING: Setting this to `false` can lead to mapping conflicts as dots indicate nesting in Elasticsearch.\n" +
            "An example of when a conflict happens is two metrics with the name `foo` and `foo.bar`.\n" +
            "The first metric maps `foo` to a number and the second metric maps `foo` as an object.")
        .dynamic(true)
        .tags("added[1.22.0]")
        .buildWithDefault(true);

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
        .description("Enables metrics which capture the resource consumption of agent background tasks.\n" +
            "Disabled by default because this measurement itself can cause some overhead.")
        .tags("added[1.35.0]")
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
}
