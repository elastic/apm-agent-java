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

import co.elastic.apm.agent.configuration.converter.TimeDuration;
import co.elastic.apm.agent.configuration.converter.TimeDurationValueConverter;
import org.stagemonitor.configuration.ConfigurationOption;
import org.stagemonitor.configuration.ConfigurationOptionProvider;

public class SpanConfiguration extends ConfigurationOptionProvider {

    public static final String HUGE_TRACES_CATEGORY = "Huge Traces";

    private final ConfigurationOption<Boolean> spanCompressionEnabled = ConfigurationOption.booleanOption()
        .key("span_compression_enabled")
        .configurationCategory(HUGE_TRACES_CATEGORY)
        .tags("added[1.30.0]")
        .description("Setting this option to true will enable span compression feature.\n" +
            "Span compression reduces the collection, processing, and storage overhead, and removes clutter from the UI. " +
            "The tradeoff is that some information such as DB statements of all the compressed spans will not be collected.")
        .dynamic(true)
        .buildWithDefault(false);

    private final ConfigurationOption<TimeDuration> spanCompressionExactMatchMaxDuration = TimeDurationValueConverter.durationOption("ms")
        .key("span_compression_exact_match_max_duration")
        .configurationCategory(HUGE_TRACES_CATEGORY)
        .tags("added[1.30.0]")
        .description("Consecutive spans that are exact match and that are under this threshold will be compressed into a single composite span. " +
            "This option does not apply to composite spans. This reduces the collection, processing, and storage overhead, and removes clutter from the UI. " +
            "The tradeoff is that the DB statements of all the compressed spans will not be collected.")
        .dynamic(true)
        .buildWithDefault(TimeDuration.of("50ms"));

    private final ConfigurationOption<TimeDuration> spanCompressionSameKindMaxDuration = TimeDurationValueConverter.durationOption("ms")
        .key("span_compression_same_kind_max_duration")
        .configurationCategory(HUGE_TRACES_CATEGORY)
        .tags("added[1.30.0]")
        .description("Consecutive spans to the same destination that are under this threshold will be compressed into a single composite span. " +
            "This option does not apply to composite spans. This reduces the collection, processing, and storage overhead, and removes clutter from the UI. " +
            "The tradeoff is that the DB statements of all the compressed spans will not be collected.")
        .dynamic(true)
        .buildWithDefault(TimeDuration.of("5ms"));

    private final ConfigurationOption<TimeDuration> exitSpanMinDuration = TimeDurationValueConverter.fineDurationOption()
        .key("exit_span_min_duration")
        .tags("added[1.30.0]")
        .configurationCategory(HUGE_TRACES_CATEGORY)
        .description("Exit spans are spans that represent a call to an external service, like a database. If such calls are very short, they are usually not relevant and can be ignored.\n" +
            "\n" +
            "NOTE: If a span propagates distributed tracing ids, it will not be ignored, even if it is shorter than the configured threshold. This is to ensure that no broken traces are recorded.")
        .dynamic(true)
        .buildWithDefault(TimeDuration.ofFine("0ms"));

    public boolean isSpanCompressionEnabled() {
        return spanCompressionEnabled.get();
    }

    public TimeDuration getSpanCompressionExactMatchMaxDuration() {
        return spanCompressionExactMatchMaxDuration.get();
    }

    public TimeDuration getSpanCompressionSameKindMaxDuration() {
        return spanCompressionSameKindMaxDuration.get();
    }

    public TimeDuration getExitSpanMinDuration() {
        return exitSpanMinDuration.get();
    }
}
