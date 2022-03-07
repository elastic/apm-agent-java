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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class TimeDurationValueConverterTest {

    @Test
    void convertWithDefaultDuration() {
        TimeDurationValueConverter converter = TimeDurationValueConverter.withDefaultDuration("s");
        assertThat(converter.convert("1").toString()).isEqualTo("1s");
        assertThat(converter.convert("1m").toString()).isEqualTo("1m");
        assertThat(converter.convert("1ms").toString()).isEqualTo("1ms");
        assertThat(converter.convert("1ms").getMicros()).isEqualTo(1000);

        assertThatCode(() -> TimeDuration.of("1us")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void convertWithDefaultFineDuration() {
        TimeDurationValueConverter converter = TimeDurationValueConverter.withDefaultFineDuration("s");
        assertThat(converter.convert("1").toString()).isEqualTo("1s");
        assertThat(converter.convert("1m").toString()).isEqualTo("1m");
        assertThat(converter.convert("1ms").toString()).isEqualTo("1ms");
        assertThat(converter.convert("1us").toString()).isEqualTo("1us");
        assertThat(converter.convert("1us").getMicros()).isEqualTo(1);
        assertThat(converter.convert("1ms").getMicros()).isEqualTo(1000);
    }

}
