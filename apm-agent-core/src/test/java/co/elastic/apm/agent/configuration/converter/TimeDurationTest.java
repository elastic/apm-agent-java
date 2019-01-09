/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018-2019 Elastic and contributors
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

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

class TimeDurationTest {

    @Test
    void testParseUnitSuccess() {
        assertSoftly(softly -> {
            softly.assertThat(TimeDuration.of("1ms").getMillis()).isEqualTo(1);
            softly.assertThat(TimeDuration.of("-1ms").getMillis()).isEqualTo(-1);

            softly.assertThat(TimeDuration.of("2s").getMillis()).isEqualTo(2000);
            softly.assertThat(TimeDuration.of("-2s").getMillis()).isEqualTo(-2000);
            softly.assertThat(TimeDuration.of("2s").getMillis()).isEqualTo(2000);
            softly.assertThat(TimeDuration.of("60s").getMillis()).isEqualTo(60_000);

            softly.assertThat(TimeDuration.of("3m").getMillis()).isEqualTo(180_000);
            softly.assertThat(TimeDuration.of("-3m").getMillis()).isEqualTo(-180_000);
        });
    }

    @Test
    void testParseUnitInvalid() {
        for (String invalid : List.of(" 1s", "1 s", "1s ", "1h")) {
            assertThatCode(() -> TimeDuration.of(invalid)).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
