/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2020 Elastic and contributors
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
package co.elastic.apm.agent.configuration.converter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoundedDoubleConverterTest {

    @ParameterizedTest(name = "input={0}, expected={1}")
    @CsvSource({
        "0.55554,0.5555",
        "0.55555,0.5556",
        "0.55556,0.5556"})
    void testRounding(String input, String expectedOutput) {
        RoundedDoubleConverter converter = new RoundedDoubleConverter(4);

        Double expected = Double.valueOf(expectedOutput);
        Double converted = converter.convert(input);

        assertThat(converted).isEqualTo(expected);
        assertThat(converted.toString()).isEqualTo(expectedOutput);
        assertThat(converter.toString(converted)).isEqualTo(expectedOutput);
    }

    @Test
    void tryInvalidPrecision() {
        assertThatThrownBy(() -> new RoundedDoubleConverter(-1))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest(name = "input={0}, precision={1}, expected={2}")
    @CsvSource({
        "0.5555,0,1",
        "0.5555,1,0.6",
        "0.5555,2,0.56",
        "0.5555,3,0.556",
        "0.55555,4,0.5556"
    })
    void testPrecisions(String input, int precision, Double expected) {
        RoundedDoubleConverter converter = new RoundedDoubleConverter(precision);

        Double converted = converter.convert(input);
        assertThat(converted).isEqualTo(expected);
    }

}
