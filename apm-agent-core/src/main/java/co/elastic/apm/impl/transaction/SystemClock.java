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

import org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement;

import java.time.Clock;
import java.time.Instant;

public interface SystemClock {

    long getEpochMicros();

    enum ForCurrentVM implements SystemClock {
        INSTANCE;
        private final SystemClock dispatcher;

        ForCurrentVM() {
            SystemClock localDispatcher;
            try {
                // being cautious to not cause linking of ForJava8CapableVM in case we are not running on Java 8+
                Class.forName("java.time.Clock");
                localDispatcher = (SystemClock) Class.forName(SystemClock.class.getName() + "$ForJava8CapableVM").getEnumConstants()[0];
            } catch (Exception e) {
                localDispatcher = ForLegacyVM.INSTANCE;
            }
            dispatcher = localDispatcher;
        }

        @Override
        public long getEpochMicros() {
            return dispatcher.getEpochMicros();
        }
    }

    @IgnoreJRERequirement
    enum ForJava8CapableVM implements SystemClock {
        INSTANCE;

        private static final Clock clock = Clock.systemUTC();

        @Override
        public long getEpochMicros() {
            // escape analysis, plz kick in and allocate the Instant on the stack
            final Instant now = clock.instant();
            return now.getEpochSecond() * 1_000_000 + now.getNano() / 1_000;
        }
    }

    enum ForLegacyVM implements SystemClock {
        INSTANCE;

        @Override
        public long getEpochMicros() {
            return System.currentTimeMillis() * 1_000;
        }
    }
}
