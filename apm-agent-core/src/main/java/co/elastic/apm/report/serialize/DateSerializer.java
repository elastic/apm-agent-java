/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 the original author or authors
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

class DateSerializer {

    private static final long MILLIS_PER_SECOND = 1000;
    private static final long MILLIS_PER_MINUTE = MILLIS_PER_SECOND * 60;
    private static final long MILLIS_PER_HOUR = MILLIS_PER_MINUTE * 60;
    private static final long MILLIS_PER_DAY = MILLIS_PER_HOUR * 24;
    private static final byte TIME_SEPARATOR = 'T';
    private static final byte TIME_ZONE_SEPARATOR = 'Z';
    private static final byte DOT = '.';
    private static final byte ZERO = '0';
    private final SimpleDateFormat dateFormat;
    private String cachedDateIso;
    private long startOfCachedDate;
    private long endOfCachedDate;

    DateSerializer() {
        dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        cacheDate(0);
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
        long remainder = epochTimestamp % MILLIS_PER_DAY;
        serializeWithLeadingZero(jw, remainder / MILLIS_PER_HOUR, 2);
        jw.writeByte(JsonWriter.SEMI);
        remainder %= MILLIS_PER_HOUR;
        serializeWithLeadingZero(jw, remainder / MILLIS_PER_MINUTE, 2);
        jw.writeByte(JsonWriter.SEMI);
        remainder %= MILLIS_PER_MINUTE;
        serializeWithLeadingZero(jw, remainder / MILLIS_PER_SECOND, 2);
        jw.writeByte(DOT);
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
