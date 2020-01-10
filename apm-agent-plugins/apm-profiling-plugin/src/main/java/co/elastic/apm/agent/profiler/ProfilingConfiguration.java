/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2019 Elastic and contributors
 * %%
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
 * #L%
 */
package co.elastic.apm.agent.profiler;

import co.elastic.apm.agent.configuration.converter.ListValueConverter;
import co.elastic.apm.agent.configuration.converter.TimeDuration;
import co.elastic.apm.agent.configuration.converter.TimeDurationValueConverter;
import co.elastic.apm.agent.configuration.validation.RangeValidator;
import co.elastic.apm.agent.matcher.WildcardMatcher;
import co.elastic.apm.agent.matcher.WildcardMatcherValueConverter;
import org.stagemonitor.configuration.ConfigurationOption;
import org.stagemonitor.configuration.ConfigurationOptionProvider;

import java.util.Arrays;
import java.util.List;

public class ProfilingConfiguration extends ConfigurationOptionProvider {

    private static final String PROFILING_CATEGORY = "Profiling";

    private final ConfigurationOption<Boolean> profilingEnabled = ConfigurationOption.<Boolean>booleanOption()
        .key("profiling_spans_enabled")
        .configurationCategory(PROFILING_CATEGORY)
        .description("Set to `true` to make the agent create spans for method executions based on\n" +
            "https://github.com/jvm-profiling-tools/async-profiler[async-profiler], a sampling aka statistical profiler.\n" +
            "\n" +
            "If this is enabled, the agent will start a profiling session every\n" +
            "<<config-profiling-interval, `profiling_interval`>> which lasts for <<config-profiling-duration, `profiling_duration`>>.\n" +
            "If a transaction happens within a profiling session,\n" +
            "the agent creates spans for slow methods.\n" +
            "\n" +
            "Note that due to the nature of how sampling profilers work,\n" +
            "the duration of the inferred spans are not exact, but only estimations.\n" +
            "The <<config-profiling-sampling-interval, `profiling_sampling_interval`>> lets you fine tune the trade-off between accuracy and overhead.\n" +
            "\n" +
            "NOTE: This feature is not available on Windows")
        .tags("experimental")
        .dynamic(true)
        .tags("added[1.13.0]")
        .buildWithDefault(false);

    private final ConfigurationOption<TimeDuration> samplingInterval = TimeDurationValueConverter.durationOption("ms")
        .key("profiling_sampling_interval")
        .configurationCategory(PROFILING_CATEGORY)
        .dynamic(true)
        .description("The frequency at which stack traces are gathered within a profiling session.\n" +
            "The lower you set it, the more accurate the durations will be.\n" +
            "This comes at the expense of higher overhead and more spans for potentially irrelevant operations.\n" +
            "The minimal duration of a profiling-inferred span is the same as the value of this setting.")
        .addValidator(RangeValidator.min(TimeDuration.of("5ms")))
        .tags("added[1.13.0]")
        .buildWithDefault(TimeDuration.of("20ms"));

    private final ConfigurationOption<List<WildcardMatcher>> includedClasses = ConfigurationOption
        .builder(new ListValueConverter<>(new WildcardMatcherValueConverter()), List.class)
        .key("profiling_included_classes")
        .configurationCategory(PROFILING_CATEGORY)
        .description("If set, the agent will only create inferred spans for methods which match this list.\n" +
            "Setting a value may slightly increase performance and can reduce clutter by only creating spans for the classes you are interested in.\n" +
            "Example: `org.example.myapp.*`\n" +
            "\n" +
            WildcardMatcher.DOCUMENTATION)
        .dynamic(true)
        .tags("added[1.13.0]")
        .buildWithDefault(WildcardMatcher.matchAllList());

    private final ConfigurationOption<List<WildcardMatcher>> excludedClasses = ConfigurationOption
        .builder(new ListValueConverter<>(new WildcardMatcherValueConverter()), List.class)
        .key("profiling_excluded_classes")
        .configurationCategory(PROFILING_CATEGORY)
        .description("Excludes classes for which no profiler-inferred spans should be created.\n" +
            "\n" +
            WildcardMatcher.DOCUMENTATION)
        .dynamic(true)
        .tags("added[1.13.0]")
        .buildWithDefault(Arrays.asList(
            WildcardMatcher.caseSensitiveMatcher("java.*"),
            WildcardMatcher.caseSensitiveMatcher("javax.*"),
            WildcardMatcher.caseSensitiveMatcher("sun.*"),
            WildcardMatcher.caseSensitiveMatcher("com.sun.*"),
            WildcardMatcher.caseSensitiveMatcher("jdk.*")
        ));

    private final ConfigurationOption<TimeDuration> profilerInterval = TimeDurationValueConverter.durationOption("s")
        .key("profiling_interval")
        .description("The interval at which profiling sessions should be started.")
        .configurationCategory(PROFILING_CATEGORY)
        .addValidator(RangeValidator.min(TimeDuration.of("0ms")))
        .dynamic(true)
        .tags("added[1.13.0]")
        .buildWithDefault(TimeDuration.of("61s"));

    private final ConfigurationOption<TimeDuration> profilingDuration = TimeDurationValueConverter.durationOption("s")
        .key("profiling_duration")
        .description("The duration of a profiling session.\n" +
            "For sampled transactions which fall within a profiling session (they start after and end before the session),\n" +
            "so-called inferred spans will be created.\n" +
            "They appear in the trace waterfall view like regular spans.")
        .configurationCategory(PROFILING_CATEGORY)
        .dynamic(true)
        .addValidator(RangeValidator.min(TimeDuration.of("1s")))
        .tags("added[1.13.0]")
        .buildWithDefault(TimeDuration.of("10s"));

    public boolean isProfilingEnabled() {
        return profilingEnabled.get();
    }

    public boolean isProfilingDisabled() {
        return !isProfilingEnabled();
    }

    public TimeDuration getSamplingInterval() {
        return samplingInterval.get();
    }

    public List<WildcardMatcher> getIncludedClasses() {
        return includedClasses.get();
    }

    public List<WildcardMatcher> getExcludedClasses() {
        return excludedClasses.get();
    }

    public TimeDuration getProfilingInterval() {
        return profilerInterval.get();
    }

    public TimeDuration getProfilingDuration() {
        return profilingDuration.get();
    }

    public boolean isNonStopProfiling() {
        return getProfilingDuration().getMillis() >= getProfilingInterval().getMillis();
    }
}
