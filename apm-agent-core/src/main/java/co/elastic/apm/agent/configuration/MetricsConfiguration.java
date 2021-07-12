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

    private final ConfigurationOption<Boolean> dedotCustomMetrics = ConfigurationOption.booleanOption()
        .key("dedot_custom_metrics")
        .configurationCategory("Metrics")
        .description("Replaces dots with underscores in the metric names for custom metrics, such as Micrometer metrics.\n" +
            "\n" +
            "WARNING: Setting this to `false` can lead to mapping conflicts as dots indicate nesting in Elasticsearch.\n" +
            "An example of when a conflict happens is two metrics with the name `foo` and `foo.bar`.\n" +
            "The first metric maps `foo` to a number and the second metric maps `foo` as an object.")
        .dynamic(true)
        .tags("added[1.22.0]")
        .buildWithDefault(true);

    public boolean isDedotCustomMetrics() {
        return dedotCustomMetrics.get();
    }
}
