package co.elastic.apm.impl.transaction;

import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

class SystemClockTest {

    @Test
    void testClocks() {
        assertThat(SystemClock.ForJava8CapableVM.INSTANCE.getEpochMicros())
            .isCloseTo(SystemClock.ForLegacyVM.INSTANCE.getEpochMicros(), offset(TimeUnit.SECONDS.toMicros(10)));
    }
}
