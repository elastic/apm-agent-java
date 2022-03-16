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
package co.elastic.apm.agent.configuration.converter;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TimeDuration implements Comparable<TimeDuration> {

    public static final Pattern DURATION_PATTERN = Pattern.compile("^(-)?(\\d+)(us|ms|s|m)$");
    private final String durationString;

    private final long durationMicros;

    private TimeDuration(String durationString, long durationMicros) {
        this.durationString = durationString;
        this.durationMicros = durationMicros;
    }

    public static TimeDuration of(String durationString) {
        return with(durationString, false);
    }

    public static TimeDuration ofFine(String durationString) {
        return with(durationString, true);
    }

    private static TimeDuration with(String durationString, boolean allowMicros) {
        Matcher matcher = DURATION_PATTERN.matcher(durationString);
        if (matcher.matches()) {
            long duration = Long.parseLong(matcher.group(2));
            if (matcher.group(1) != null) {
                duration = duration * -1;
            }
            String unit = matcher.group(3);
            if (!allowMicros && "us".equals(unit)) {
                throw new IllegalArgumentException("Invalid duration '" + durationString + "', 'us' are only supported for fine granular durations");
            }
            return new TimeDuration(durationString, duration * getDurationMultiplier(unit));
        } else {
            throw new IllegalArgumentException("Invalid duration '" + durationString + "'");
        }
    }

    private static int getDurationMultiplier(String unit) {
        switch (unit) {
            case "us":
                return 1;
            case "ms":
                return 1000;
            case "s":
                return 1000 *1000;
            case "m":
                return 1000 * 1000 * 60;
            default:
                throw new IllegalStateException("Duration unit '" + unit + "' is unknown");
        }
    }

    public long getMillis() {
        return durationMicros/1000;
    }

    public long getMicros() {
        return durationMicros;
    }

    @Override
    public String toString() {
        return durationString;
    }

    @Override
    public int compareTo(TimeDuration o) {
        return Long.compare(durationMicros, o.durationMicros);
    }
}
