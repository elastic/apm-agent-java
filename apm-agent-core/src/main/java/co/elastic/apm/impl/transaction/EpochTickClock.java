package co.elastic.apm.impl.transaction;

import co.elastic.apm.objectpool.Recyclable;

import java.util.concurrent.TimeUnit;

/**
 * This clock makes sure that each {@link Span} and {@link Transaction} uses a consistent clock
 * which does not drift in case of NTP updates or leap seconds.
 * <p>
 * The clock is initialized with wall clock time when the transaction starts and from there on
 * uses {@link System#nanoTime()} in order to be able to measure durations.
 * {@link System#currentTimeMillis()}, which uses wall clock time is not suitable for measuring durations because of the aforementioned
 * possibility of clock drifts.
 * </p>
 */
public class EpochTickClock implements Recyclable {

    private long nanoTimeOffsetToEpoch;

    public void init(EpochTickClock other) {
        this.nanoTimeOffsetToEpoch = other.nanoTimeOffsetToEpoch;
    }

    /**
     * Initializes and calibrates the clock based on wall clock time
     */
    public void init() {
        init(System.currentTimeMillis(), System.nanoTime());
    }

    public void init(long epochMillis, long nanoTime) {
        nanoTimeOffsetToEpoch = TimeUnit.MILLISECONDS.toNanos(epochMillis) - nanoTime;
    }

    public long getEpochMicros() {
        return getEpochMicros(System.nanoTime());
    }

    public long getEpochMicros(final long nanoTime) {
        return (nanoTime + nanoTimeOffsetToEpoch) / 1000;
    }

    @Override
    public void resetState() {
        nanoTimeOffsetToEpoch = 0;
    }
}
