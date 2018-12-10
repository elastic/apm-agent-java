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
package co.elastic.apm.agent.configuration.converter;

import org.stagemonitor.configuration.ConfigurationOption;
import org.stagemonitor.configuration.converter.AbstractValueConverter;

public class TimeDurationValueConverter extends AbstractValueConverter<TimeDuration> {

    private final String defaultDurationSuffix;

    private TimeDurationValueConverter(String defaultDurationSuffix) {
        this.defaultDurationSuffix = defaultDurationSuffix;
    }

    public static TimeDurationValueConverter withDefaultDuration(String defaultDurationSuffix) {
        return new TimeDurationValueConverter(defaultDurationSuffix);
    }

    public static ConfigurationOption.ConfigurationOptionBuilder<TimeDuration> durationOption(String defaultDuration) {
        return ConfigurationOption.<TimeDuration>builder(new TimeDurationValueConverter(defaultDuration), TimeDuration.class);
    }

    @Override
    public TimeDuration convert(String s) throws IllegalArgumentException {
        if (!s.endsWith("ms") && !s.endsWith("s") && !s.endsWith("m")) {
            s += defaultDurationSuffix;
        }
        return TimeDuration.of(s);
    }

    @Override
    public String toString(TimeDuration value) {
        return value.toString();
    }

    public String getDefaultDurationSuffix() {
        return defaultDurationSuffix;
    }
}
