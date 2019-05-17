/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2019 Elastic and contributors
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
package co.elastic.apm.agent.configuration.validation;

import co.elastic.apm.agent.configuration.converter.TimeDuration;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RangeValidatorTest {

    @Test
    void testRange() {
        final RangeValidator<Integer> validator = RangeValidator.isInRange(1, 3);
        assertThatThrownBy(() -> validator.assertValid(0)).isInstanceOf(IllegalArgumentException.class);
        validator.assertValid(1);
        validator.assertValid(2);
        validator.assertValid(3);
        assertThatThrownBy(() -> validator.assertValid(4)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testNotInRange() {
        final RangeValidator<Integer> validator = RangeValidator.isNotInRange(1, 3);
        validator.assertValid(0);
        assertThatThrownBy(() -> validator.assertValid(1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> validator.assertValid(2)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> validator.assertValid(3)).isInstanceOf(IllegalArgumentException.class);
        validator.assertValid(4);
    }

    @Test
    void testMin() {
        final RangeValidator<Integer> validator = RangeValidator.min(1);
        assertThatThrownBy(() -> validator.assertValid(0)).isInstanceOf(IllegalArgumentException.class);
        validator.assertValid(1);
        validator.assertValid(2);
    }

    @Test
    void testMax() {
        final RangeValidator<Integer> validator = RangeValidator.max(3);
        validator.assertValid(1);
        validator.assertValid(2);
        validator.assertValid(3);
        assertThatThrownBy(() -> validator.assertValid(4)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testTimeDuration() {
        final RangeValidator<TimeDuration> validator = RangeValidator.min(TimeDuration.of("1s"));
        assertThatThrownBy(() -> validator.assertValid(TimeDuration.of("0s"))).isInstanceOf(IllegalArgumentException.class);
        validator.assertValid(TimeDuration.of("1s"));
        validator.assertValid(TimeDuration.of("2s"));
    }
}
