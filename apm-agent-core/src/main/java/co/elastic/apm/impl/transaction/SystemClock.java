package co.elastic.apm.impl.transaction;

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
                Class.forName("java.time.Instant");
                localDispatcher = ForJava8CapableVM.INSTANCE;
            } catch (Exception | NoClassDefFoundError e) {
                localDispatcher = ForLegacyVM.INSTANCE;
            }
            dispatcher = localDispatcher;
        }

        @Override
        public long getEpochMicros() {
            return dispatcher.getEpochMicros();
        }
    }

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
