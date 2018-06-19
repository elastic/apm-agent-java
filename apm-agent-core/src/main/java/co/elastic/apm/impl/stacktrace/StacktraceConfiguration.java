/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 Elastic and contributors
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package co.elastic.apm.impl.stacktrace;

import org.stagemonitor.configuration.ConfigurationOption;
import org.stagemonitor.configuration.ConfigurationOptionProvider;

import java.util.Collection;
import java.util.Collections;

public class StacktraceConfiguration extends ConfigurationOptionProvider {

    private static final String STACKTRACE_CATEGORY = "Stacktrace";
    private final ConfigurationOption<Collection<String>> applicationPackages = ConfigurationOption.stringsOption()
        .key("application_packages")
        .configurationCategory(STACKTRACE_CATEGORY)
        .description("Used to determine whether a stack trace frame is an 'in-app frame' or a 'library frame'.")
        .dynamic(true)
        .buildWithDefault(Collections.<String>emptyList());

    private final ConfigurationOption<Integer> stackTraceLimit = ConfigurationOption.integerOption()
        .key("stack_trace_limit")
        .configurationCategory(STACKTRACE_CATEGORY)
        .description("Setting it to 0 will disable stack trace collection. " +
            "Any positive integer value will be used as the maximum number of frames to collect. " +
            "Setting it -1 means that all frames will be collected.")
        .dynamic(true)
        .buildWithDefault(50);

    private final ConfigurationOption<Integer> spanFramesMinDurationMs = ConfigurationOption.integerOption()
        .key("span_frames_min_duration_ms")
        .configurationCategory(STACKTRACE_CATEGORY)
        .description("In its default settings, the APM agent will collect a stack trace with every recorded span.\n" +
            "While this is very helpful to find the exact place in your code that causes the span, " +
            "collecting this stack trace does have some overhead. " +
            "\n" +
            "With the default setting, `-1`, stack traces will be collected for all spans. " +
            "Setting it to a positive value, e.g. `5`, will limit stack trace collection to spans " +
            "with durations equal or longer than the given value in milliseconds, e.g. 5 milliseconds.\n" +
            "\n" +
            "To disable stack trace collection for spans completely, set the value to 0.")
        .dynamic(true)
        .buildWithDefault(5);

    public Collection<String> getApplicationPackages() {
        return applicationPackages.get();
    }

    public int getStackTraceLimit() {
        return stackTraceLimit.get();
    }

    public int getSpanFramesMinDurationMs() {
        return spanFramesMinDurationMs.getValue();
    }
}
