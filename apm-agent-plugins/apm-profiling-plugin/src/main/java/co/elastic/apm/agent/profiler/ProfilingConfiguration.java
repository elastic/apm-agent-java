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
package co.elastic.apm.agent.profiler;

import co.elastic.apm.agent.common.util.WildcardMatcher;
import co.elastic.apm.agent.tracer.configuration.ListValueConverter;
import co.elastic.apm.agent.tracer.configuration.TimeDuration;
import co.elastic.apm.agent.tracer.configuration.TimeDurationValueConverter;
import co.elastic.apm.agent.tracer.configuration.WildcardMatcherValueConverter;
import org.stagemonitor.configuration.ConfigurationOption;
import org.stagemonitor.configuration.ConfigurationOptionProvider;

import java.util.Arrays;
import java.util.List;

import static co.elastic.apm.agent.tracer.configuration.RangeValidator.isInRange;
import static co.elastic.apm.agent.tracer.configuration.RangeValidator.min;

public class ProfilingConfiguration extends ConfigurationOptionProvider {

    private static final String PROFILING_CATEGORY = "Profiling";

    private final ConfigurationOption<Boolean> profilingEnabled = ConfigurationOption.<Boolean>booleanOption()
        .key("profiling_inferred_spans_enabled")
        .configurationCategory(PROFILING_CATEGORY)
        .description("Set to `true` to make the agent create spans for method executions based on\n" +
            "https://github.com/jvm-profiling-tools/async-profiler[async-profiler], a sampling aka statistical profiler.\n" +
            "\n" +
            "Due to the nature of how sampling profilers work,\n" +
            "the duration of the inferred spans are not exact, but only estimations.\n" +
            "The <<config-profiling-inferred-spans-sampling-interval, `profiling_inferred_spans_sampling_interval`>> lets you fine tune the trade-off between accuracy and overhead.\n" +
            "\n" +
            "The inferred spans are created after a profiling session has ended.\n" +
            "This means there is a delay between the regular and the inferred spans being visible in the UI.\n" +
            "\n" +
            "Only platform threads are supported. Virtual threads are not supported and will not be profiled.\n" +
            "\n" +
            "NOTE: This feature is not available on Windows and on OpenJ9")
        .dynamic(true)
        .tags("added[1.15.0]", "experimental")
        .buildWithDefault(false);

    private final ConfigurationOption<Boolean> profilerLoggingEnabled = ConfigurationOption.<Boolean>booleanOption()
        .key("profiling_inferred_spans_logging_enabled")
        .configurationCategory(PROFILING_CATEGORY)
        .description("By default, async profiler prints warning messages about missing JVM symbols to standard output. \n" +
                     "Set this option to `false` to suppress such messages")
        .dynamic(true)
        .tags("added[1.37.0]")
        .buildWithDefault(true);

    private final ConfigurationOption<Boolean> backupDiagnosticFiles = ConfigurationOption.<Boolean>booleanOption()
        .key("profiling_inferred_spans_backup_diagnostic_files")
        .configurationCategory(PROFILING_CATEGORY)
        .dynamic(true)
        .tags("added[1.15.0]", "internal")
        .buildWithDefault(false);

    private final ConfigurationOption<Integer> asyncProfilerSafeMode = ConfigurationOption.<Integer>integerOption()
        .key("async_profiler_safe_mode")
        .configurationCategory(PROFILING_CATEGORY)
        .dynamic(false)
        .description("Can be used for analysis: the Async Profiler's area that deals with recovering stack trace frames \n" +
            "is known to be sensitive in some systems. It is used as a bit mask using values are between 0 and 31, \n" +
            "where 0 enables all recovery attempts and 31 disables all five (corresponding 1, 2, 4, 8 and 16).")
        .tags("internal")
        .buildWithDefault(0);

    private final ConfigurationOption<Boolean> postProcessingEnabled = ConfigurationOption.<Boolean>booleanOption()
        .key("profiling_inferred_spans_post_processing_enabled")
        .configurationCategory(PROFILING_CATEGORY)
        .dynamic(true)
        .description("Can be used to test the effect of the async-profiler in isolation from the agent's post-processing.")
        .tags("added[1.18.0]", "internal")
        .buildWithDefault(true);

    private final ConfigurationOption<TimeDuration> samplingInterval = TimeDurationValueConverter.durationOption("ms")
        .key("profiling_inferred_spans_sampling_interval")
        .configurationCategory(PROFILING_CATEGORY)
        .dynamic(true)
        .description("The frequency at which stack traces are gathered within a profiling session.\n" +
            "The lower you set it, the more accurate the durations will be.\n" +
            "This comes at the expense of higher overhead and more spans for potentially irrelevant operations.\n" +
            "The minimal duration of a profiling-inferred span is the same as the value of this setting.")
        .addValidator(isInRange(TimeDuration.of("1ms"), TimeDuration.of("1s")))
        .tags("added[1.15.0]")
        .buildWithDefault(TimeDuration.of("50ms"));

    private final ConfigurationOption<TimeDuration> inferredSpansMinDuration = TimeDurationValueConverter.durationOption("ms")
        .key("profiling_inferred_spans_min_duration")
        .configurationCategory(PROFILING_CATEGORY)
        .dynamic(true)
        .description("The minimum duration of an inferred span.\n" +
            "Note that the min duration is also implicitly set by the sampling interval.\n" +
            "However, increasing the sampling interval also decreases the accuracy of the duration of inferred spans.")
        .tags("added[1.15.0]")
        .addValidator(min(TimeDuration.of("0ms")))
        .buildWithDefault(TimeDuration.of("0ms"));

    private final ConfigurationOption<List<WildcardMatcher>> includedClasses = ConfigurationOption
        .builder(new ListValueConverter<>(new WildcardMatcherValueConverter()), List.class)
        .key("profiling_inferred_spans_included_classes")
        .configurationCategory(PROFILING_CATEGORY)
        .description("If set, the agent will only create inferred spans for methods which match this list.\n" +
            "Setting a value may slightly reduce overhead and can reduce clutter by only creating spans for the classes you are interested in.\n" +
            "Example: `org.example.myapp.*`\n" +
            "\n" +
            WildcardMatcher.DOCUMENTATION)
        .dynamic(true)
        .tags("added[1.15.0]")
        .buildWithDefault(WildcardMatcher.matchAllList());

    private final ConfigurationOption<List<WildcardMatcher>> excludedClasses = ConfigurationOption
        .builder(new ListValueConverter<>(new WildcardMatcherValueConverter()), List.class)
        .key("profiling_inferred_spans_excluded_classes")
        .configurationCategory(PROFILING_CATEGORY)
        .description("Excludes classes for which no profiler-inferred spans should be created.\n" +
            "\n" +
            WildcardMatcher.DOCUMENTATION)
        .dynamic(true)
        .tags("added[1.15.0]")
        .buildWithDefault(Arrays.asList(
            WildcardMatcher.caseSensitiveMatcher("java.*"),
            WildcardMatcher.caseSensitiveMatcher("javax.*"),
            WildcardMatcher.caseSensitiveMatcher("sun.*"),
            WildcardMatcher.caseSensitiveMatcher("com.sun.*"),
            WildcardMatcher.caseSensitiveMatcher("jdk.*"),
            WildcardMatcher.caseSensitiveMatcher("org.apache.tomcat.*"),
            WildcardMatcher.caseSensitiveMatcher("org.apache.catalina.*"),
            WildcardMatcher.caseSensitiveMatcher("org.apache.coyote.*"),
            WildcardMatcher.caseSensitiveMatcher("org.jboss.as.*"),
            WildcardMatcher.caseSensitiveMatcher("org.glassfish.*"),
            WildcardMatcher.caseSensitiveMatcher("org.eclipse.jetty.*"),
            WildcardMatcher.caseSensitiveMatcher("com.ibm.websphere.*"),
            WildcardMatcher.caseSensitiveMatcher("io.undertow.*")
        ));

    private final ConfigurationOption<TimeDuration> profilerInterval = TimeDurationValueConverter.durationOption("s")
        .key("profiling_inferred_spans_interval")
        .description("The interval at which profiling sessions should be started.")
        .configurationCategory(PROFILING_CATEGORY)
        .addValidator(min(TimeDuration.of("0ms")))
        .dynamic(true)
        .tags("added[1.15.0]", "internal")
        .buildWithDefault(TimeDuration.of("5s"));

    private final ConfigurationOption<TimeDuration> profilingDuration = TimeDurationValueConverter.durationOption("s")
        .key("profiling_inferred_spans_duration")
        .description("The duration of a profiling session.\n" +
            "For sampled transactions which fall within a profiling session (they start after and end before the session),\n" +
            "so-called inferred spans will be created.\n" +
            "They appear in the trace waterfall view like regular spans.\n" +
            "\n" +
            "NOTE: It is not recommended to set much higher durations as it may fill the activation events file and async-profiler's frame buffer.\n" +
            "Warnings will be logged if the activation events file is full.\n" +
            "If you want to have more profiling coverage, try decreasing <<config-profiling-inferred-spans-interval, `profiling_inferred_spans_interval`>>.")
        .configurationCategory(PROFILING_CATEGORY)
        .dynamic(true)
        .addValidator(isInRange(TimeDuration.of("1s"), TimeDuration.of("30s")))
        .tags("added[1.15.0]", "internal")
        .buildWithDefault(TimeDuration.of("5s"));

    private final ConfigurationOption<String> profilerLibDirectory = ConfigurationOption.<String>stringOption()
        .key("profiling_inferred_spans_lib_directory")
        .description("Profiling requires that the https://github.com/jvm-profiling-tools/async-profiler[async-profiler] shared library " +
            "is exported to a temporary location and loaded by the JVM.\n" +
            "The partition backing this location must be executable, however in some server-hardened environments, " +
            "`noexec` may be set on the standard `/tmp` partition, leading to `java.lang.UnsatisfiedLinkError` errors.\n" +
            "Set this property to an alternative directory (e.g. `/var/tmp`) to resolve this.\n" +
            "If unset, the value of the `java.io.tmpdir` system property will be used.")
        .configurationCategory(PROFILING_CATEGORY)
        .dynamic(false)
        .tags("added[1.18.0]")
        .build();

    public boolean isProfilingEnabled() {
        return profilingEnabled.get();
    }

    public boolean isProfilingLoggingEnabled() {
        return profilerLoggingEnabled.get();
    }

    public int getAsyncProfilerSafeMode() {
        return asyncProfilerSafeMode.get();
    }

    public TimeDuration getSamplingInterval() {
        return samplingInterval.get();
    }

    public TimeDuration getInferredSpansMinDuration() {
        return inferredSpansMinDuration.get();
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

    public boolean isBackupDiagnosticFiles() {
        return backupDiagnosticFiles.get();
    }

    public String getProfilerLibDirectory() {
        return profilerLibDirectory.isDefault() ? System.getProperty("java.io.tmpdir") : profilerLibDirectory.get();
    }

    public boolean isPostProcessingEnabled() {
        return postProcessingEnabled.get();
    }
}
