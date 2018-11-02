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

import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

class SystemClockTest {

    @Test
    void testClocks() {
        final long currentVmEpochMicros = SystemClock.ForCurrentVM.INSTANCE.getEpochMicros();
        final long java8EpochMicros = SystemClock.ForJava8CapableVM.INSTANCE.getEpochMicros();
        final long java7EpochMicros = SystemClock.ForLegacyVM.INSTANCE.getEpochMicros();
        assertThat(java8EpochMicros).isCloseTo(java7EpochMicros, offset(TimeUnit.SECONDS.toMicros(10)));
        assertThat(java8EpochMicros).isCloseTo(currentVmEpochMicros, offset(TimeUnit.SECONDS.toMicros(10)));
        assertThat(java7EpochMicros % 1000).isEqualTo(0);
    }
}
