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
package co.elastic.apm.report.serialize;

import com.dslplatform.json.JsonWriter;
import com.dslplatform.json.NumberConverter;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

/**
 * This class serializes an epoch timestamp in milliseconds to a ISO 8601 date time sting,
 * for example {@code 1970-01-01T00:00:00.000Z}
 * <p>
 * The main advantage of this class is that is able to serialize the timestamp in a garbage free way,
 * i.e. without object allocations and that it is faster than {@link java.text.DateFormat#format(Date)}.
 * </p>
 * <p>
 * The most complex part when formatting a ISO date is to determine the actual year,
 * month and date as you have to account for leap years.
 * Leveraging the fact that for a whole day this stays the same
 * and that the agent only serializes the current timestamp and not arbitrary ones,
 * we offload this task to {@link java.text.DateFormat#format(Date)} and cache the result.
 * So we only have to serialize the time part of the ISO timestamp which is easy
 * as a day has exactly {@code 1000 * 60 * 60 * 24} milliseconds.
 * Also, we don't have to worry about leap seconds when dealing with the epoch timestamp.
 * </p>
 * <p>
 * Note: this class is not thread safe.
 * As serializing the payloads is done in a single thread,
 * this is no problem though.
 * </p>
 */
class DateSerializer {

    private static final long MILLIS_PER_SECOND = 1000;
    private static final long MILLIS_PER_MINUTE = MILLIS_PER_SECOND * 60;
    private static final long MILLIS_PER_HOUR = MILLIS_PER_MINUTE * 60;
    private static final long MILLIS_PER_DAY = MILLIS_PER_HOUR * 24;
    private static final byte TIME_SEPARATOR = 'T';
    private static final byte TIME_ZONE_SEPARATOR = 'Z';
    private static final byte COLON = ':';
    private static final byte DOT = '.';
    private static final byte ZERO = '0';
    private final SimpleDateFormat dateFormat;
    // initialized in constructor via cacheDate
    @SuppressWarnings("NullableProblems")
    private String cachedDateIso;
    private long startOfCachedDate;
    private long endOfCachedDate;

    DateSerializer() {
        dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        cacheDate(System.currentTimeMillis());
    }

    private static long atStartOfDay(long epochTimestamp) {
        return epochTimestamp - epochTimestamp % MILLIS_PER_DAY;
    }

    private static long atEndOfDay(long epochTimestamp) {
        return atStartOfDay(epochTimestamp) + MILLIS_PER_DAY - 1;
    }

    void serializeEpochTimestampAsIsoDateTime(JsonWriter jw, long epochTimestamp) {
        if (!isDateCached(epochTimestamp)) {
            cacheDate(epochTimestamp);
        }
        jw.writeAscii(cachedDateIso);

        jw.writeByte(TIME_SEPARATOR);

        // hours
        long remainder = epochTimestamp % MILLIS_PER_DAY;
        serializeWithLeadingZero(jw, remainder / MILLIS_PER_HOUR, 2);
        jw.writeByte(COLON);

        // minutes
        remainder %= MILLIS_PER_HOUR;
        serializeWithLeadingZero(jw, remainder / MILLIS_PER_MINUTE, 2);
        jw.writeByte(COLON);

        // seconds
        remainder %= MILLIS_PER_MINUTE;
        serializeWithLeadingZero(jw, remainder / MILLIS_PER_SECOND, 2);
        jw.writeByte(DOT);

        // milliseconds
        remainder %= MILLIS_PER_SECOND;
        serializeWithLeadingZero(jw, remainder, 3);

        jw.writeByte(TIME_ZONE_SEPARATOR);
    }

    private void serializeWithLeadingZero(JsonWriter jw, long value, int minLength) {
        for (int i = minLength - 1; i > 0; i--) {
            if (value < Math.pow(10, i)) {
                jw.writeByte(ZERO);
            }
        }
        NumberConverter.serialize(value, jw);
    }

    private void cacheDate(long epochTimestamp) {
        cachedDateIso = dateFormat.format(new Date(epochTimestamp));
        startOfCachedDate = atStartOfDay(epochTimestamp);
        endOfCachedDate = atEndOfDay(epochTimestamp);
    }

    private boolean isDateCached(long epochTimestamp) {
        return epochTimestamp >= startOfCachedDate && epochTimestamp <= endOfCachedDate;
    }
}
