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

import com.dslplatform.json.DslJson;
import com.dslplatform.json.JsonWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

class DateSerializerTest {

    private DateSerializer dateSerializer;
    private JsonWriter jsonWriter;
    private DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneId.of("UTC"));


    @BeforeEach
    void setUp() {
        jsonWriter = new DslJson<>().newWriter();
        dateSerializer = new DateSerializer();
    }

    @Test
    void testSerializeEpochTimestampAsIsoDateTime() {
        long timestamp = 0;
        long lastTimestampToCheck = LocalDateTime.now()
            .plus(1, ChronoUnit.YEARS)
            .toInstant(ZoneOffset.UTC)
            .toEpochMilli();
        // interval is approximately a hour but not exactly
        // to get different values for the minutes, seconds and milliseconds
        long interval = 997 * 61 * 61;
        for (; timestamp <= lastTimestampToCheck; timestamp += interval) {
            assertDateFormattingIsCorrect(Instant.ofEpochMilli(timestamp));
        }
    }

    private void assertDateFormattingIsCorrect(Instant instant) {
        jsonWriter.reset();
        dateSerializer.serializeEpochTimestampAsIsoDateTime(jsonWriter, instant.toEpochMilli());
        assertThat(jsonWriter.toString()).isEqualTo(dateTimeFormatter.format(instant));
    }
}
