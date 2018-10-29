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
