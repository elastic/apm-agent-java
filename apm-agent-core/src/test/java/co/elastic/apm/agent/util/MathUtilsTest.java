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
package co.elastic.apm.agent.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.SoftAssertions.assertSoftly;

class MathUtilsTest {

    @Test
    void getNextPowerOf2() {
        assertSoftly(softly -> {
            softly.assertThat(MathUtils.getNextPowerOf2(-1)).isEqualTo(2);
            softly.assertThat(MathUtils.getNextPowerOf2(0)).isEqualTo(2);
            softly.assertThat(MathUtils.getNextPowerOf2(1)).isEqualTo(2);
            softly.assertThat(MathUtils.getNextPowerOf2(2)).isEqualTo(2);
            softly.assertThat(MathUtils.getNextPowerOf2(3)).isEqualTo(4);
            softly.assertThat(MathUtils.getNextPowerOf2(234234)).isEqualTo(262144);
        });
    }
}
