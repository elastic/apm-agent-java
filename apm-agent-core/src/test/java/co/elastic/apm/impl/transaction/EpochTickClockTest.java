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
package co.elastic.apm.impl.transaction;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

class EpochTickClockTest {

    private EpochTickClock epochTickClock;

    @BeforeEach
    void setUp() {
        epochTickClock = new EpochTickClock();
    }

    @Test
    void testEpochMicros() {
        final long epochMicros = SystemClock.ForJava8CapableVM.INSTANCE.getEpochMicros();
        epochTickClock.init(epochMicros, 0);
        final int nanoTime = 1000;
        assertThat(epochTickClock.getEpochMicros(nanoTime)).isEqualTo(epochMicros + TimeUnit.NANOSECONDS.toMicros(nanoTime));
    }
}
