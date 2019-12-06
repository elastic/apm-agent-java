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

    private final ConfigurationOption<TimeDuration> sampleRate = TimeDurationValueConverter.durationOption("ms")
        .key("profiling_sample_rate")
        .configurationCategory(PROFILING_CATEGORY)
        .dynamic(true)
        .addValidator(RangeValidator.min(TimeDuration.of("10ms")))
        .buildWithDefault(TimeDuration.of("20ms"));

    private final ConfigurationOption<List<WildcardMatcher>> excludedClasses = ConfigurationOption
        .builder(new ListValueConverter<>(new WildcardMatcherValueConverter()), List.class)
        .key("profiling_excluded_classes")
        .configurationCategory(PROFILING_CATEGORY)
        .tags("internal")
        .description("\n" +
            "\n" +
            WildcardMatcher.DOCUMENTATION)
        .dynamic(true)
        .buildWithDefault(Arrays.asList(
            WildcardMatcher.caseSensitiveMatcher("java.*"),
            WildcardMatcher.caseSensitiveMatcher("javax.*"),
            WildcardMatcher.caseSensitiveMatcher("sun.*"),
            WildcardMatcher.caseSensitiveMatcher("com.sun.*"),
            WildcardMatcher.caseSensitiveMatcher("jdk.*")
        ));

    private final ConfigurationOption<TimeDuration> profilerDelay = TimeDurationValueConverter.durationOption("s")
        .key("profiling_delay")
        .description("The delay between profiling session.")
        .configurationCategory(PROFILING_CATEGORY)
        .addValidator(RangeValidator.min(TimeDuration.of("0ms")))
        .buildWithDefault(TimeDuration.of("53s"));

    private final ConfigurationOption<TimeDuration> profilingDuration = TimeDurationValueConverter.durationOption("s")
        .key("profiling_duration")
        .description("The duration of a profiling session.\n" +
            "For sampled transactions which fall within a profiling session (they start after and end before the session),\n" +
            "so-called inferred spans will be created.\n" +
            "They appear in the trace waterfall view like regular spans.")
        .configurationCategory(PROFILING_CATEGORY)
        .addValidator(RangeValidator.min(TimeDuration.of("1s")))
        .buildWithDefault(TimeDuration.of("8s"));

    public TimeDuration getSampleRate() {
        return sampleRate.get();
    }

    public List<WildcardMatcher> getExcludedClasses() {
        return excludedClasses.get();
    }

    public TimeDuration getProfilingDelay() {
        return profilerDelay.get();
    }

    public TimeDuration getProfilingDuration() {
        return profilingDuration.get();
    }
}
