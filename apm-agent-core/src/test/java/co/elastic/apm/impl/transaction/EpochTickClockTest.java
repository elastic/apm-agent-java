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
        final long epochMillis = System.currentTimeMillis();
        epochTickClock.init(epochMillis, 0);
        final int nanoTime = 1000;
        assertThat(epochTickClock.getEpochMicros(nanoTime)).isEqualTo(epochMillis * 1000 + TimeUnit.NANOSECONDS.toMicros(nanoTime));
    }
}
