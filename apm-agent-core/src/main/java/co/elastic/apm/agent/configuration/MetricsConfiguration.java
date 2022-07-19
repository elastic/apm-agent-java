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
        .description("Limits the number of active metric sets.\nThe metrics sets are based on transaction names and are" +
            " held internally in a collection which we limit in size to prevent leaking. If you hit the limit, you'll receive" +
            "a warning in the agent log. The better option to workaround the limit is to try to name your transactions so" +
            " that there are fewer distinct transaction names.\nBut if you must, you can use this option to increase the limit.")
        .tags("added[1.33.0]")
        .dynamic(false)
        .buildWithDefault(1000);

    public boolean isDedotCustomMetrics() {
        return dedotCustomMetrics.get();
    }

    public int getMetricSetLimit() {
        return metricSetLimit.get();
    }
}
