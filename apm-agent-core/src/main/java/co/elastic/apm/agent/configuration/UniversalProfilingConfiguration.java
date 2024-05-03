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

import static co.elastic.apm.agent.tracer.configuration.RangeValidator.isInRange;

public class UniversalProfilingConfiguration extends ConfigurationOptionProvider {

    private static final String PROFILING_CATEGORY = "Profiling";

    private final ConfigurationOption<Boolean> enabled = ConfigurationOption.booleanOption()
        .key("universal_profiling_integration_enabled")
        .tags("added[1.50.0]")
        .configurationCategory(PROFILING_CATEGORY)
        .description("If enabled, the apm agent will correlate it's transaction with the profiling data from elastic universal profiling running on the same host.")
        .buildWithDefault(false);

    private final ConfigurationOption<Integer> bufferSize = ConfigurationOption.integerOption()
        .key("universal_profiling_integration_buffer_size")
        .addValidator(isInRange(64, Integer.MAX_VALUE))
        .tags("added[1.50.0]")
        .configurationCategory(PROFILING_CATEGORY)
        .description("The feature needs to buffer ended local-root spans for a short duration to ensure that all of its profiling data has been received." +
                     "This configuration option configures the buffer size in number of spans. " +
                     "The higher the number of local root spans per second, the higher this buffer size should be set.\n" +
                     "The agent will log a warning if it is not capable of buffering a span due to insufficient buffer size. " +
                     "This will cause the span to be exported immediately instead with possibly incomplete profiling correlation data.")
        .buildWithDefault(4096);

    private final ConfigurationOption<String> socketDir = ConfigurationOption.stringOption()
        .key("universal_profiling_integration_socket_dir")
        .tags("added[1.50.0]")
        .configurationCategory(PROFILING_CATEGORY)
        .description("The extension needs to bind a socket to a file for communicating with the universal profiling host agent." +
                     "This configuration option can be used to change the location. " +
                     "Note that the total path name (including the socket) must not exceed 100 characters due to OS restrictions.\n" +
                     "If unset, the value of the `java.io.tmpdir` system property will be used.")
        .build();

    public boolean isEnabled() {
        return enabled.get();
    }

    public int getBufferSize() {
        return bufferSize.get();
    }

    public String getSocketDir() {
        String dir = socketDir.get();
        return dir == null || dir.isEmpty() ? System.getProperty("java.io.tmpdir") : dir;
    }

}
