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
package co.elastic.apm.agent.impl;

import co.elastic.apm.agent.configuration.CoreConfiguration;
import org.stagemonitor.configuration.ConfigurationOption;
import org.stagemonitor.configuration.ConfigurationOptionProvider;

public class TracerConfiguration extends ConfigurationOptionProvider {
    public static final String RECORDING = "recording";

    private final ConfigurationOption<Boolean> recording = ConfigurationOption.booleanOption()
        .key(RECORDING)
        .aliasKeys("active")
        .tags("added[1.15.0]")
        .configurationCategory(CoreConfiguration.CORE_CATEGORY)
        .description("NOTE: This option was available in older versions through the `active` key. The old key is still \n" +
            "supported in newer versions, but it is now deprecated.\n" +
            "\n" +
            "A boolean specifying if the agent should be recording or not.\n" +
            "When recording, the agent instruments incoming HTTP requests, tracks errors and collects and sends metrics.\n" +
            "When not recording, the agent works as a noop, not collecting data and not communicating with the APM sever,\n" +
            "except for polling the central configuration endpoint.\n" +
            "As this is a reversible switch, agent threads are not being killed when inactivated, but they will be \n" +
            "mostly idle in this state, so the overhead should be negligible.\n" +
            "\n" +
            "You can use this setting to dynamically disable Elastic APM at runtime.")
        .dynamic(true)
        .buildWithDefault(true);

    /**
     * Returns the `recording` configuration option.
     * NOTE: this configuration cannot be used as a global state to be queried by any tracer component like plugins, as
     * it does not determine the tracer state on its own. Therefore, it should remain package private
     *
     * @return the `recording` configuration option
     */
    ConfigurationOption<Boolean> getRecordingConfig() {
        return recording;
    }
}
